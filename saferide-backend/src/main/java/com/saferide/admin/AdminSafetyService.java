package com.saferide.admin;

import com.saferide.common.AuditLogService;
import com.saferide.exception.InvalidSafetyAlertStateException;
import com.saferide.exception.ResourceNotFoundException;
import com.saferide.ride.RideDriverInfoFactory;
import com.saferide.safety.SafetyAlert;
import com.saferide.safety.SafetyAlertRepository;
import com.saferide.safety.SafetyAlertStatus;
import com.saferide.safety.dto.SafetyAlertResponse;
import com.saferide.user.UserService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin visibility into SOS presses - {@code /admin/safety/alerts}. Every resolution is audit-logged, same as complaint review. */
@Service
@RequiredArgsConstructor
public class AdminSafetyService {

    private final SafetyAlertRepository safetyAlertRepository;
    private final AuditLogService auditLogService;
    private final UserService userService;
    private final RideDriverInfoFactory rideDriverInfoFactory;

    @Transactional(readOnly = true)
    public Page<SafetyAlertResponse> list(SafetyAlertStatus statusFilter, Pageable pageable) {
        Page<SafetyAlert> page = statusFilter == null
                ? safetyAlertRepository.findAll(pageable)
                : safetyAlertRepository.findByStatusOrderByCreatedAtAsc(statusFilter, pageable);
        return page.map(this::toResponse);
    }

    /**
     * Resolving requires a real account of what happened and what was
     * done about it - not just a status flip. {@code notes} is validated
     * non-blank at the controller ({@link com.saferide.safety.dto} request
     * DTO), not here.
     */
    @Transactional
    public SafetyAlertResponse resolve(Long adminUserId, Long alertId, String notes) {
        SafetyAlert alert = safetyAlertRepository.findById(alertId)
                .orElseThrow(() -> ResourceNotFoundException.of("Safety alert", alertId));
        if (alert.getStatus() == SafetyAlertStatus.RESOLVED) {
            throw new InvalidSafetyAlertStateException("This alert has already been resolved");
        }

        alert.setStatus(SafetyAlertStatus.RESOLVED);
        alert.setResolvedBy(userService.getById(adminUserId));
        alert.setResolvedAt(Instant.now());
        alert.setResolutionNotes(notes);
        SafetyAlert saved = safetyAlertRepository.save(alert);

        auditLogService.record(adminUserId, alert.getRide().getId(), "SAFETY_ALERT_RESOLVED", "Resolved SOS alert #" + alertId + ": " + notes);
        return toResponse(saved);
    }

    /**
     * Only ever a RESOLVED alert - an ACTIVE one must never be able to
     * simply disappear (that's what {@link #resolve} is for, and it
     * leaves a record of the outcome). A hard delete, unlike the soft
     * deletes elsewhere in the app: nothing else references a
     * SafetyAlert row the way rides/payments reference a User, so there's
     * no history to protect by keeping it around.
     */
    @Transactional
    public void delete(Long adminUserId, Long alertId) {
        SafetyAlert alert = safetyAlertRepository.findById(alertId)
                .orElseThrow(() -> ResourceNotFoundException.of("Safety alert", alertId));
        if (alert.getStatus() != SafetyAlertStatus.RESOLVED) {
            throw new InvalidSafetyAlertStateException("Only a resolved alert can be deleted");
        }
        safetyAlertRepository.delete(alert);
        auditLogService.record(adminUserId, alert.getRide().getId(), "SAFETY_ALERT_DELETED", "Deleted resolved SOS alert #" + alertId);
    }

    private SafetyAlertResponse toResponse(SafetyAlert alert) {
        return SafetyAlertResponse.from(alert, rideDriverInfoFactory.from(alert.getRide()));
    }
}
