package com.saferide.ride;

import com.saferide.common.Auditable;
import com.saferide.driver.Driver;
import com.saferide.user.User;
import com.saferide.vehicle.VehicleType;
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
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single ride request end to end. {@code distanceKm} and
 * {@code estimatedDurationMinutes} are always computed server-side via
 * {@link com.saferide.map.MapService} at creation time - never accepted
 * from the client. {@code otp} is null until {@code DRIVER_ARRIVED}
 * generates it. {@code version} is the optimistic-lock column that stops
 * two drivers from accepting the same ride under concurrent requests -
 * see {@code RideService.acceptRide} and
 * {@code GlobalExceptionHandler.handleConcurrentModification}.
 */
@Entity
@Table(
        name = "rides",
        indexes = {
            @Index(name = "idx_rides_customer_id", columnList = "customer_id"),
            @Index(name = "idx_rides_driver_id", columnList = "driver_id"),
            @Index(name = "idx_rides_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
public class Ride extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    /** Null until a driver accepts (Phase 4: self-claim; Phase 6: assigned by the matching engine). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 10)
    private VehicleType vehicleType;

    @Column(name = "pickup_address", nullable = false, length = 300)
    private String pickupAddress;

    @Column(name = "destination_address", nullable = false, length = 300)
    private String destinationAddress;

    @Column(name = "pickup_latitude", nullable = false)
    private Double pickupLatitude;

    @Column(name = "pickup_longitude", nullable = false)
    private Double pickupLongitude;

    @Column(name = "destination_latitude", nullable = false)
    private Double destinationLatitude;

    @Column(name = "destination_longitude", nullable = false)
    private Double destinationLongitude;

    @Column(name = "distance_km", nullable = false)
    private Double distanceKm;

    @Column(name = "estimated_duration_minutes", nullable = false)
    private Integer estimatedDurationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RideStatus status;

    @Column(length = 4)
    private String otp;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 20)
    private RideCancellationReason cancellationReason;

    /**
     * Set every time the ride (re-)enters {@code SEARCHING_DRIVER} - the
     * initial search via {@code confirmFare}, and again after every
     * {@code declineRide}/driver cancellation reassignment. Read by
     * {@code RideService#autoExpireStaleSearches} to auto-cancel a ride
     * that's been searching too long, and echoed to the customer so their
     * UI can show an accurate countdown rather than guessing.
     */
    @Column(name = "searching_since")
    private Instant searchingSince;

    @Version
    private Long version;
}
