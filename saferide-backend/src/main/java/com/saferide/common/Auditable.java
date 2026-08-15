package com.saferide.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Shared {@code createdAt}/{@code updatedAt} columns for every entity that
 * needs them (User, Driver, Ride, Fare, ...) - populated automatically by
 * Spring Data JPA auditing (enabled in
 * {@link com.saferide.config.JpaAuditingConfig}) instead of each entity
 * re-implementing {@code @PrePersist}/{@code @PreUpdate}.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
