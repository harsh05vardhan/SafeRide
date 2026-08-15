package com.saferide.driver;

import com.saferide.common.VerificationStatus;
import com.saferide.driver.dto.DriverProfileResponse;
import com.saferide.exception.DriverNotVerifiedException;
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
        driver.setVerificationStatus(VerificationStatus.PENDING);
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
        return toProfileResponse(getDriverByUserId(userId));
    }

    /**
     * {@code PUT /api/drivers/status}. Going ONLINE requires the driver to
     * already be admin-verified - an unverified driver has no business
     * being visible to matching. Going OFFLINE is always allowed (a driver
     * must always be able to step away).
     */
    @Transactional
    public DriverProfileResponse updateOnlineStatus(Long userId, DriverOnlineStatus requested) {
        Driver driver = getDriverByUserId(userId);
        if (requested == DriverOnlineStatus.ONLINE && driver.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new DriverNotVerifiedException(
                    "Your driver profile must be verified by an admin before you can go online");
        }
        driver.setOnlineStatus(requested);
        return toProfileResponse(driverRepository.save(driver));
    }

    /**
     * {@code PUT /api/drivers/location}. Source-of-truth write to MySQL -
     * see the class-level note on {@link Driver} about where Redis fits
     * in starting Phase 11.
     */
    @Transactional
    public DriverProfileResponse updateLocation(Long userId, double latitude, double longitude) {
        Driver driver = getDriverByUserId(userId);
        driver.setCurrentLatitude(latitude);
        driver.setCurrentLongitude(longitude);
        return toProfileResponse(driverRepository.save(driver));
    }

    /** Public: other domains (vehicle, admin, later matching) resolve a driver's own record through this too. */
    public Driver getDriverByUserId(Long userId) {
        return driverRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No driver profile found for this account. Call POST /api/drivers/register first."));
    }

    public Driver getDriverById(Long driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() -> ResourceNotFoundException.of("Driver", driverId));
    }

    DriverProfileResponse toProfileResponse(Driver driver) {
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
                driver.getOnTimeRate(),
                driver.getComplaintCount(),
                driver.getReliabilityScore(),
                driver.getCurrentLatitude(),
                driver.getCurrentLongitude());
    }
}
