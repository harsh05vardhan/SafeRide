package com.saferide.driver;

import com.saferide.common.Auditable;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A driver's operational profile: one row per {@link User} with the
 * DRIVER role. Vehicle assignment and document verification are added in
 * Phase 3; live location is Redis-backed starting Phase 11 -
 * {@code currentLatitude}/{@code currentLongitude} here are the
 * last-known fallback, not the hot path.
 */
@Entity
@Table(
        name = "drivers",
        indexes = {
            @Index(name = "idx_drivers_online_status", columnList = "online_status"),
            @Index(name = "idx_drivers_verification_status", columnList = "verification_status")
        })
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
public class Driver extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "license_number", nullable = false, unique = true, length = 30)
    private String licenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private DriverVerificationStatus verificationStatus = DriverVerificationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "online_status", nullable = false, length = 10)
    private DriverOnlineStatus onlineStatus = DriverOnlineStatus.OFFLINE;

    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    @Column(nullable = false)
    private Double rating = 5.0;

    @Column(name = "acceptance_rate", nullable = false)
    private Double acceptanceRate = 100.0;

    @Column(name = "cancellation_rate", nullable = false)
    private Double cancellationRate = 0.0;

    @Column(name = "completion_rate", nullable = false)
    private Double completionRate = 100.0;

    @Column(name = "reliability_score", nullable = false)
    private Double reliabilityScore = 70.0;

    public boolean isVerified() {
        return verificationStatus == DriverVerificationStatus.VERIFIED;
    }
}
