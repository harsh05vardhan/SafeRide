package com.saferide.notification;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    /** {@code now} is bound rather than using JPQL's {@code current_timestamp} - that resolves to {@code java.sql.Timestamp}, which Hibernate rejects assigning into an {@code Instant} field. */
    @Modifying
    @Query("update Notification n set n.read = true, n.readAt = :now where n.user.id = :userId and n.read = false")
    void markAllAsRead(@Param("userId") Long userId, @Param("now") Instant now);
}
