package com.saferide.settings;

import com.saferide.common.ApiResponse;
import com.saferide.settings.dto.PlatformSettingsResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately public (no {@code @PreAuthorize}, listed as a permitted
 * path in {@code SecurityConfig}) - a customer/driver needs the support
 * number without necessarily being logged in (e.g. a just-blocked account
 * failing at the login screen), so this can't require auth.
 */
@Tag(name = "Settings")
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final PlatformSettingsService settingsService;

    @GetMapping("/support-contact")
    public ApiResponse<PlatformSettingsResponse> supportContact() {
        return ApiResponse.ok(new PlatformSettingsResponse(settingsService.getSupportPhoneNumber()));
    }
}
