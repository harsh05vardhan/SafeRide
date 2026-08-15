package com.saferide.ride;

import com.saferide.vehicle.VehicleType;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RideRepository extends JpaRepository<Ride, Long> {

    Page<Ride> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<Ride> findByDriverIdOrderByCreatedAtDesc(Long driverId, Pageable pageable);

    /** Fallback for a ride matching couldn't auto-assign - a driver can still self-claim it manually. */
    Page<Ride> findByStatusAndVehicleTypeOrderByCreatedAtAsc(RideStatus status, VehicleType vehicleType, Pageable pageable);

    boolean existsByDriverIdAndStatusIn(Long driverId, Collection<RideStatus> statuses);

    /** Same "is this driver already busy" check, excluding one ride - lets a driver accept the very ride they were just proactively assigned to. */
    boolean existsByDriverIdAndStatusInAndIdNot(Long driverId, Collection<RideStatus> statuses, Long excludedRideId);

    boolean existsByCustomerIdAndStatusIn(Long customerId, Collection<RideStatus> statuses);
}
