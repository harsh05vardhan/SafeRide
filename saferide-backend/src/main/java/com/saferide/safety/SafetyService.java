package com.saferide.safety;

import com.saferide.auth.AuthenticatedUser;
import com.saferide.exception.ResourceNotFoundException;
import com.saferide.exception.UnauthorizedException;
import com.saferide.ride.Ride;
import com.saferide.ride.RideDriverInfoFactory;
import com.saferide.ride.RideRepository;
import com.saferide.safety.dto.PublicTripResponse;
import com.saferide.safety.dto.SafetyAlertResponse;
import com.saferide.safety.dto.SosRequest;
import com.saferide.safety.dto.TripShareResponse;
import com.saferide.user.User;
import com.saferide.vehicle.Vehicle;
import com.saferide.vehicle.VehicleService;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SOS alerts and shareable trip-tracking links - the Safety Center's
 * backend. Both are scoped to one ride and checked against whoever is
 * calling; {@link #getPublicTrip} is the one deliberate exception - it's
 * reachable with no authentication at all, guarded only by possessing an
 * unguessable {@link TripShare#getToken() token} (see its class doc for why
 * that's the whole point).
 */
@Service
@RequiredArgsConstructor
public class SafetyService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration SHARE_LINK_TTL = Duration.ofHours(24);

    private final RideRepository rideRepository;
    private final SafetyAlertRepository safetyAlertRepository;
    private final TripShareRepository tripShareRepository;
    private final VehicleService vehicleService;
    private final RideDriverInfoFactory rideDriverInfoFactory;

    @Transactional
    public SafetyAlertResponse triggerSos(AuthenticatedUser principal, Long rideId, SosRequest request) {
        Ride ride = getRideOrThrow(rideId);
        User triggeredBy = requireParticipant(principal, ride);

        SafetyAlert alert = new SafetyAlert();
        alert.setRide(ride);
        alert.setTriggeredBy(triggeredBy);
        alert.setLatitude(request.latitude());
        alert.setLongitude(request.longitude());
        alert.setStatus(SafetyAlertStatus.ACTIVE);

        SafetyAlert saved = safetyAlertRepository.save(alert);
        return SafetyAlertResponse.from(saved, rideDriverInfoFactory.from(ride));
    }

    /** Only the customer can generate a share link - "Share Ride" is a rider-safety feature in the UI, not something a driver initiates on someone else's trip. */
    @Transactional
    public TripShareResponse getOrCreateShareLink(Long customerUserId, Long rideId) {
        Ride ride = getRideOrThrow(rideId);
        if (!ride.getCustomer().getId().equals(customerUserId)) {
            throw new UnauthorizedException("This ride does not belong to you");
        }

        TripShare share = tripShareRepository.findByRideId(rideId).orElse(null);
        if (share == null || share.isExpired()) {
            if (share == null) {
                share = new TripShare();
                share.setRide(ride);
            }
            share.setToken(generateToken());
            share.setExpiresAt(Instant.now().plus(SHARE_LINK_TTL));
            share = tripShareRepository.save(share);
        }

        return new TripShareResponse(share.getToken(), share.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public PublicTripResponse getPublicTrip(String token) {
        TripShare share = tripShareRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("This tracking link is invalid"));
        if (share.isExpired()) {
            throw new ResourceNotFoundException("This tracking link has expired");
        }

        Ride ride = share.getRide();
        String driverName = null;
        String vehicleModel = null;
        String vehicleNumber = null;
        if (ride.getDriver() != null) {
            driverName = ride.getDriver().getUser().getName();
            Optional<Vehicle> vehicle = vehicleService.getActiveVehicleEntity(ride.getDriver().getId());
            vehicleModel = vehicle.map(Vehicle::getVehicleModel).orElse(null);
            vehicleNumber = vehicle.map(Vehicle::getVehicleNumber).orElse(null);
        }

        return new PublicTripResponse(
                ride.getStatus(),
                ride.getPickupAddress(),
                ride.getDestinationAddress(),
                ride.getPickupLatitude(),
                ride.getPickupLongitude(),
                ride.getDestinationLatitude(),
                ride.getDestinationLongitude(),
                driverName,
                vehicleModel,
                vehicleNumber);
    }

    private User requireParticipant(AuthenticatedUser principal, Ride ride) {
        if (ride.getCustomer().getId().equals(principal.userId())) {
            return ride.getCustomer();
        }
        if (ride.getDriver() != null && ride.getDriver().getUser().getId().equals(principal.userId())) {
            return ride.getDriver().getUser();
        }
        throw new UnauthorizedException("This ride does not belong to you");
    }

    private Ride getRideOrThrow(Long rideId) {
        return rideRepository.findById(rideId)
                .orElseThrow(() -> ResourceNotFoundException.of("Ride", rideId));
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
