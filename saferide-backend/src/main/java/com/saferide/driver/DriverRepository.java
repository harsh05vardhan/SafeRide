package com.saferide.driver;

import com.saferide.common.VerificationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    Optional<Driver> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    boolean existsByLicenseNumber(String licenseNumber);

    Page<Driver> findByVerificationStatus(VerificationStatus status, Pageable pageable);

    /**
     * {@link com.saferide.matching.SqlDriverGeoIndex}'s candidate pool - not additionally
     * filtered by verification here, because a driver can only ever reach
     * {@code DriverOnlineStatus.ONLINE} in the first place via {@code DriverService.updateOnlineStatus},
     * which already refuses to let an unverified driver go online. Narrowed further (vehicle,
     * distance, exclusions, busy check) in {@code DriverMatchingService}.
     */
    List<Driver> findByOnlineStatus(DriverOnlineStatus onlineStatus);
}
