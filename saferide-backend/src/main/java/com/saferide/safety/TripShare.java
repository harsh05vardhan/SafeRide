package com.saferide.safety;

import com.saferide.common.Auditable;
import com.saferide.ride.Ride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One shareable, unauthenticated read-only tracking link per ride -
 * {@code POST /api/rides/{id}/share}, the backend behind the "Share Ride"
 * button. {@code token} is the only thing that guards it (a long random
 * value, never a sequential id - see {@link SafetyService}), and
 * {@link com.saferide.safety.dto.PublicTripResponse} is deliberately a
 * stripped-down view (no OTP, no phone numbers) - a link is, by design,
 * shareable with anyone the customer chooses to send it to.
 */
@Entity
@Table(name = "trip_shares")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
public class TripShare extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ride_id", nullable = false, unique = true)
    private Ride ride;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
