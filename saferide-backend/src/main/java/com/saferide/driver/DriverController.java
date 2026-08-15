package com.saferide.driver;

import com.saferide.auth.AuthenticatedUser;
import com.saferide.common.ApiResponse;
import com.saferide.driver.dto.DriverDocumentResponse;
import com.saferide.driver.dto.DriverDocumentUploadRequest;
import com.saferide.driver.dto.DriverLocationUpdateRequest;
import com.saferide.driver.dto.DriverProfileResponse;
import com.saferide.driver.dto.DriverRegisterRequest;
import com.saferide.driver.dto.DriverStatusUpdateRequest;
import com.saferide.ride.RideEventPublisher;
import com.saferide.ride.RideService;
import com.saferide.ride.dto.RideResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Driver self-service endpoints. */
@Tag(name = "Drivers")
@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;
    private final DriverDocumentService documentService;
    private final RideService rideService;
    private final RideEventPublisher rideEventPublisher;

    /**
     * Note: since access tokens are stateless JWTs, a token issued before
     * this call won't carry the new DRIVER role claim - the caller must
     * hit {@code POST /api/auth/refresh} (or log in again) afterwards to
     * get a token that can pass this controller's {@code hasRole('DRIVER')}
     * checks.
     */
    @PostMapping("/register")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DriverProfileResponse> register(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody DriverRegisterRequest request) {
        var profile = driverService.registerAsDriver(principal.userId(), request.licenseNumber());
        return ApiResponse.ok("Driver profile created - pending verification", profile);
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<DriverProfileResponse> getProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(driverService.getProfileByUserId(principal.userId()));
    }

    @PutMapping("/status")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<DriverProfileResponse> updateStatus(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody DriverStatusUpdateRequest request) {
        var profile = driverService.updateOnlineStatus(principal.userId(), request.onlineStatus());
        return ApiResponse.ok("Status updated", profile);
    }

    @PutMapping("/location")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<DriverProfileResponse> updateLocation(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody DriverLocationUpdateRequest request) {
        var profile = driverService.updateLocation(principal.userId(), request.latitude(), request.longitude());
        // Only worth broadcasting if this driver is actually on a ride right now - a location
        // report while idle/browsing has nobody subscribed to any /topic/rides/{id}/location.
        rideService.findActiveRideIdForDriver(principal.userId())
                .ifPresent(rideId -> rideEventPublisher.publishDriverLocation(
                        rideId, profile.id(), request.latitude(), request.longitude()));
        return ApiResponse.ok("Location updated", profile);
    }

    @PostMapping("/documents")
    @PreAuthorize("hasRole('DRIVER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DriverDocumentResponse> uploadDocument(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody DriverDocumentUploadRequest request) {
        return ApiResponse.ok("Document submitted - pending verification", documentService.upload(principal.userId(), request));
    }

    @GetMapping("/documents")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<List<DriverDocumentResponse>> listDocuments(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(documentService.listForDriver(principal.userId()));
    }

    @GetMapping("/rides")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<Page<RideResponse>> myRides(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ApiResponse.ok(rideService.listForDriver(principal.userId(), pageable));
    }
}
