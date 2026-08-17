package com.saferide.rating;

import com.saferide.driver.Driver;
import com.saferide.driver.DriverReliabilityService;
import com.saferide.exception.DuplicateResourceException;
import com.saferide.exception.ResourceNotFoundException;
import com.saferide.rating.dto.RatingRequest;
import com.saferide.rating.dto.RatingResponse;
import com.saferide.ride.Ride;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns {@link Rating} persistence and feeding the result into the
 * driver's {@code rating}/{@code reliabilityScore} via
 * {@link DriverReliabilityService}. Ride-status transitions
 * ({@code PAYMENT_COMPLETED -> RATED}) are {@code RideService}'s job, not
 * this class's - same shape as {@code FareService}/{@code PaymentService}.
 */
@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final DriverReliabilityService reliabilityService;

    /** Called exactly once per ride by {@code RideService.rateRide} - {@code RATED} is terminal, so a second rating attempt is rejected before this is ever reached. */
    @Transactional
    public RatingResponse rate(Ride ride, RatingRequest request) {
        if (ratingRepository.findByRideId(ride.getId()).isPresent()) {
            throw new DuplicateResourceException("This ride has already been rated", "ALREADY_RATED");
        }

        Rating rating = new Rating();
        rating.setRide(ride);
        rating.setScore(request.score());
        rating.setComment(request.comment());
        Rating saved = ratingRepository.save(rating);

        Driver driver = ride.getDriver();
        if (driver != null) {
            reliabilityService.recordRating(driver, request.score());
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RatingResponse getForRide(Long rideId) {
        Rating rating = ratingRepository.findByRideId(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("No rating found for ride " + rideId));
        return toResponse(rating);
    }

    /** Most recent feedback a driver has received - admin driver detail screen, not paginated (a fixed recent window via {@code pageable}'s size is enough for that one screen). */
    @Transactional(readOnly = true)
    public List<RatingResponse> listForDriver(Long driverId, Pageable pageable) {
        return ratingRepository.findByRide_Driver_IdOrderByCreatedAtDesc(driverId, pageable).stream()
                .map(this::toResponse)
                .toList();
    }

    private RatingResponse toResponse(Rating rating) {
        return new RatingResponse(rating.getRide().getId(), rating.getScore(), rating.getComment(), rating.getCreatedAt());
    }
}
