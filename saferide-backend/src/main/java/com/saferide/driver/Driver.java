package com.saferide.driver;

import com.saferide.common.Auditable;
import com.saferide.common.VerificationStatus;
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
 * DRIVER role. {@code verificationStatus} covers the driver as a person
 * (license reviewed by an admin) - see {@link com.saferide.vehicle.Vehicle}
 * and {@link DriverDocument} for the per-vehicle and per-document
 * verification that ride matching (Phase 6) also requires before a driver
 * can actually accept rides.
 *
 * <p>The spec's separate DRIVER_RELIABILITY entity is deliberately folded
 * into these same rate columns rather than duplicated into a second
 * table: {@code acceptanceRate}/{@code cancellationRate}/
 * {@code completionRate}/{@code reliabilityScore} already lived here since
 * Phase 2, so a parallel table would just be the same numbers twice with
 * a real risk of the two drifting apart. {@code onTimeRate} and
 * {@code complaintCount} (Phase 6) complete the set - see
 * {@link com.saferide.driver.ReliabilityCalculator} for how they combine
 * into {@code reliabilityScore}. {@code averageRating} in the spec is
 * just {@link #rating} by another name, updated once ratings exist
 * (Phase 12).
 *
 * <p>{@code currentLatitude}/{@code currentLongitude} are written by
 * {@code PUT /api/drivers/location} (Phase 6) and remain MySQL's
 * source-of-truth copy. Since Phase 11, {@code DriverService} additionally
 * mirrors an ONLINE driver's position into
 * {@link com.saferide.matching.DriverGeoIndex} (Redis by default) - a hot,
 * ephemeral, purely derived copy that matching actually queries; MySQL
 * stays authoritative and is what a Redis flush/restart would be rebuilt
 * from, not the other way around.
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
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

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

    /** Defaults to 100 (benefit of the doubt) until real arrival-time tracking exists to earn or lose it. */
    @Column(name = "on_time_rate", nullable = false)
    private Double onTimeRate = 100.0;

    /** Incremented once the complaint system (Phase 12) exists; 0 until then. */
    @Column(name = "complaint_count", nullable = false)
    private Integer complaintCount = 0;

    public boolean isVerified() {
        return verificationStatus == VerificationStatus.VERIFIED;
    }
}
