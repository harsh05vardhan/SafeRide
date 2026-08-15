package com.saferide.user;

import com.saferide.auth.AuthenticatedUser;
import com.saferide.common.ApiResponse;
import com.saferide.user.dto.UpdateProfileRequest;
import com.saferide.user.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every route here resolves the target user from the JWT principal, never
 * from a path/body parameter - a customer can only ever read or edit
 * their own profile, which is what makes {@code hasRole('CUSTOMER')} alone
 * a sufficient check (no separate "is this your resource" test needed).
 */
@Tag(name = "Customers")
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final UserService userService;

    @GetMapping("/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<UserProfileResponse> getProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(userService.getProfile(principal.userId()));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok("Profile updated successfully", userService.updateProfile(principal.userId(), request));
    }
}
