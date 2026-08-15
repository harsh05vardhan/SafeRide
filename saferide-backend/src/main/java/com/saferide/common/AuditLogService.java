package com.saferide.common;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single entry point for writing audit trail rows, so "how do I log an
 * admin action" always has one obvious answer. Every admin mutation
 * (driver approve/block, vehicle/document verify/reject, and whatever
 * later phases add) calls {@link #record} rather than touching
 * {@link AuditLogRepository} directly.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void record(Long actorUserId, Long rideId, String action, String description) {
        AuditLog log = AuditLog.builder()
                .userId(actorUserId)
                .rideId(rideId)
                .action(action)
                .description(description)
                .createdAt(Instant.now())
                .build();
        auditLogRepository.save(log);
    }

    public void record(Long actorUserId, String action, String description) {
        record(actorUserId, null, action, description);
    }
}
