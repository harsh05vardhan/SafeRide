package com.saferide.matching;

import com.saferide.driver.Driver;
import com.saferide.ride.Ride;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Records "driver X is no longer eligible for ride Y" - the mechanism
 * behind the business rule "a previously cancelled/declined driver must
 * not receive the same ride again". {@link com.saferide.matching.DriverMatchingService}
 * excludes every driver with a row here from candidate selection for that
 * ride, for the rest of that ride's lifetime.
 */
@Entity
@Table(
        name = "ride_driver_exclusions",
        indexes = @Index(name = "idx_ride_driver_exclusions_ride_id", columnList = "ride_id"))
@Getter
@Setter
@NoArgsConstructor
public class RideDriverExclusion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExclusionReason reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
