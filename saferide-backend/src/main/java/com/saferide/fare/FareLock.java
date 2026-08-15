package com.saferide.fare;

import com.saferide.ride.Ride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * The actual "no unauthorized extra fare" guarantee: once
 * {@code status == LOCKED}, {@code lockedAmount} is what the driver gets
 * shown and what payment (Phase 12) charges - there is no endpoint, for
 * either the driver or the customer, that can change it. The only way a
 * new amount can ever exist is a destination change, which explicitly
 * unlocks this row (recording {@code unlockedAt}) and requires the
 * customer to confirm the new fare before it locks again.
 *
 * <p>One row per ride, reused across lock/unlock/re-lock cycles - not a
 * history table. If an audit trail of every lock/unlock is ever needed,
 * {@link com.saferide.common.AuditLog} is where it would go, not here.
 */
@Entity
@Table(name = "fare_locks")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class FareLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ride_id", nullable = false, unique = true)
    private Ride ride;

    @Column(name = "locked_amount", nullable = false)
    private Double lockedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FareLockStatus status;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "unlocked_at")
    private Instant unlockedAt;

    public boolean isLocked() {
        return status == FareLockStatus.LOCKED;
    }
}
