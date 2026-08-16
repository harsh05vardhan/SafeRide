package com.saferide.admin;

import com.saferide.auth.AuthenticatedUser;
import com.saferide.common.ApiResponse;
import com.saferide.safety.SafetyAlertStatus;
import com.saferide.safety.dto.SafetyAlertResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin visibility into SOS presses - {@code /admin/safety/alerts}. Every route requires ADMIN. */
@Tag(name = "Admin - Safety")
@RestController
@RequestMapping("/api/admin/safety/alerts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSafetyController {

    private final AdminSafetyService adminSafetyService;

    @GetMapping
    public ApiResponse<Page<SafetyAlertResponse>> list(
            @RequestParam(required = false) SafetyAlertStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ApiResponse.ok(adminSafetyService.list(status, pageable));
    }

    @PutMapping("/{id}/resolve")
    public ApiResponse<SafetyAlertResponse> resolve(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id) {
        return ApiResponse.ok("Alert resolved", adminSafetyService.resolve(admin.userId(), id));
    }
}
