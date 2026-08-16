package com.saferide.admin;

import com.saferide.common.AuditLogService;
import com.saferide.exception.InvalidSafetyAlertStateException;
import com.saferide.exception.ResourceNotFoundException;
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

    @Transactional(readOnly = true)
    public Page<SafetyAlertResponse> list(SafetyAlertStatus statusFilter, Pageable pageable) {
        Page<SafetyAlert> page = statusFilter == null
                ? safetyAlertRepository.findAll(pageable)
                : safetyAlertRepository.findByStatusOrderByCreatedAtAsc(statusFilter, pageable);
        return page.map(SafetyAlertResponse::from);
    }

    @Transactional
    public SafetyAlertResponse resolve(Long adminUserId, Long alertId) {
        SafetyAlert alert = safetyAlertRepository.findById(alertId)
                .orElseThrow(() -> ResourceNotFoundException.of("Safety alert", alertId));
        if (alert.getStatus() == SafetyAlertStatus.RESOLVED) {
            throw new InvalidSafetyAlertStateException("This alert has already been resolved");
        }

        alert.setStatus(SafetyAlertStatus.RESOLVED);
        alert.setResolvedBy(userService.getById(adminUserId));
        alert.setResolvedAt(Instant.now());
        SafetyAlert saved = safetyAlertRepository.save(alert);

        auditLogService.record(adminUserId, alert.getRide().getId(), "SAFETY_ALERT_RESOLVED", "Resolved SOS alert #" + alertId);
        return SafetyAlertResponse.from(saved);
    }
}
