package com.saferide.driver;

import com.saferide.auth.AuthenticatedUser;
import com.saferide.common.ApiResponse;
import com.saferide.driver.dto.DriverDocumentResponse;
import com.saferide.driver.dto.DriverDocumentUploadRequest;
import com.saferide.driver.dto.DriverProfileResponse;
import com.saferide.driver.dto.DriverRegisterRequest;
import com.saferide.driver.dto.DriverStatusUpdateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driver self-service endpoints. {@code PUT /api/drivers/location} and
 * {@code GET /api/drivers/rides} arrive later (location once Redis is
 * wired up in Phase 11, rides alongside the ride domain in Phase 4/6).
 */
@Tag(name = "Drivers")
@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;
    private final DriverDocumentService documentService;

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
}
