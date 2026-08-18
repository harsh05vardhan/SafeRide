package com.saferide.user;

import com.saferide.exception.DuplicateResourceException;
import com.saferide.exception.ResourceNotFoundException;
import com.saferide.user.dto.UpdateProfileRequest;
import com.saferide.user.dto.UserProfileResponse;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the {@link User} aggregate: creation, lookup and profile
 * self-service. Password hashing and token issuance are
 * {@code AuthService}'s job - this class only ever deals with the
 * persisted account itself.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional
    public User createUser(String name, String email, String phone, String passwordHash, RoleName initialRole) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered", "EMAIL_ALREADY_REGISTERED");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new DuplicateResourceException("Phone number is already registered", "PHONE_ALREADY_REGISTERED");
        }

        Role role = roleRepository.findByName(initialRole)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + initialRole));

        User user = new User();
        user.setName(name);
        user.setEmail(email.toLowerCase());
        user.setPhone(phone);
        user.setPasswordHash(passwordHash);
        user.setStatus(UserStatus.ACTIVE);
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    /** Adds a role to an existing user (e.g. a customer applying to also become a driver). Idempotent. */
    @Transactional
    public User grantRole(User user, RoleName roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return toProfileResponse(getById(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getById(userId);
        if (!user.getPhone().equals(request.phone()) && userRepository.existsByPhone(request.phone())) {
            throw new DuplicateResourceException("Phone number is already registered", "PHONE_ALREADY_REGISTERED");
        }
        user.setName(request.name());
        user.setPhone(request.phone());
        return toProfileResponse(userRepository.save(user));
    }

    /** Admin action - blocking a user immediately locks them out of login (see {@code AuthService.login}). */
    @Transactional
    public User updateStatus(Long userId, UserStatus status) {
        User user = getById(userId);
        user.setStatus(status);
        return userRepository.save(user);
    }

    /**
     * Role-agnostic - a customer, driver, or admin's own photo is the same
     * concept regardless of role, so this lives here rather than being
     * duplicated per-role like the rest of profile editing currently is.
     * A blank/null value intentionally clears it back to the initial-letter
     * avatar, not an error.
     */
    @Transactional
    public UserProfileResponse updateAvatar(Long userId, String avatarUrl) {
        User user = getById(userId);
        user.setAvatarUrl(avatarUrl == null || avatarUrl.isBlank() ? null : avatarUrl);
        return toProfileResponse(userRepository.save(user));
    }

    private UserProfileResponse toProfileResponse(User user) {
        Set<RoleName> roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        return new UserProfileResponse(
                user.getId(), user.getName(), user.getEmail(), user.getPhone(),
                user.getStatus(), roleNames, user.getAvatarUrl(), user.getCreatedAt());
    }
}
