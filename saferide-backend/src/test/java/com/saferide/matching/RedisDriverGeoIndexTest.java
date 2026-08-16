package com.saferide.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Verifies the Redis command shape (GEOADD/GEOSEARCH/ZREM) and, separately, that a Redis failure degrades rather than propagates - see the class doc on RedisDriverGeoIndex. */
@ExtendWith(MockitoExtension.class)
class RedisDriverGeoIndexTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private GeoOperations<String, String> geoOperations;

    private RedisDriverGeoIndex geoIndex;

    private void stubGeoOps() {
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        geoIndex = new RedisDriverGeoIndex(redisTemplate);
    }

    @Test
    void indexLocationAddsAPointKeyedByDriverId() {
        stubGeoOps();

        geoIndex.indexLocation(42L, 26.1845, 91.7362);

        // Point is (x, y) = (longitude, latitude) - see RedisDriverGeoIndex's own comment on why.
        verify(geoOperations).add(eq(RedisDriverGeoIndex.KEY), eq(new Point(91.7362, 26.1845)), eq("42"));
    }

    @Test
    void removeDriverRemovesByIdFromTheSameKey() {
        stubGeoOps();

        geoIndex.removeDriver(42L);

        verify(geoOperations).remove(RedisDriverGeoIndex.KEY, "42");
    }

    @Test
    void findWithinRadiusMapsGeoResultsBackToDriverIds() {
        stubGeoOps();
        GeoResults<GeoLocation<String>> results = new GeoResults<>(List.of(
                new GeoResult<>(new GeoLocation<>("7", new Point(91.7362, 26.1845)), new Distance(0.5, Metrics.KILOMETERS)),
                new GeoResult<>(new GeoLocation<>("9", new Point(91.74, 26.19)), new Distance(2.1, Metrics.KILOMETERS))));
        when(geoOperations.search(eq(RedisDriverGeoIndex.KEY), any(), any(Distance.class), any())).thenReturn(results);

        List<Long> found = geoIndex.findWithinRadius(26.1845, 91.7362, 5.0);

        assertThat(found).containsExactly(7L, 9L);
    }

    @Test
    void findWithinRadiusReturnsEmptyRatherThanNullResults() {
        stubGeoOps();
        when(geoOperations.search(eq(RedisDriverGeoIndex.KEY), any(), any(Distance.class), any())).thenReturn(null);

        assertThat(geoIndex.findWithinRadius(26.1845, 91.7362, 5.0)).isEmpty();
    }

    @Test
    void aRedisFailureOnIndexOrRemoveIsSwallowedNotPropagated() {
        // redisTemplate.opsForGeo() itself throwing simulates "Redis is unreachable" - every
        // public method must fail open, never throw, per the class doc.
        when(redisTemplate.opsForGeo()).thenThrow(new RuntimeException("connection refused"));
        geoIndex = new RedisDriverGeoIndex(redisTemplate);

        assertThatCode(() -> geoIndex.indexLocation(1L, 26.0, 91.0)).doesNotThrowAnyException();
        assertThatCode(() -> geoIndex.removeDriver(1L)).doesNotThrowAnyException();
    }

    @Test
    void aRedisFailureOnSearchDegradesToNoCandidatesRatherThanThrowing() {
        when(redisTemplate.opsForGeo()).thenThrow(new RuntimeException("connection refused"));
        geoIndex = new RedisDriverGeoIndex(redisTemplate);

        assertThat(geoIndex.findWithinRadius(26.0, 91.0, 5.0)).isEmpty();
    }
}
