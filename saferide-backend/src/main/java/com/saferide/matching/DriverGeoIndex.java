package com.saferide.matching;

import java.util.List;

/**
 * Finds candidate driver ids near a point - the abstraction
 * {@link DriverMatchingService} was always written against (see its
 * class doc: "a geo query instead of a full table scan" was the intended
 * Phase-11 replacement, not a new method signature).
 *
 * <p>Two implementations, chosen by {@code app.matching.geo-index-source}:
 * {@link RedisDriverGeoIndex} (default - a real geospatial index, O(nearby)
 * instead of O(all online drivers)) and {@link SqlDriverGeoIndex} (a full
 * scan of online+verified drivers, filtered in application code - what ran
 * before this phase, kept as a legitimate no-Redis fallback and what the
 * test profile uses, since Testcontainers-backed Redis in tests arrives
 * Phase 14). Neither is a mock of the other - both are complete, correct
 * implementations, exactly the same relationship {@code MockMapService}
 * has to a real map provider.
 */
public interface DriverGeoIndex {

    /** Indexes (or re-indexes) a driver's current position. Only ever called while the driver is ONLINE - see DriverService. */
    void indexLocation(Long driverId, double latitude, double longitude);

    /** Removes a driver from the index - called when they go OFFLINE, since an offline driver must never be a matching candidate. */
    void removeDriver(Long driverId);

    /** Every online, indexed driver within {@code radiusKm} of the given point, nearest first. Callers still apply verification/vehicle/exclusion/busy filters on top. */
    List<Long> findWithinRadius(double latitude, double longitude, double radiusKm);
}
