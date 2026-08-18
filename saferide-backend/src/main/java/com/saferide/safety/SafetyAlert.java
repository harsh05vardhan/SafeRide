package com.saferide.safety;

import com.saferide.common.Auditable;
import com.saferide.ride.Ride;
import com.saferide.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An SOS press during a ride - {@code POST /api/rides/{id}/sos}, the
 * backend behind the "SOS" button every customer/driver ride screen
 * already shows. {@code latitude}/{@code longitude} are the triggering
 * device's position at the moment of the press, not the ride's pickup
 * point - deliberately captured fresh, since an emergency mid-trip can be
 * anywhere along the route.
 */
@Entity
@Table(
        name = "safety_alerts",
        indexes = {@Index(name = "idx_safety_alerts_status", columnList = "status")})
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
public class SafetyAlert extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "triggered_by_user_id", nullable = false)
    private User triggeredBy;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SafetyAlertStatus status = SafetyAlertStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** What actually happened and what was done about it - required to resolve an alert (see AdminSafetyService#resolve), not just a status flip with no record of the outcome. */
    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;
}
