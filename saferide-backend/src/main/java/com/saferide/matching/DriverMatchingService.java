package com.saferide.matching;

import com.saferide.common.VerificationStatus;
import com.saferide.driver.Driver;
import com.saferide.driver.DriverOnlineStatus;
import com.saferide.driver.DriverRepository;
import com.saferide.map.GeoUtils;
import com.saferide.ride.ActiveRideStatuses;
import com.saferide.ride.Ride;
import com.saferide.ride.RideRepository;
import com.saferide.vehicle.Vehicle;
import com.saferide.vehicle.VehicleService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the best driver for a ride and tracks who's been ruled out for
 * it. This is Phase 6's replacement for Phase 4's "driver browses open
 * requests" model - {@code RideService} calls {@link #findBestDriver} the
 * moment a ride's fare is locked (and again after a decline or a driver
 * cancellation) instead of waiting for a driver to self-claim.
 *
 * <p>Candidate retrieval here is a plain MySQL query over online+verified
 * drivers, filtered and scored in application code. That's the
 * intentional Phase-11 replacement point: once driver locations move
 * into Redis, this method's <em>signature</em> (a ride in, a best driver
 * out) doesn't change, only how it finds candidates - a geo query instead
 * of a full table scan.
 */
@Service
@RequiredArgsConstructor
public class DriverMatchingService {

    private final DriverRepository driverRepository;
    private final RideRepository rideRepository;
    private final VehicleService vehicleService;
    private final RideDriverExclusionRepository exclusionRepository;
    private final DriverMatchScorer matchScorer;
    private final MatchingProperties matchingProperties;

    @Transactional(readOnly = true)
    public Optional<Driver> findBestDriver(Ride ride) {
        Set<Long> excludedDriverIds = Set.copyOf(exclusionRepository.findDriverIdsByRideId(ride.getId()));
        List<Driver> onlineVerifiedDrivers =
                driverRepository.findByOnlineStatusAndVerificationStatus(DriverOnlineStatus.ONLINE, VerificationStatus.VERIFIED);

        return onlineVerifiedDrivers.stream()
                .filter(driver -> !excludedDriverIds.contains(driver.getId()))
                .filter(driver -> !isBusy(driver))
                .map(driver -> scoreIfEligible(driver, ride))
                .flatMap(Optional::stream)
                .max(Comparator.comparingDouble(ScoredCandidate::score))
                .map(ScoredCandidate::driver);
    }

    /** Marks a driver as ruled out for this specific ride, so they're never offered it again (any subsequent call to {@link #findBestDriver}). */
    @Transactional
    public void recordExclusion(Ride ride, Driver driver, ExclusionReason reason) {
        RideDriverExclusion exclusion = new RideDriverExclusion();
        exclusion.setRide(ride);
        exclusion.setDriver(driver);
        exclusion.setReason(reason);
        exclusion.setCreatedAt(Instant.now());
        exclusionRepository.save(exclusion);
    }

    /**
     * Exclusion must also gate the {@code /api/rides/open} fallback
     * self-claim path, not just proactive assignment - otherwise a driver
     * who declined or cancelled a ride could simply browse to it and
     * claim it anyway, defeating "a previously cancelled/declined driver
     * must not receive the same ride again". {@code RideService} calls
     * this from both {@link #findBestDriver}'s callers and the self-claim
     * branch of accept.
     */
    @Transactional(readOnly = true)
    public boolean isExcluded(Long rideId, Long driverId) {
        return exclusionRepository.findDriverIdsByRideId(rideId).contains(driverId);
    }

    private boolean isBusy(Driver driver) {
        return rideRepository.existsByDriverIdAndStatusIn(driver.getId(), ActiveRideStatuses.DRIVER);
    }

    private Optional<ScoredCandidate> scoreIfEligible(Driver driver, Ride ride) {
        if (driver.getCurrentLatitude() == null || driver.getCurrentLongitude() == null) {
            return Optional.empty();
        }
        Optional<Vehicle> activeVehicle = vehicleService.getActiveVehicleEntity(driver.getId());
        if (activeVehicle.isEmpty()) {
            return Optional.empty();
        }
        Vehicle vehicle = activeVehicle.get();
        if (vehicle.getVerificationStatus() != VerificationStatus.VERIFIED || vehicle.getVehicleType() != ride.getVehicleType()) {
            return Optional.empty();
        }

        double distanceKm = GeoUtils.haversineDistanceKm(
                driver.getCurrentLatitude(), driver.getCurrentLongitude(),
                ride.getPickupLatitude(), ride.getPickupLongitude());
        if (distanceKm > matchingProperties.getMaxRadiusKm()) {
            return Optional.empty();
        }

        return Optional.of(new ScoredCandidate(driver, matchScorer.score(driver, distanceKm)));
    }

    private record ScoredCandidate(Driver driver, double score) {
    }
}
