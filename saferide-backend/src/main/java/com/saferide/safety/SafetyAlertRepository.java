package com.saferide.safety;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafetyAlertRepository extends JpaRepository<SafetyAlert, Long> {

    Page<SafetyAlert> findByStatusOrderByCreatedAtAsc(SafetyAlertStatus status, Pageable pageable);

    /** Admin analytics summary (Phase 13). */
    long countByStatus(SafetyAlertStatus status);
}
