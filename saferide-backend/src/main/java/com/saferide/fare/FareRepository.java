package com.saferide.fare;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareRepository extends JpaRepository<Fare, Long> {

    Optional<Fare> findByRideId(Long rideId);
}
