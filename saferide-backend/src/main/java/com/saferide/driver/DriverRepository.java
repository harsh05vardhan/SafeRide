package com.saferide.driver;

import com.saferide.common.VerificationStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    Optional<Driver> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    boolean existsByLicenseNumber(String licenseNumber);

    Page<Driver> findByVerificationStatus(VerificationStatus status, Pageable pageable);
}
