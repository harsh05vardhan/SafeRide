package com.saferide.settings;

import com.saferide.common.ApiResponse;
import com.saferide.settings.dto.PlatformSettingsResponse;
import com.saferide.settings.dto.UpdateSupportContactRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only write side of {@link SettingsController}'s public read - kept as a separate controller/route rather than one controller with mixed auth, matching how every other admin write already lives under its own {@code /api/admin/**} controller. */
@Tag(name = "Admin - Settings")
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettingsController {

    private final PlatformSettingsService settingsService;

    @PutMapping("/support-contact")
    public ApiResponse<PlatformSettingsResponse> updateSupportContact(@Valid @RequestBody UpdateSupportContactRequest request) {
        String updated = settingsService.updateSupportPhoneNumber(request.supportPhoneNumber());
        return ApiResponse.ok(new PlatformSettingsResponse(updated));
    }
}
