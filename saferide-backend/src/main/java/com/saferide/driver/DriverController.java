package com.saferide.driver;

import com.saferide.auth.AuthenticatedUser;
import com.saferide.common.ApiResponse;
import com.saferide.driver.dto.DriverProfileResponse;
import com.saferide.driver.dto.DriverRegisterRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driver self-service profile endpoints. {@code PUT /api/drivers/status},
 * {@code PUT /api/drivers/location} and {@code GET /api/drivers/rides}
 * arrive in later phases (status/verification in Phase 3, location once
 * Redis is wired up in Phase 11, rides alongside the ride domain).
 */
@Tag(name = "Drivers")
@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    /**
     * Note: since access tokens are stateless JWTs, a token issued before
     * this call won't carry the new DRIVER role claim - the caller must
     * hit {@code POST /api/auth/refresh} (or log in again) afterwards to
     * get a token that can pass {@code GET /api/drivers/profile}'s
     * {@code hasRole('DRIVER')} check.
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
}
