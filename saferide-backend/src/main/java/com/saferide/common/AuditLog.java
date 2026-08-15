package com.saferide.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An immutable record of a security- or business-relevant action -
 * currently admin driver/vehicle/document decisions; ride and payment
 * events join in their respective phases. {@code userId} is the actor
 * (who did it), not the subject, and {@code rideId} is nullable since most
 * audited actions (Phase 3's driver verification) aren't ride-scoped.
 *
 * <p>Deliberately holds plain {@code Long} ids rather than {@code @ManyToOne}
 * relations: an audit trail needs to keep working (and keep meaning what it
 * said at the time) even if the referenced row is later modified or
 * removed, so it never joins against live, mutable entities.
 */
@Entity
@Table(
        name = "audit_logs",
        indexes = {
            @Index(name = "idx_audit_logs_user_id", columnList = "user_id"),
            @Index(name = "idx_audit_logs_ride_id", columnList = "ride_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "ride_id")
    private Long rideId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
