package com.saferide.user;

import com.saferide.auth.AuthenticatedUser;
import com.saferide.common.ApiResponse;
import com.saferide.user.dto.UpdateAvatarRequest;
import com.saferide.user.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role-agnostic "my own account" actions - unlike {@code CustomerController}/
 * {@code DriverController}'s profile endpoints (which are deliberately
 * role-scoped, since name/phone editing rules could one day diverge by
 * role), a photo is the exact same concept for a customer, driver, or
 * admin, so it gets one shared endpoint any authenticated user can call on
 * themselves rather than three copies of the same logic.
 */
@Tag(name = "Me")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;

    @PutMapping("/avatar")
    public ApiResponse<UserProfileResponse> updateAvatar(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody UpdateAvatarRequest request) {
        return ApiResponse.ok("Photo updated", userService.updateAvatar(principal.userId(), request.avatarUrl()));
    }
}
