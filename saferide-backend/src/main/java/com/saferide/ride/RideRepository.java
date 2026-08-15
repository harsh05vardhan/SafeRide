package com.saferide.ride;

import com.saferide.vehicle.VehicleType;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RideRepository extends JpaRepository<Ride, Long> {

    Page<Ride> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<Ride> findByDriverIdOrderByCreatedAtDesc(Long driverId, Pageable pageable);

    /** Phase 4's naive "browse open requests" model - superseded by proactive assignment in Phase 6. */
    Page<Ride> findByStatusAndVehicleTypeOrderByCreatedAtAsc(RideStatus status, VehicleType vehicleType, Pageable pageable);

    boolean existsByDriverIdAndStatusIn(Long driverId, Collection<RideStatus> statuses);

    boolean existsByCustomerIdAndStatusIn(Long customerId, Collection<RideStatus> statuses);
}
