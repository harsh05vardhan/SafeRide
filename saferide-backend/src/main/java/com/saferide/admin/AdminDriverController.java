package com.saferide.admin;

import com.saferide.admin.dto.AdminActionRequest;
import com.saferide.admin.dto.DriverAdminDetailResponse;
import com.saferide.admin.dto.DriverAdminSummaryResponse;
import com.saferide.auth.AuthenticatedUser;
import com.saferide.common.ApiResponse;
import com.saferide.common.VerificationStatus;
import com.saferide.driver.dto.DriverDocumentResponse;
import com.saferide.vehicle.dto.VehicleResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Driver/vehicle/document verification review for {@code /admin/driver-verification}. Every route requires ADMIN. */
@Tag(name = "Admin - Drivers")
@RestController
@RequestMapping("/api/admin/drivers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDriverController {

    private final AdminDriverService adminDriverService;

    @GetMapping
    public ApiResponse<Page<DriverAdminSummaryResponse>> list(
            @RequestParam(required = false) VerificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ApiResponse.ok(adminDriverService.listDrivers(status, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<DriverAdminDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(adminDriverService.getDriverDetail(id));
    }

    @PutMapping("/{id}/approve")
    public ApiResponse<DriverAdminSummaryResponse> approve(
            @AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id) {
        return ApiResponse.ok("Driver approved", adminDriverService.approveDriver(admin.userId(), id));
    }

    @PutMapping("/{id}/block")
    public ApiResponse<DriverAdminSummaryResponse> block(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminActionRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.ok("Driver blocked", adminDriverService.blockDriver(admin.userId(), id, reason));
    }

    @PutMapping("/{id}/unblock")
    public ApiResponse<DriverAdminSummaryResponse> unblock(
            @AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id) {
        return ApiResponse.ok("Driver unblocked", adminDriverService.unblockDriver(admin.userId(), id));
    }

    @PutMapping("/vehicles/{vehicleId}/approve")
    public ApiResponse<VehicleResponse> approveVehicle(
            @AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long vehicleId) {
        return ApiResponse.ok("Vehicle verified", adminDriverService.verifyVehicle(admin.userId(), vehicleId));
    }

    @PutMapping("/vehicles/{vehicleId}/reject")
    public ApiResponse<VehicleResponse> rejectVehicle(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long vehicleId,
            @Valid @RequestBody(required = false) AdminActionRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.ok("Vehicle rejected", adminDriverService.rejectVehicle(admin.userId(), vehicleId, reason));
    }

    @PutMapping("/documents/{documentId}/approve")
    public ApiResponse<DriverDocumentResponse> approveDocument(
            @AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long documentId) {
        return ApiResponse.ok("Document verified", adminDriverService.verifyDocument(admin.userId(), documentId));
    }

    @PutMapping("/documents/{documentId}/reject")
    public ApiResponse<DriverDocumentResponse> rejectDocument(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long documentId,
            @Valid @RequestBody(required = false) AdminActionRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.ok("Document rejected", adminDriverService.rejectDocument(admin.userId(), documentId, reason));
    }
}
