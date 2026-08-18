package com.saferide.vehicle;

import com.saferide.common.VerificationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByDriverIdOrderByCreatedAtDesc(Long driverId);

    Optional<Vehicle> findByDriverIdAndActiveTrue(Long driverId);

    /** Batch form of findByDriverIdAndActiveTrue - one query for every driver in a ride list page instead of one query per ride (see RideDriverInfoFactory#fromBatch). */
    List<Vehicle> findByDriverIdInAndActiveTrue(Collection<Long> driverIds);

    boolean existsByVehicleNumber(String vehicleNumber);

    Page<Vehicle> findByVerificationStatus(VerificationStatus status, Pageable pageable);
}
