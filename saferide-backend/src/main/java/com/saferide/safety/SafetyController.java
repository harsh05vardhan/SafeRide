package com.saferide.safety;

import com.saferide.auth.AuthenticatedUser;
import com.saferide.common.ApiResponse;
import com.saferide.safety.dto.PublicTripResponse;
import com.saferide.safety.dto.SafetyAlertResponse;
import com.saferide.safety.dto.SosRequest;
import com.saferide.safety.dto.TripShareResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Safety")
@RestController
@RequiredArgsConstructor
public class SafetyController {

    private final SafetyService safetyService;

    @PostMapping("/api/rides/{id}/sos")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SafetyAlertResponse> triggerSos(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody SosRequest request) {
        return ApiResponse.ok("Alert sent - our safety team has been notified", safetyService.triggerSos(principal, id, request));
    }

    @PostMapping("/api/rides/{id}/share")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<TripShareResponse> share(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return ApiResponse.ok(safetyService.getOrCreateShareLink(principal.userId(), id));
    }

    /** Unauthenticated on purpose - guarded only by possessing the token itself. See SafetyService's class doc. */
    @GetMapping("/api/public/trips/{token}")
    public ApiResponse<PublicTripResponse> publicTrip(@PathVariable String token) {
        return ApiResponse.ok(safetyService.getPublicTrip(token));
    }
}
