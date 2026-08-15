package com.saferide.ride;

import com.saferide.auth.AuthenticatedUser;
import com.saferide.common.VerificationStatus;
import com.saferide.driver.Driver;
import com.saferide.driver.DriverOnlineStatus;
import com.saferide.driver.DriverService;
import com.saferide.exception.DriverNotVerifiedException;
import com.saferide.exception.DriverUnavailableException;
import com.saferide.exception.DuplicateResourceException;
import com.saferide.exception.InvalidOtpException;
import com.saferide.exception.InvalidRideStateException;
import com.saferide.exception.ResourceNotFoundException;
import com.saferide.exception.UnauthorizedException;
import com.saferide.map.MapService;
import com.saferide.map.RouteEstimate;
import com.saferide.ride.dto.RideCreateRequest;
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
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the ride lifecycle. Every status change goes through
 * {@link RideStateMachine} - this class decides <em>who is allowed to
 * trigger</em> a transition and what side effects it has (OTP generation,
 * timestamps), the state machine decides whether the transition itself is
 * legal.
 *
 * <p>Phase 4 has no driver-matching engine yet (Phase 6), so
 * {@link #acceptRide} is a naive self-claim: any online, verified driver
 * with a matching, verified active vehicle may accept an open request,
 * with {@code @Version} optimistic locking as the concurrency guard against
 * two drivers claiming the same ride. Phase 6 replaces the "driver browses
 * {@link #listOpenRides}" model with proactive assignment; this service's
 * public contract (accept/arrive/start/complete/cancel) does not need to
 * change when that happens.
 */
@Service
@RequiredArgsConstructor
public class RideService {

    /** A customer with a ride in any of these statuses can't book another one until it resolves. */
    private static final Set<RideStatus> ACTIVE_CUSTOMER_STATUSES = EnumSet.of(
            RideStatus.REQUESTED, RideStatus.SEARCHING_DRIVER, RideStatus.DRIVER_ASSIGNED,
            RideStatus.DRIVER_ACCEPTED, RideStatus.DRIVER_ARRIVED, RideStatus.RIDE_STARTED,
            RideStatus.DRIVER_CANCELLED);

    /** A driver already committed to one of these can't accept a second ride. */
    private static final Set<RideStatus> ACTIVE_DRIVER_STATUSES = EnumSet.of(
            RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ACCEPTED,
            RideStatus.DRIVER_ARRIVED, RideStatus.RIDE_STARTED);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RideRepository rideRepository;
    private final RideStateMachine stateMachine;
    private final MapService mapService;
    private final UserService userService;
    private final DriverService driverService;
    private final VehicleService vehicleService;

    public RideEstimateResponse estimateRoute(RideEstimateRequest request) {
        RouteEstimate estimate = mapService.estimateRoute(
                request.pickupLatitude(), request.pickupLongitude(),
                request.destinationLatitude(), request.destinationLongitude());
        return new RideEstimateResponse(estimate.distanceKm(), estimate.estimatedDurationMinutes());
    }

    @Transactional
    public RideResponse createRide(Long customerUserId, RideCreateRequest request) {
        if (rideRepository.existsByCustomerIdAndStatusIn(customerUserId, ACTIVE_CUSTOMER_STATUSES)) {
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

        transitionTo(ride, RideStatus.SEARCHING_DRIVER);

        return toResponse(rideRepository.save(ride), true);
    }

    @Transactional(readOnly = true)
    public RideResponse getRide(AuthenticatedUser principal, Long rideId) {
        Ride ride = getRideOrThrow(rideId);
        boolean isCustomer = ride.getCustomer().getId().equals(principal.userId());
        boolean isAssignedDriver = ride.getDriver() != null
                && ride.getDriver().getUser().getId().equals(principal.userId());
        boolean isAdmin = principal.roles().contains("ADMIN");

        if (!isCustomer && !isAssignedDriver && !isAdmin) {
            throw new UnauthorizedException("This ride does not belong to you");
        }
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

    /** Phase 4 stand-in for real matching: a driver browses open requests that match their active vehicle's type. */
    @Transactional(readOnly = true)
    public Page<RideResponse> listOpenRides(Long driverUserId, Pageable pageable) {
        Driver driver = driverService.getDriverByUserId(driverUserId);
        Vehicle activeVehicle = vehicleService.getActiveVehicleEntity(driver.getId())
                .orElseThrow(() -> new DriverUnavailableException(
                        "Register an active vehicle before browsing ride requests"));

        return rideRepository
                .findByStatusAndVehicleTypeOrderByCreatedAtAsc(RideStatus.SEARCHING_DRIVER, activeVehicle.getVehicleType(), pageable)
                .map(ride -> toResponse(ride, false));
    }

    @Transactional
    public RideResponse acceptRide(Long driverUserId, Long rideId) {
        Driver driver = driverService.getDriverByUserId(driverUserId);

        if (driver.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("Your account has been blocked");
        }
        if (driver.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new DriverNotVerifiedException("Your driver profile must be verified before you can accept rides");
        }
        if (driver.getOnlineStatus() != DriverOnlineStatus.ONLINE) {
            throw new DriverUnavailableException("You must be online to accept rides");
        }
        if (rideRepository.existsByDriverIdAndStatusIn(driver.getId(), ACTIVE_DRIVER_STATUSES)) {
            throw new DriverUnavailableException("You already have an active ride");
        }

        Vehicle activeVehicle = vehicleService.getActiveVehicleEntity(driver.getId())
                .orElseThrow(() -> new DriverUnavailableException("Register an active vehicle before accepting rides"));
        if (activeVehicle.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new DriverUnavailableException("Your active vehicle is not verified yet");
        }

        Ride ride = getRideOrThrow(rideId);
        if (ride.getStatus() != RideStatus.SEARCHING_DRIVER) {
            throw new InvalidRideStateException("This ride is no longer available");
        }
        if (ride.getVehicleType() != activeVehicle.getVehicleType()) {
            throw new DriverUnavailableException("Your active vehicle does not match this ride's requested vehicle type");
        }

        ride.setDriver(driver);
        transitionTo(ride, RideStatus.DRIVER_ASSIGNED);
        transitionTo(ride, RideStatus.DRIVER_ACCEPTED);
        ride.setAcceptedAt(Instant.now());

        // Optimistic-lock check happens on flush: if another driver's accept() committed first,
        // this save fails with ObjectOptimisticLockingFailureException -> 409 CONCURRENT_MODIFICATION.
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
        // Auto-advance: the system now expects payment. Fare/Payment domains land in Phase 5/12;
        // this transition is a pure state-machine concern and doesn't depend on either existing yet.
        transitionTo(ride, RideStatus.PAYMENT_PENDING);
        return toResponse(rideRepository.save(ride), false);
    }

    @Transactional
    public RideResponse cancelRide(AuthenticatedUser principal, Long rideId) {
        Ride ride = getRideOrThrow(rideId);
        boolean isCustomer = ride.getCustomer().getId().equals(principal.userId());
        boolean isAssignedDriver = ride.getDriver() != null
                && ride.getDriver().getUser().getId().equals(principal.userId());

        RideStatus target;
        if (isCustomer) {
            target = RideStatus.CANCELLED;
        } else if (isAssignedDriver) {
            target = RideStatus.DRIVER_CANCELLED;
            // Reliability-score impact and automatic reassignment onto the next-best driver
            // are Phase 6 concerns (driver matching); this phase only records the cancellation.
        } else {
            throw new UnauthorizedException("This ride does not belong to you");
        }

        transitionTo(ride, target);
        ride.setCancelledAt(Instant.now());
        return toResponse(rideRepository.save(ride), isCustomer);
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
            // vehicle was used at accept time; that field isn't in the Phase 4 Ride schema.
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
