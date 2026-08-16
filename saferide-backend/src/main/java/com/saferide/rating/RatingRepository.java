package com.saferide.rating;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findByRideId(Long rideId);

    /** Admin analytics summary (Phase 13) - coalesced to 0 rather than null when nothing's been rated yet. */
    @Query("select coalesce(avg(r.score), 0) from Rating r")
    double averageScore();
}
