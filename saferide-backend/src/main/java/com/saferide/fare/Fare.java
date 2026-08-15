package com.saferide.fare;

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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The current fare breakdown for a ride - one row per ride, updated in
 * place (not appended) when a destination change forces a recalculation.
 * {@code updatedAt} (from {@link Auditable}) doubles as "when was this
 * last recalculated". Never trust a total the frontend sends back -
 * every value here always comes from {@link FareCalculator}.
 */
@Entity
@Table(name = "fares")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
public class Fare extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ride_id", nullable = false, unique = true)
    private Ride ride;

    @Column(name = "base_fare", nullable = false)
    private Double baseFare;

    @Column(name = "distance_fare", nullable = false)
    private Double distanceFare;

    @Column(name = "time_fare", nullable = false)
    private Double timeFare;

    @Column(name = "platform_fee", nullable = false)
    private Double platformFee;

    @Column(nullable = false)
    private Double discount;

    @Column(name = "total_fare", nullable = false)
    private Double totalFare;

    @Column(nullable = false, length = 3)
    private String currency;

    public void applyBreakdown(FareBreakdown breakdown) {
        this.baseFare = breakdown.baseFare();
        this.distanceFare = breakdown.distanceFare();
        this.timeFare = breakdown.timeFare();
        this.platformFee = breakdown.platformFee();
        this.discount = breakdown.discount();
        this.totalFare = breakdown.totalFare();
        this.currency = breakdown.currency();
    }
}
