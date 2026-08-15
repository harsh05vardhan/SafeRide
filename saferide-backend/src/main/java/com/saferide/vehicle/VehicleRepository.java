package com.saferide.vehicle;

import com.saferide.common.VerificationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByDriverIdOrderByCreatedAtDesc(Long driverId);

    Optional<Vehicle> findByDriverIdAndActiveTrue(Long driverId);

    boolean existsByVehicleNumber(String vehicleNumber);

    Page<Vehicle> findByVerificationStatus(VerificationStatus status, Pageable pageable);
}
