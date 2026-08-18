package com.saferide.user.dto;

import com.saferide.user.Role;
import com.saferide.user.RoleName;
import com.saferide.user.User;
import java.util.Set;
import java.util.stream.Collectors;

/** Minimal user identity, embedded in {@code AuthResponse} and anywhere else a caller only needs "who is this". */
public record UserSummaryDTO(Long id, String name, String email, String phone, Set<RoleName> roles, String avatarUrl) {

    public static UserSummaryDTO from(User user) {
        Set<RoleName> roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        return new UserSummaryDTO(user.getId(), user.getName(), user.getEmail(), user.getPhone(), roleNames, user.getAvatarUrl());
    }
}
