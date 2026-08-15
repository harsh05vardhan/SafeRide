package com.saferide.ride;

import com.saferide.auth.AuthenticatedUser;
import com.saferide.common.VerificationStatus;
import com.saferide.driver.Driver;
import com.saferide.driver.DriverOnlineStatus;
import com.saferide.driver.DriverReliabilityService;
import com.saferide.driver.DriverService;
import com.saferide.exception.DriverNotVerifiedException;
import com.saferide.exception.DriverUnavailableException;
import com.saferide.exception.DuplicateResourceException;
import com.saferide.exception.InvalidOtpException;
import com.saferide.exception.InvalidRideStateException;
import com.saferide.exception.ResourceNotFoundException;
import com.saferide.exception.UnauthorizedException;
import com.saferide.fare.FareService;
import com.saferide.fare.dto.RideFareResponse;
import com.saferide.map.MapService;
import com.saferide.map.RouteEstimate;
import com.saferide.matching.DriverMatchingService;
import com.saferide.matching.ExclusionReason;
import com.saferide.ride.dto.RideCreateRequest;
import com.saferide.ride.dto.RideDestinationChangeRequest;
import com.saferide.ride.dto.RideDriverInfo;
import com.saferide.ride.dto.RideEstimateRequest;
import com.saferide.ride.dto.RideEstimateResponse;
import com.saferide.ride.dto.RideResponse;
import com.saferide.user.User;
import com.saferide.user.UserService;
import com.saferide.user.UserStatus;
import com.saferide.user.dto.UserSummaryDTO;
import com.saferide.vehicle.Vehicle;
import com.saferide.vehicle.VehicleService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the ride lifecycle. Every status change goes through
 * {@link RideStateMachine} - this class decides <em>who is allowed to
 * trigger</em> a transition and what side effects it has (OTP generation,
 * timestamps, matching, reliability updates), the state machine decides
 * whether the transition itself is legal.
 *
 * <p><b>Fare Lock (Phase 5):</b> {@link #createRide} leaves a new ride at
 * {@code REQUESTED} - not yet visible to any driver. Only
 * {@link #confirmFare} (which locks the fare via {@code FareService})
 * advances it to {@code SEARCHING_DRIVER}. If {@link #changeDestination}
 * is called on a ride that's already searching, the fare is unlocked and
 * the ride is pulled back to {@code REQUESTED} until the customer
 * re-confirms.
 *
 * <p><b>Matching (Phase 6):</b> the moment a ride reaches
 * {@code SEARCHING_DRIVER} - via {@link #confirmFare}, a decline, or a
 * driver cancellation - {@link #attemptAutoAssign} asks
 * {@code DriverMatchingService} for the best eligible driver and, if one
 * exists, proactively assigns them ({@code DRIVER_ASSIGNED}). That driver
 * must then explicitly {@link #acceptRide} or {@link #declineRide}; a
 * decline excludes them from this ride forever and immediately re-runs
 * matching for the next-best driver. {@link #listOpenRides} /
 * {@link #acceptRide}'s self-claim branch remain as a fallback for when
 * matching finds nobody (e.g. no driver has reported a location yet).
 *
 * <p>Driver cancellation ({@link #cancelRide}) works the same way:
 * records the driver as excluded, updates their reliability, and
 * immediately tries to reassign the same ride to someone else rather than
 * leaving the customer to start over - {@code RideStateMachine}'s
 * {@code DRIVER_CANCELLED -> SEARCHING_DRIVER} edge exists exactly for
 * this.
 */
@Service
@RequiredArgsConstructor
public class RideService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RideRepository rideRepository;
    private final RideStateMachine stateMachine;
    private final MapService mapService;
    private final UserService userService;
    private final DriverService driverService;
    private final VehicleService vehicleService;
    private final FareService fareService;
    private final DriverMatchingService matchingService;
    private final DriverReliabilityService reliabilityService;

    public RideEstimateResponse estimateRoute(RideEstimateRequest request) {
        RouteEstimate estimate = mapService.estimateRoute(
                request.pickupLatitude(), request.pickupLongitude(),
                request.destinationLatitude(), request.destinationLongitude());
        return new RideEstimateResponse(estimate.distanceKm(), estimate.estimatedDurationMinutes());
    }

    @Transactional
    public RideResponse createRide(Long customerUserId, RideCreateRequest request) {
        if (rideRepository.existsByCustomerIdAndStatusIn(customerUserId, ActiveRideStatuses.CUSTOMER)) {
            throw new DuplicateResourceException(
                    "You already have an active ride in progress", "ACTIVE_RIDE_EXISTS");
        }

        User customer = userService.getById(customerUserId);
        RouteEstimate estimate = mapService.estimateRoute(
                request.pickupLatitude(), request.pickupLongitude(),
                request.destinationLatitude(), request.destinationLongitude());

        Ride ride = new Ride();
        ride.setCustomer(customer);
        ride.setVehicleType(request.vehicleType());
        ride.setPickupAddress(request.pickupAddress());
        ride.setDestinationAddress(request.destinationAddress());
        ride.setPickupLatitude(request.pickupLatitude());
        ride.setPickupLongitude(request.pickupLongitude());
        ride.setDestinationLatitude(request.destinationLatitude());
        ride.setDestinationLongitude(request.destinationLongitude());
        ride.setDistanceKm(estimate.distanceKm());
        ride.setEstimatedDurationMinutes(estimate.estimatedDurationMinutes());
        ride.setStatus(RideStatus.REQUESTED);

        Ride savedRide = rideRepository.save(ride);
        // Every ride gets a Fare the moment it exists, so GET .../fare always has something to
        // show - but the ride stays REQUESTED (invisible to drivers) until confirmFare locks it.
        fareService.createForRide(savedRide);

        return toResponse(savedRide, true);
    }

    @Transactional(readOnly = true)
    public RideFareResponse getRideFare(AuthenticatedUser principal, Long rideId) {
        Ride ride = getRideOrThrow(rideId);
        requireCustomerOrAssignedDriverOrAdmin(principal, ride);
        // Both parties may view the locked amount - that's the whole point of the guarantee
        // (the driver sees exactly what the customer confirmed); only the customer can change it.
        return fareService.getRideFare(rideId);
    }

    /**
     * Locks the ride's current fare and, the first time this is called for
     * a ride, is what actually starts the search - see the class-level
     * Fare Lock note above. Locking is also what triggers the first
     * matching attempt.
     */
    @Transactional
    public RideFareResponse confirmFare(Long customerUserId, Long rideId) {
        Ride ride = getRideOrThrow(rideId);
        if (!ride.getCustomer().getId().equals(customerUserId)) {
            throw new UnauthorizedException("This ride does not belong to you");
        }

        RideFareResponse response = fareService.confirmFare(rideId);

        if (ride.getStatus() == RideStatus.REQUESTED) {
            transitionTo(ride, RideStatus.SEARCHING_DRIVER);
            attemptAutoAssign(ride);
            rideRepository.save(ride);
        }
        return response;
    }

    /**
     * Recalculates distance/fare for a new destination and unlocks the
     * previous amount - "do not silently increase the fare" means the
     * customer must see and re-confirm the new number via
     * {@link #confirmFare} before matching runs again.
     */
    @Transactional
    public RideResponse changeDestination(Long customerUserId, Long rideId, RideDestinationChangeRequest request) {
        Ride ride = getRideOrThrow(rideId);
        if (!ride.getCustomer().getId().equals(customerUserId)) {
            throw new UnauthorizedException("This ride does not belong to you");
        }
        if (ride.getStatus() != RideStatus.REQUESTED && ride.getStatus() != RideStatus.SEARCHING_DRIVER) {
            throw new InvalidRideStateException("Cannot change destination once a driver has been assigned");
        }

        RouteEstimate estimate = mapService.estimateRoute(
                ride.getPickupLatitude(), ride.getPickupLongitude(),
                request.destinationLatitude(), request.destinationLongitude());

        ride.setDestinationAddress(request.destinationAddress());
        ride.setDestinationLatitude(request.destinationLatitude());
        ride.setDestinationLongitude(request.destinationLongitude());
        ride.setDistanceKm(estimate.distanceKm());
        ride.setEstimatedDurationMinutes(estimate.estimatedDurationMinutes());

        fareService.recalculateForDestinationChange(ride);

        if (ride.getStatus() == RideStatus.SEARCHING_DRIVER) {
            // Fare just got unlocked - pull the ride out of the driver-visible pool until re-confirmed.
            transitionTo(ride, RideStatus.REQUESTED);
        }

        return toResponse(rideRepository.save(ride), true);
    }

    @Transactional(readOnly = true)
    public RideResponse getRide(AuthenticatedUser principal, Long rideId) {
        Ride ride = getRideOrThrow(rideId);
        boolean isCustomer = requireCustomerOrAssignedDriverOrAdmin(principal, ride);
        return toResponse(ride, isCustomer);
    }

    @Transactional(readOnly = true)
    public Page<RideResponse> listForCustomer(Long customerUserId, Pageable pageable) {
        return rideRepository.findByCustomerIdOrderByCreatedAtDesc(customerUserId, pageable)
                .map(ride -> toResponse(ride, true));
    }

    @Transactional(readOnly = true)
    public Page<RideResponse> listForDriver(Long driverUserId, Pageable pageable) {
        Driver driver = driverService.getDriverByUserId(driverUserId);
        return rideRepository.findByDriverIdOrderByCreatedAtDesc(driver.getId(), pageable)
                .map(ride -> toResponse(ride, false));
    }

    /**
     * Fallback browse-and-claim, for whenever automatic matching didn't
     * find (or hasn't yet found) anyone for a ride. Still excludes any
     * ride this driver already declined or cancelled - the fallback path
     * must honor the same exclusion rule as proactive matching, or a
     * driver could simply browse back to a ride they were ruled out of.
     */
    @Transactional(readOnly = true)
    public Page<RideResponse> listOpenRides(Long driverUserId, Pageable pageable) {
        Driver driver = driverService.getDriverByUserId(driverUserId);
        Vehicle activeVehicle = vehicleService.getActiveVehicleEntity(driver.getId())
                .orElseThrow(() -> new DriverUnavailableException(
                        "Register an active vehicle before browsing ride requests"));

        Page<Ride> ridesPage = rideRepository
                .findByStatusAndVehicleTypeOrderByCreatedAtAsc(RideStatus.SEARCHING_DRIVER, activeVehicle.getVehicleType(), pageable);

        List<RideResponse> visible = ridesPage.getContent().stream()
                .filter(ride -> !matchingService.isExcluded(ride.getId(), driver.getId()))
                .map(ride -> toResponse(ride, false))
                .toList();
        return new PageImpl<>(visible, pageable, ridesPage.getTotalElements());
    }

    /**
     * Accepts a ride two ways: if matching already proactively assigned
     * this exact driver ({@code DRIVER_ASSIGNED}), it's a simple
     * confirmation; otherwise it's the Phase 4 self-claim fallback against
     * an open {@code SEARCHING_DRIVER} ride (still concurrency-guarded by
     * {@code @Version} either way).
     */
    @Transactional
    public RideResponse acceptRide(Long driverUserId, Long rideId) {
        Driver driver = driverService.getDriverByUserId(driverUserId);
        Ride ride = getRideOrThrow(rideId);
        Vehicle activeVehicle = validateDriverEligibleToAct(driver, rideId);

        boolean alreadyAssignedToMe = ride.getStatus() == RideStatus.DRIVER_ASSIGNED
                && ride.getDriver() != null && ride.getDriver().getId().equals(driver.getId());

        if (alreadyAssignedToMe) {
            transitionTo(ride, RideStatus.DRIVER_ACCEPTED);
        } else {
            if (ride.getStatus() != RideStatus.SEARCHING_DRIVER) {
                throw new InvalidRideStateException("This ride is no longer available");
            }
            if (matchingService.isExcluded(rideId, driver.getId())) {
                throw new DriverUnavailableException("You are no longer eligible for this ride");
            }
            if (ride.getVehicleType() != activeVehicle.getVehicleType()) {
                throw new DriverUnavailableException("Your active vehicle does not match this ride's requested vehicle type");
            }
            ride.setDriver(driver);
            transitionTo(ride, RideStatus.DRIVER_ASSIGNED);
            transitionTo(ride, RideStatus.DRIVER_ACCEPTED);
        }

        ride.setAcceptedAt(Instant.now());
        reliabilityService.recordAcceptance(driver);

        // Optimistic-lock check happens on flush: if another driver's accept() committed first,
        // this save fails with ObjectOptimisticLockingFailureException -> 409 CONCURRENT_MODIFICATION.
        return toResponse(rideRepository.save(ride), false);
    }

    /**
     * Explicitly turns down a ride matching proactively assigned. Excludes
     * this driver from ever being offered this exact ride again and
     * immediately tries to reassign it to the next-best eligible driver.
     */
    @Transactional
    public RideResponse declineRide(Long driverUserId, Long rideId) {
        Ride ride = getOwnedByDriver(driverUserId, rideId);
        if (ride.getStatus() != RideStatus.DRIVER_ASSIGNED) {
            throw new InvalidRideStateException("This ride is not currently awaiting your decision");
        }

        Driver decliningDriver = ride.getDriver();
        matchingService.recordExclusion(ride, decliningDriver, ExclusionReason.DECLINED);
        reliabilityService.recordDecline(decliningDriver);

        ride.setDriver(null);
        transitionTo(ride, RideStatus.SEARCHING_DRIVER);
        attemptAutoAssign(ride);

        return toResponse(rideRepository.save(ride), false);
    }

    @Transactional
    public RideResponse arriveRide(Long driverUserId, Long rideId) {
        Ride ride = getOwnedByDriver(driverUserId, rideId);
        transitionTo(ride, RideStatus.DRIVER_ARRIVED);
        ride.setOtp(generateOtp());
        return toResponse(rideRepository.save(ride), false);
    }

    @Transactional
    public RideResponse startRide(Long driverUserId, Long rideId, String enteredOtp) {
        Ride ride = getOwnedByDriver(driverUserId, rideId);
        stateMachine.validateTransition(ride.getStatus(), RideStatus.RIDE_STARTED);

        if (ride.getOtp() == null || !ride.getOtp().equals(enteredOtp)) {
            throw new InvalidOtpException();
        }

        ride.setStatus(RideStatus.RIDE_STARTED);
        ride.setStartedAt(Instant.now());
        return toResponse(rideRepository.save(ride), false);
    }

    @Transactional
    public RideResponse completeRide(Long driverUserId, Long rideId) {
        Ride ride = getOwnedByDriver(driverUserId, rideId);
        transitionTo(ride, RideStatus.RIDE_COMPLETED);
        ride.setCompletedAt(Instant.now());
        reliabilityService.recordCompletion(ride.getDriver());
        // Auto-advance: the system now expects payment. The Payment domain itself lands in
        // Phase 12; this transition is a pure state-machine concern and doesn't depend on it.
        transitionTo(ride, RideStatus.PAYMENT_PENDING);
        return toResponse(rideRepository.save(ride), false);
    }

    @Transactional
    public RideResponse cancelRide(AuthenticatedUser principal, Long rideId) {
        Ride ride = getRideOrThrow(rideId);
        boolean isCustomer = ride.getCustomer().getId().equals(principal.userId());
        boolean isAssignedDriver = ride.getDriver() != null
                && ride.getDriver().getUser().getId().equals(principal.userId());

        if (isCustomer) {
            transitionTo(ride, RideStatus.CANCELLED);
            ride.setCancelledAt(Instant.now());
        } else if (isAssignedDriver) {
            Driver cancellingDriver = ride.getDriver();
            matchingService.recordExclusion(ride, cancellingDriver, ExclusionReason.CANCELLED);
            reliabilityService.recordCancellation(cancellingDriver);

            transitionTo(ride, RideStatus.DRIVER_CANCELLED);
            ride.setCancelledAt(Instant.now());

            // Automatic reassignment: don't make the customer start over - continue the same request.
            ride.setDriver(null);
            transitionTo(ride, RideStatus.SEARCHING_DRIVER);
            attemptAutoAssign(ride);
        } else {
            throw new UnauthorizedException("This ride does not belong to you");
        }

        return toResponse(rideRepository.save(ride), isCustomer);
    }

    /** Tries to proactively assign the best eligible driver; leaves the ride at SEARCHING_DRIVER (fallback: {@link #listOpenRides}) if nobody qualifies. */
    private void attemptAutoAssign(Ride ride) {
        matchingService.findBestDriver(ride).ifPresent(driver -> {
            ride.setDriver(driver);
            transitionTo(ride, RideStatus.DRIVER_ASSIGNED);
        });
    }

    /** Shared driver-side eligibility checks for accepting/being assigned a ride, excluding {@code rideId} from the "already busy" check. */
    private Vehicle validateDriverEligibleToAct(Driver driver, Long rideId) {
        if (driver.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("Your account has been blocked");
        }
        if (driver.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new DriverNotVerifiedException("Your driver profile must be verified before you can accept rides");
        }
        if (driver.getOnlineStatus() != DriverOnlineStatus.ONLINE) {
            throw new DriverUnavailableException("You must be online to accept rides");
        }
        if (rideRepository.existsByDriverIdAndStatusInAndIdNot(driver.getId(), ActiveRideStatuses.DRIVER, rideId)) {
            throw new DriverUnavailableException("You already have an active ride");
        }

        Vehicle activeVehicle = vehicleService.getActiveVehicleEntity(driver.getId())
                .orElseThrow(() -> new DriverUnavailableException("Register an active vehicle before accepting rides"));
        if (activeVehicle.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new DriverUnavailableException("Your active vehicle is not verified yet");
        }
        return activeVehicle;
    }

    private Ride getOwnedByDriver(Long driverUserId, Long rideId) {
        Driver driver = driverService.getDriverByUserId(driverUserId);
        Ride ride = getRideOrThrow(rideId);
        if (ride.getDriver() == null || !ride.getDriver().getId().equals(driver.getId())) {
            throw new UnauthorizedException("This ride is not assigned to you");
        }
        return ride;
    }

    private Ride getRideOrThrow(Long rideId) {
        return rideRepository.findById(rideId)
                .orElseThrow(() -> ResourceNotFoundException.of("Ride", rideId));
    }

    /** @return whether the caller is the ride's own customer (used to decide OTP/fare-edit visibility downstream) */
    private boolean requireCustomerOrAssignedDriverOrAdmin(AuthenticatedUser principal, Ride ride) {
        boolean isCustomer = ride.getCustomer().getId().equals(principal.userId());
        boolean isAssignedDriver = ride.getDriver() != null
                && ride.getDriver().getUser().getId().equals(principal.userId());
        boolean isAdmin = principal.roles().contains("ADMIN");

        if (!isCustomer && !isAssignedDriver && !isAdmin) {
            throw new UnauthorizedException("This ride does not belong to you");
        }
        return isCustomer;
    }

    private void transitionTo(Ride ride, RideStatus target) {
        stateMachine.validateTransition(ride.getStatus(), target);
        ride.setStatus(target);
    }

    private String generateOtp() {
        return String.format("%04d", SECURE_RANDOM.nextInt(10_000));
    }

    private RideResponse toResponse(Ride ride, boolean includeOtp) {
        RideDriverInfo driverInfo = null;
        if (ride.getDriver() != null) {
            Driver driver = ride.getDriver();
            // Shows the driver's *current* active vehicle. A real system would snapshot which
            // vehicle was used at accept time; that field isn't in the Ride schema.
            Optional<Vehicle> vehicle = vehicleService.getActiveVehicleEntity(driver.getId());
            driverInfo = new RideDriverInfo(
                    driver.getId(),
                    driver.getUser().getName(),
                    driver.getUser().getPhone(),
                    driver.getRating(),
                    vehicle.map(Vehicle::getVehicleModel).orElse(null),
                    vehicle.map(Vehicle::getVehicleNumber).orElse(null),
                    vehicle.map(Vehicle::getVehicleColor).orElse(null));
        }

        return new RideResponse(
                ride.getId(),
                UserSummaryDTO.from(ride.getCustomer()),
                driverInfo,
                ride.getVehicleType(),
                ride.getPickupAddress(),
                ride.getDestinationAddress(),
                ride.getPickupLatitude(),
                ride.getPickupLongitude(),
                ride.getDestinationLatitude(),
                ride.getDestinationLongitude(),
                ride.getDistanceKm(),
                ride.getEstimatedDurationMinutes(),
                ride.getStatus(),
                includeOtp ? ride.getOtp() : null,
                ride.getCreatedAt(),
                ride.getAcceptedAt(),
                ride.getStartedAt(),
                ride.getCompletedAt(),
                ride.getCancelledAt());
    }
}
