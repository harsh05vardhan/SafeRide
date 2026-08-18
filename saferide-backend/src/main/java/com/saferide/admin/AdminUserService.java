package com.saferide.admin;

import com.saferide.admin.dto.AdminUserSummaryResponse;
import com.saferide.admin.dto.CreateAdminRequest;
import com.saferide.common.AuditLogService;
import com.saferide.driver.DriverOnlineStatus;
import com.saferide.driver.DriverRepository;
import com.saferide.exception.CannotDeleteUserException;
import com.saferide.exception.ResourceNotFoundException;
import com.saferide.matching.DriverGeoIndex;
import com.saferide.user.RoleName;
import com.saferide.user.User;
import com.saferide.user.UserRepository;
import com.saferide.user.UserService;
import com.saferide.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform-wide user management for {@code /admin/users} - every account regardless of role,
 * distinct from {@code AdminDriverService} (which only ever manages the driver-specific
 * verification/vehicle/document review flow). Deletion here is a soft delete
 * ({@link UserStatus#DELETED}) - see that enum's own doc for why nothing in SafeRide ever
 * hard-deletes a user: every ride/payment/rating/complaint/notification a deleted account was
 * ever part of keeps meaning exactly what it always did.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final DriverGeoIndex geoIndex;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<AdminUserSummaryResponse> listUsers(RoleName role, Pageable pageable) {
        return userRepository.findAllByOptionalRole(role, pageable).map(AdminUserSummaryResponse::from);
    }

    /**
     * The one legitimate way to create an admin account - {@code AuthService
     * #register} refuses {@code role: ADMIN} outright, by design, so this
     * exists so one admin isn't a single point of failure/workload for the
     * whole platform without opening admin signup to the public. Any
     * existing admin can create another; there's no separate "super admin"
     * tier - matches {@code deleteUser}'s own reasoning that whoever
     * controls the platform already controls this.
     */
    @Transactional
    public AdminUserSummaryResponse createAdmin(Long creatingAdminUserId, CreateAdminRequest request) {
        User created = userService.createUser(
                request.name(), request.email(), request.phone(), passwordEncoder.encode(request.password()), RoleName.ADMIN);
        auditLogService.record(creatingAdminUserId, "ADMIN_CREATED", "Created admin account for " + request.email());
        return AdminUserSummaryResponse.from(created);
    }

    /**
     * Refuses to delete the caller's own account (an admin locking themselves out has no
     * recovery path) or any other admin's (a compromised/rogue admin session should never be
     * able to remove the rest of the admin team - that call belongs to whoever controls the
     * database directly, not this endpoint). A driver being deleted is also forced OFFLINE and
     * pulled out of the geo-index immediately, exactly like a normal go-offline - a deleted
     * driver must never be a matching candidate a moment longer than necessary, not just
     * whenever their next status check happens to notice UserStatus != ACTIVE.
     */
    @Transactional
    public void deleteUser(Long adminUserId, Long targetUserId) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", targetUserId));

        if (target.getId().equals(adminUserId)) {
            throw new CannotDeleteUserException("You cannot delete your own account");
        }
        if (target.hasRole(RoleName.ADMIN)) {
            throw new CannotDeleteUserException("Admin accounts cannot be deleted through this endpoint");
        }

        target.setStatus(UserStatus.DELETED);
        userRepository.save(target);

        driverRepository.findByUserId(targetUserId).ifPresent(driver -> {
            if (driver.getOnlineStatus() == DriverOnlineStatus.ONLINE) {
                driver.setOnlineStatus(DriverOnlineStatus.OFFLINE);
                driverRepository.save(driver);
            }
            geoIndex.removeDriver(driver.getId());
        });
    }
}
