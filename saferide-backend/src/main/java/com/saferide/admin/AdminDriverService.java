package com.saferide.admin;

import com.saferide.admin.dto.DriverAdminDetailResponse;
import com.saferide.admin.dto.DriverAdminSummaryResponse;
import com.saferide.common.AuditLogService;
import com.saferide.common.VerificationStatus;
import com.saferide.driver.Driver;
import com.saferide.driver.DriverDocumentService;
import com.saferide.driver.DriverOnlineStatus;
import com.saferide.driver.DriverRepository;
import com.saferide.driver.DriverService;
import com.saferide.driver.dto.DriverDocumentResponse;
import com.saferide.matching.DriverGeoIndex;
import com.saferide.rating.RatingService;
import com.saferide.ride.RideRepository;
import com.saferide.ride.TerminalRideStatuses;
import com.saferide.user.UserService;
import com.saferide.user.UserStatus;
import com.saferide.user.dto.UserSummaryDTO;
import com.saferide.vehicle.VehicleService;
import com.saferide.vehicle.dto.VehicleResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin review actions for the driver onboarding pipeline
 * ({@code /admin/driver-verification}): list/inspect drivers, approve or
 * block a driver, and verify/reject their vehicles and documents. Every
 * mutation is audit-logged - see business rule "Admin actions should be
 * audit logged".
 */
@Service
@RequiredArgsConstructor
public class AdminDriverService {

    private final DriverRepository driverRepository;
    private final DriverService driverService;
    private final UserService userService;
    private final VehicleService vehicleService;
    private final DriverDocumentService documentService;
    private final AuditLogService auditLogService;
    private final RideRepository rideRepository;
    private final RatingService ratingService;
    private final DriverGeoIndex geoIndex;

    @Transactional(readOnly = true)
    public Page<DriverAdminSummaryResponse> listDrivers(VerificationStatus statusFilter, Pageable pageable) {
        Page<Driver> page = statusFilter == null
                ? driverRepository.findAll(pageable)
                : driverRepository.findByVerificationStatus(statusFilter, pageable);
        return page.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public DriverAdminDetailResponse getDriverDetail(Long driverId) {
        Driver driver = driverService.getDriverById(driverId);
        List<VehicleResponse> vehicles = vehicleService.listForDriverId(driverId);
        List<DriverDocumentResponse> documents = documentService.listForDriverId(driverId);
        long completedRideCount = rideRepository.countByDriverIdAndStatusIn(driverId, TerminalRideStatuses.COMPLETED);
        long cancelledRideCount = rideRepository.countByDriverIdAndStatusIn(driverId, TerminalRideStatuses.CANCELLED);
        var recentRatings = ratingService.listForDriver(driverId, PageRequest.of(0, 10));
        return new DriverAdminDetailResponse(
                toSummary(driver), vehicles, documents, completedRideCount, cancelledRideCount, recentRatings);
    }

    @Transactional
    public DriverAdminSummaryResponse approveDriver(Long adminUserId, Long driverId) {
        Driver driver = driverService.getDriverById(driverId);
        driver.setVerificationStatus(VerificationStatus.VERIFIED);
        driver = driverRepository.save(driver);

        auditLogService.record(adminUserId, "DRIVER_APPROVED",
                "Approved driver #" + driverId + " (license " + driver.getLicenseNumber() + ")");
        return toSummary(driver);
    }

    @Transactional
    public DriverAdminSummaryResponse blockDriver(Long adminUserId, Long driverId, String reason) {
        Driver driver = driverService.getDriverById(driverId);
        driver.setOnlineStatus(DriverOnlineStatus.OFFLINE);
        driver = driverRepository.save(driver);
        userService.updateStatus(driver.getUser().getId(), UserStatus.BLOCKED);
        // Setting the DB column to OFFLINE alone isn't enough - matching reads candidates
        // straight from the geo-index, not this column, so a driver who was online the moment
        // they got blocked would otherwise stay a live matching candidate regardless.
        geoIndex.removeDriver(driverId);

        auditLogService.record(adminUserId, "DRIVER_BLOCKED",
                "Blocked driver #" + driverId + (reason == null || reason.isBlank() ? "" : ": " + reason));
        return toSummary(driverService.getDriverById(driverId));
    }

    @Transactional
    public DriverAdminSummaryResponse unblockDriver(Long adminUserId, Long driverId) {
        Driver driver = driverService.getDriverById(driverId);
        userService.updateStatus(driver.getUser().getId(), UserStatus.ACTIVE);

        auditLogService.record(adminUserId, "DRIVER_UNBLOCKED", "Unblocked driver #" + driverId);
        return toSummary(driverService.getDriverById(driverId));
    }

    @Transactional
    public VehicleResponse verifyVehicle(Long adminUserId, Long vehicleId) {
        VehicleResponse response = vehicleService.setVerificationStatus(vehicleId, VerificationStatus.VERIFIED);
        auditLogService.record(adminUserId, "VEHICLE_VERIFIED", "Verified vehicle #" + vehicleId);
        return response;
    }

    @Transactional
    public VehicleResponse rejectVehicle(Long adminUserId, Long vehicleId, String reason) {
        VehicleResponse response = vehicleService.setVerificationStatus(vehicleId, VerificationStatus.REJECTED);
        auditLogService.record(adminUserId, "VEHICLE_REJECTED",
                "Rejected vehicle #" + vehicleId + (reason == null || reason.isBlank() ? "" : ": " + reason));
        return response;
    }

    @Transactional
    public DriverDocumentResponse verifyDocument(Long adminUserId, Long documentId) {
        var response = documentService.setVerificationStatus(documentId, VerificationStatus.VERIFIED);
        auditLogService.record(adminUserId, "DOCUMENT_VERIFIED", "Verified document #" + documentId);
        return response;
    }

    @Transactional
    public DriverDocumentResponse rejectDocument(Long adminUserId, Long documentId, String reason) {
        var response = documentService.setVerificationStatus(documentId, VerificationStatus.REJECTED);
        auditLogService.record(adminUserId, "DOCUMENT_REJECTED",
                "Rejected document #" + documentId + (reason == null || reason.isBlank() ? "" : ": " + reason));
        return response;
    }

    private DriverAdminSummaryResponse toSummary(Driver driver) {
        return new DriverAdminSummaryResponse(
                driver.getId(),
                UserSummaryDTO.from(driver.getUser()),
                driver.getUser().getStatus(),
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
                driver.getCreatedAt());
    }
}
