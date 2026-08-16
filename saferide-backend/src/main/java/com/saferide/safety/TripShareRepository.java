package com.saferide.safety;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripShareRepository extends JpaRepository<TripShare, Long> {

    Optional<TripShare> findByRideId(Long rideId);

    Optional<TripShare> findByToken(String token);
}
