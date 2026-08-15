package com.saferide.fare;

import com.saferide.exception.FareLockedException;
import com.saferide.exception.ResourceNotFoundException;
import com.saferide.fare.dto.FareBreakdownResponse;
import com.saferide.fare.dto.FareCalculateRequest;
import com.saferide.fare.dto.RideFareResponse;
import com.saferide.ride.Ride;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns {@link Fare} and {@link FareLock} persistence. Ride-status
 * transitions (moving a ride into {@code SEARCHING_DRIVER} once its fare
 * is locked, pulling it back to {@code REQUESTED} if a destination change
 * unlocks it) are {@code RideService}'s job, not this class's - keeping
 * the dependency one-directional (ride depends on fare, never the
 * reverse) is what avoids a circular bean dependency between the two
 * domains.
 */
@Service
@RequiredArgsConstructor
public class FareService {

    private final FareRepository fareRepository;
    private final FareLockRepository fareLockRepository;
    private final FareCalculator fareCalculator;

    public FareBreakdownResponse preview(FareCalculateRequest request) {
        FareBreakdown breakdown = fareCalculator.calculate(
                request.distanceKm(), request.estimatedDurationMinutes(), request.vehicleType(), request.discount());
        return FareBreakdownResponse.from(breakdown);
    }

    /** Called once, right after a ride is created - every ride has exactly one {@link Fare} row from that point on. */
    @Transactional
    public Fare createForRide(Ride ride) {
        FareBreakdown breakdown = fareCalculator.calculate(
                ride.getDistanceKm(), ride.getEstimatedDurationMinutes(), ride.getVehicleType(), 0.0);

        Fare fare = new Fare();
        fare.setRide(ride);
        fare.applyBreakdown(breakdown);
        return fareRepository.save(fare);
    }

    @Transactional(readOnly = true)
    public RideFareResponse getRideFare(Long rideId) {
        Fare fare = getFareOrThrow(rideId);
        FareLock lock = fareLockRepository.findByRideId(rideId).orElse(null);
        return toResponse(fare, lock);
    }

    /**
     * Locks the ride's current fare. Throws {@link FareLockedException} if
     * it's already locked - a customer confirming twice without an
     * intervening destination change is a no-op error, not a silent
     * success, so the client always knows exactly which amount is locked.
     */
    @Transactional
    public RideFareResponse confirmFare(Long rideId) {
        Fare fare = getFareOrThrow(rideId);
        FareLock lock = fareLockRepository.findByRideId(rideId).orElse(null);

        if (lock != null && lock.isLocked()) {
            throw new FareLockedException();
        }

        if (lock == null) {
            lock = new FareLock();
            lock.setRide(fare.getRide());
        }
        lock.setLockedAmount(fare.getTotalFare());
        lock.setStatus(FareLockStatus.LOCKED);
        lock.setLockedAt(Instant.now());
        lock.setUnlockedAt(null);
        fareLockRepository.save(lock);

        return toResponse(fare, lock);
    }

    /**
     * Recomputes the fare in place from the ride's (already-updated)
     * distance/duration, and unlocks any existing lock - the old amount no
     * longer reflects the trip being taken, so it can't stay authoritative.
     * The caller (RideService) is responsible for re-confirming with the
     * customer and for pulling the ride back out of the driver-visible
     * search pool until they do.
     */
    @Transactional
    public Fare recalculateForDestinationChange(Ride ride) {
        Fare fare = getFareOrThrow(ride.getId());
        FareBreakdown breakdown = fareCalculator.calculate(
                ride.getDistanceKm(), ride.getEstimatedDurationMinutes(), ride.getVehicleType(), fare.getDiscount());
        fare.applyBreakdown(breakdown);
        fareRepository.save(fare);

        fareLockRepository.findByRideId(ride.getId()).ifPresent(lock -> {
            if (lock.isLocked()) {
                lock.setStatus(FareLockStatus.UNLOCKED);
                lock.setUnlockedAt(Instant.now());
                fareLockRepository.save(lock);
            }
        });

        return fare;
    }

    private Fare getFareOrThrow(Long rideId) {
        return fareRepository.findByRideId(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("No fare found for ride " + rideId));
    }

    private RideFareResponse toResponse(Fare fare, FareLock lock) {
        return new RideFareResponse(
                fare.getRide().getId(),
                fare.getBaseFare(), fare.getDistanceFare(), fare.getTimeFare(),
                fare.getPlatformFee(), fare.getDiscount(), fare.getTotalFare(), fare.getCurrency(),
                lock == null ? null : lock.getStatus(),
                lock == null ? null : lock.getLockedAmount(),
                lock == null ? null : lock.getLockedAt(),
                lock == null ? null : lock.getUnlockedAt());
    }
}
