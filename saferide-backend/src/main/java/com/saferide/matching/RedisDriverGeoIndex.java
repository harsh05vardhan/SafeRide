package com.saferide.matching;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Component;

/**
 * The real implementation - a single Redis geospatial set
 * ({@code GEOADD}/{@code GEOSEARCH}/{@code ZREM} under the hood via
 * {@link GeoOperations}) so finding candidates near a pickup point is
 * O(nearby drivers) instead of {@link SqlDriverGeoIndex}'s O(all online
 * drivers). Active by default - see {@code app.matching.geo-index-source}.
 *
 * <p>One key, {@value #KEY}, holds every currently-online driver with a
 * known position; {@code DriverService} is what keeps membership in sync
 * with {@code DriverOnlineStatus} (indexed on going online or reporting a
 * new location while online, removed on going offline).
 *
 * <p><b>Fails open, never fails the request.</b> MySQL's
 * {@code Driver.currentLatitude}/{@code currentLongitude} is still the
 * source of truth (see {@code Driver}'s class doc) - this index is a hot,
 * purely derived copy of it. If Redis is briefly unreachable, a location
 * update or an online/offline toggle must still succeed against MySQL, so
 * every method here catches and logs rather than propagating: a write
 * that silently doesn't make it into the index just means that driver
 * won't turn up in proactive matching until their next successful report,
 * and {@link #findWithinRadius} returning empty on failure means a ride
 * simply waits at {@code SEARCHING_DRIVER} - still discoverable through
 * the {@code GET /api/rides/open} fallback, which never touches Redis at
 * all - rather than the request failing outright.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.matching", name = "geo-index-source", havingValue = "redis", matchIfMissing = true)
public class RedisDriverGeoIndex implements DriverGeoIndex {

    static final String KEY = "drivers:geo";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void indexLocation(Long driverId, double latitude, double longitude) {
        try {
            // Point is (x, y) = (longitude, latitude) - Redis's own GEO convention, matched by
            // GeoReference.fromCoordinate below so a search and an add always agree on axis order.
            geoOps().add(KEY, new Point(longitude, latitude), driverId.toString());
        } catch (Exception ex) {
            log.warn("Could not index driver {} location in Redis: {}", driverId, ex.getMessage());
        }
    }

    @Override
    public void removeDriver(Long driverId) {
        try {
            geoOps().remove(KEY, driverId.toString());
        } catch (Exception ex) {
            log.warn("Could not remove driver {} from the Redis geo index: {}", driverId, ex.getMessage());
        }
    }

    @Override
    public List<Long> findWithinRadius(double latitude, double longitude, double radiusKm) {
        try {
            GeoResults<GeoLocation<String>> results = geoOps().search(
                    KEY,
                    GeoReference.fromCoordinate(longitude, latitude),
                    new Distance(radiusKm, Metrics.KILOMETERS),
                    GeoSearchCommandArgs.newGeoSearchArgs().sortAscending());

            if (results == null) {
                return List.of();
            }
            return results.getContent().stream()
                    .map(result -> Long.valueOf(result.getContent().getName()))
                    .toList();
        } catch (Exception ex) {
            log.warn("Redis geo search failed, treating as no nearby candidates: {}", ex.getMessage());
            return List.of();
        }
    }

    private GeoOperations<String, String> geoOps() {
        return redisTemplate.opsForGeo();
    }
}
