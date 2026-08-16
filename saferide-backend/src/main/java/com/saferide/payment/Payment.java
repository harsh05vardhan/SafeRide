package com.saferide.payment;

import com.saferide.common.Auditable;
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
 * One row per ride, created only once {@code POST /api/rides/{id}/pay} is
 * called - never before. {@code amount}/{@code currency} are copied
 * straight from the ride's {@code FareLock} at payment time (see
 * {@link PaymentService}), never accepted from the client - the same "the
 * driver cannot demand an unauthorized extra fare" guarantee extends all
 * the way through to what actually gets charged.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
public class Payment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ride_id", nullable = false, unique = true)
    private Ride ride;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentStatus status;

    /** Opaque reference from {@link PaymentGateway} - a real provider's transaction id, once one exists. */
    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @Column(name = "paid_at")
    private Instant paidAt;
}
