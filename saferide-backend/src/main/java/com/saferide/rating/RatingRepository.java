package com.saferide.rating;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findByRideId(Long rideId);
}
