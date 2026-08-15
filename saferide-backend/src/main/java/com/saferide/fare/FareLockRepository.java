package com.saferide.fare;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareLockRepository extends JpaRepository<FareLock, Long> {

    Optional<FareLock> findByRideId(Long rideId);
}
