package com.saferide.admin.dto;

import com.saferide.user.User;
import com.saferide.user.UserStatus;
import com.saferide.user.dto.UserSummaryDTO;
import java.time.Instant;

public record AdminUserSummaryResponse(UserSummaryDTO user, UserStatus status, Instant createdAt) {

    public static AdminUserSummaryResponse from(User user) {
        return new AdminUserSummaryResponse(UserSummaryDTO.from(user), user.getStatus(), user.getCreatedAt());
    }
}
