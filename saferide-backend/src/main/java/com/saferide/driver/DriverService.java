package com.saferide.driver;

import com.saferide.driver.dto.DriverProfileResponse;
import com.saferide.exception.DuplicateResourceException;
import com.saferide.exception.ResourceNotFoundException;
import com.saferide.user.RoleName;
import com.saferide.user.User;
import com.saferide.user.UserService;
import com.saferide.user.dto.UserSummaryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;
    private final UserService userService;

    /** Used by {@code AuthService} during signup when {@code role == DRIVER}. */
    @Transactional
    public Driver createDriverProfile(User user, String licenseNumber) {
        if (driverRepository.existsByUserId(user.getId())) {
            throw new DuplicateResourceException("Driver profile already exists for this account", "DRIVER_PROFILE_EXISTS");
        }
        if (driverRepository.existsByLicenseNumber(licenseNumber)) {
            throw new DuplicateResourceException("License number is already registered", "LICENSE_ALREADY_REGISTERED");
        }
        Driver driver = new Driver();
        driver.setUser(user);
        driver.setLicenseNumber(licenseNumber);
        driver.setVerificationStatus(DriverVerificationStatus.PENDING);
        driver.setOnlineStatus(DriverOnlineStatus.OFFLINE);
        return driverRepository.save(driver);
    }

    /** {@code POST /api/drivers/register} - an existing (e.g. customer) account self-upgrading to also be a driver. */
    @Transactional
    public DriverProfileResponse registerAsDriver(Long userId, String licenseNumber) {
        User user = userService.getById(userId);
        userService.grantRole(user, RoleName.DRIVER);
        Driver driver = createDriverProfile(user, licenseNumber);
        return toProfileResponse(driver);
    }

    @Transactional(readOnly = true)
    public DriverProfileResponse getProfileByUserId(Long userId) {
        // readOnly transaction keeps the Hibernate session open through toProfileResponse(),
        // which needs it: Driver -> User is LAZY and open-in-view is disabled.
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No driver profile found for this account. Call POST /api/drivers/register first."));
        return toProfileResponse(driver);
    }

    private DriverProfileResponse toProfileResponse(Driver driver) {
        return new DriverProfileResponse(
                driver.getId(),
                UserSummaryDTO.from(driver.getUser()),
                driver.getLicenseNumber(),
                driver.getVerificationStatus(),
                driver.getOnlineStatus(),
                driver.getRating(),
                driver.getAcceptanceRate(),
                driver.getCancellationRate(),
                driver.getCompletionRate(),
                driver.getReliabilityScore());
    }
}
