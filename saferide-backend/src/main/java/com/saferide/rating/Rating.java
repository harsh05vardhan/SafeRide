package com.saferide.rating;

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
 * One row per ride - the customer rating the driver they just rode with.
 * There is no symmetric driver-rates-customer flow (the spec's
 * {@code Driver.rating}/{@code averageRating} is the only rating field
 * anywhere in the schema); a driver-side rating would need a matching
 * field on {@code User}/{@code Customer} that doesn't exist, so it's left
 * out rather than half-built.
 */
@Entity
@Table(name = "ratings")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
public class Rating extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ride_id", nullable = false, unique = true)
    private Ride ride;

    /** 1-5, validated by {@code RatingRequest}. */
    @Column(nullable = false)
    private Integer score;

    @Column(length = 500)
    private String comment;
}
