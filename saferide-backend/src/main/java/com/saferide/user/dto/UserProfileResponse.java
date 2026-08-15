package com.saferide.user.dto;

import com.saferide.user.RoleName;
import com.saferide.user.UserStatus;
import java.time.Instant;
import java.util.Set;

/** Full profile payload for {@code GET /api/customers/profile}. */
public record UserProfileResponse(
        Long id,
        String name,
        String email,
        String phone,
        UserStatus status,
        Set<RoleName> roles,
        Instant createdAt) {
}
