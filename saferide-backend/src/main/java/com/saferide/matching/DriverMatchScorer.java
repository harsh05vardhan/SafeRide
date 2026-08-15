package com.saferide.matching;

import com.saferide.driver.Driver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The matching algorithm's scoring function, isolated in its own class
 * exactly as the spec asks - pure and stateless (a driver's stats plus a
 * distance in, a score out), so it's unit-testable without a database,
 * HTTP layer, or any other collaborator.
 *
 * <p>Deliberately balances distance against rating/reliability/cancellation
 * rather than just picking the nearest driver: a driver 1.2km away with a
 * 4.8 rating and a 3% cancellation rate should - and with the default
 * weights below, does - outscore one 0.5km away with a 3.9 rating and a
 * 20% cancellation rate. See {@code DriverMatchScorerTest} for that exact
 * scenario worked out.
 *
 * <pre>
 *   score = distanceScore + ratingScore + reliabilityScore + acceptanceScore
 *           + etaScore - cancellationPenalty
 * </pre>
 *
 * Each positive component is a weighted fraction (closer/higher/more
 * reliable = closer to that component's full weight); the cancellation
 * penalty is subtracted, not inverted into a positive term, to keep its
 * effect legible on its own.
 */
@Component
@RequiredArgsConstructor
public class DriverMatchScorer {

    private final MatchingProperties properties;

    public double score(Driver driver, double distanceKm) {
        double distanceScore = fractionOfRemaining(distanceKm, properties.getMaxRadiusKm()) * properties.getDistanceWeight();
        double ratingScore = clamp01(driver.getRating() / 5.0) * properties.getRatingWeight();
        double reliabilityScore = clamp01(driver.getReliabilityScore() / 100.0) * properties.getReliabilityWeight();
        double acceptanceScore = clamp01(driver.getAcceptanceRate() / 100.0) * properties.getAcceptanceWeight();

        double etaMinutes = (distanceKm / properties.getAverageSpeedKmh()) * 60.0;
        double etaScore = fractionOfRemaining(etaMinutes, properties.getMaxEtaMinutes()) * properties.getEtaWeight();

        double cancellationPenalty = clamp01(driver.getCancellationRate() / 100.0) * properties.getCancellationPenaltyWeight();

        double total = distanceScore + ratingScore + reliabilityScore + acceptanceScore + etaScore - cancellationPenalty;
        return Math.round(total * 100.0) / 100.0;
    }

    /** 1.0 at value=0, sliding down to 0.0 at value=max (and beyond) - "closer/sooner is better, out of range is worthless". */
    private double fractionOfRemaining(double value, double max) {
        if (max <= 0) {
            return 0.0;
        }
        return clamp01(1.0 - (value / max));
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
