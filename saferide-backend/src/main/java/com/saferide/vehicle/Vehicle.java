package com.saferide.vehicle;

import com.saferide.common.Auditable;
import com.saferide.common.VerificationStatus;
import com.saferide.driver.Driver;
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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A vehicle owned by a {@link Driver}. A driver may register more than one
 * (spec allows it - e.g. switching cars) but only one can be
 * {@code active} at a time; matching (Phase 6) only ever looks at a
 * driver's active vehicle. Verification is tracked independently from the
 * driver's own {@code verificationStatus} - an admin reviews the person
 * and the vehicle as separate decisions.
 */
@Entity
@Table(
        name = "vehicles",
        indexes = {
            @Index(name = "idx_vehicles_driver_id", columnList = "driver_id"),
            @Index(name = "idx_vehicles_verification_status", columnList = "verification_status")
        })
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
public class Vehicle extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 10)
    private VehicleType vehicleType;

    @Column(name = "vehicle_model", nullable = false, length = 60)
    private String vehicleModel;

    @Column(name = "vehicle_number", nullable = false, unique = true, length = 20)
    private String vehicleNumber;

    @Column(name = "vehicle_color", nullable = false, length = 30)
    private String vehicleColor;

    @Column(name = "seat_capacity", nullable = false)
    private Integer seatCapacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    /** Exactly one of a driver's vehicles is active at a time - enforced by {@code VehicleService}, not the DB. */
    @Column(nullable = false)
    private boolean active;
}
