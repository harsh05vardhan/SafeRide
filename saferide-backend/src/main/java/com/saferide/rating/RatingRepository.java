package com.saferide.rating;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findByRideId(Long rideId);

    /** Admin analytics summary (Phase 13) - coalesced to 0 rather than null when nothing's been rated yet. */
    @Query("select coalesce(avg(r.score), 0) from Rating r")
    double averageScore();

    /** Most recent feedback for one driver's admin detail screen - traverses Rating -> Ride -> Driver since a rating has no direct driver FK of its own. */
    List<Rating> findByRide_Driver_IdOrderByCreatedAtDesc(Long driverId, Pageable pageable);
}
