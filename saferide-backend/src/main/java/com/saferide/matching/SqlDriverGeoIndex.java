package com.saferide.matching;

import com.saferide.driver.Driver;
import com.saferide.driver.DriverOnlineStatus;
import com.saferide.driver.DriverRepository;
import com.saferide.map.GeoUtils;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The pre-Phase-11 candidate lookup, extracted verbatim rather than
 * deleted: a full scan of online drivers, filtered to those with a known
 * position and within radius, sorted nearest-first in application code.
 * O(all online drivers) instead of {@link RedisDriverGeoIndex}'s
 * O(nearby), but correct and dependency-free - active whenever
 * {@code app.matching.geo-index-source=sql} (the test profile's default,
 * since Testcontainers-backed Redis in tests arrives Phase 14; also a
 * legitimate small-scale no-Redis deployment choice).
 *
 * <p>{@link #indexLocation}/{@link #removeDriver} are no-ops here - there's
 * nothing to maintain when "the index" is just {@code Driver} rows in
 * MySQL, which {@code DriverService} already writes on every location
 * report regardless of which implementation is active.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.matching", name = "geo-index-source", havingValue = "sql")
public class SqlDriverGeoIndex implements DriverGeoIndex {

    private final DriverRepository driverRepository;

    @Override
    public void indexLocation(Long driverId, double latitude, double longitude) {
        // No-op - MySQL's Driver.currentLatitude/currentLongitude already is the index.
    }

    @Override
    public void removeDriver(Long driverId) {
        // No-op - see class doc.
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findWithinRadius(double latitude, double longitude, double radiusKm) {
        return driverRepository.findByOnlineStatus(DriverOnlineStatus.ONLINE).stream()
                .filter(driver -> driver.getCurrentLatitude() != null && driver.getCurrentLongitude() != null)
                .map(driver -> new ScoredCandidate(driver,
                        GeoUtils.haversineDistanceKm(latitude, longitude, driver.getCurrentLatitude(), driver.getCurrentLongitude())))
                .filter(candidate -> candidate.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(ScoredCandidate::distanceKm))
                .map(candidate -> candidate.driver().getId())
                .toList();
    }

    private record ScoredCandidate(Driver driver, double distanceKm) {
    }
}
