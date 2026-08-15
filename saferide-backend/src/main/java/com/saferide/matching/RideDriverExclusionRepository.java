package com.saferide.matching;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RideDriverExclusionRepository extends JpaRepository<RideDriverExclusion, Long> {

    @Query("select e.driver.id from RideDriverExclusion e where e.ride.id = :rideId")
    List<Long> findDriverIdsByRideId(Long rideId);
}
