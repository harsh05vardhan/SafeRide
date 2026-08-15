package com.saferide.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferide.driver.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverMatchScorerTest {

    private DriverMatchScorer scorer;

    @BeforeEach
    void setUp() {
        MatchingProperties properties = new MatchingProperties();
        properties.setDistanceWeight(25.0);
        properties.setRatingWeight(25.0);
        properties.setReliabilityWeight(20.0);
        properties.setAcceptanceWeight(10.0);
        properties.setEtaWeight(20.0);
        properties.setCancellationPenaltyWeight(30.0);
        properties.setMaxRadiusKm(5.0);
        properties.setMaxEtaMinutes(15.0);
        properties.setAverageSpeedKmh(25.0);
        scorer = new DriverMatchScorer(properties);
    }

    private Driver driver(double distanceKmIrrelevantHere, double rating, double cancellationRate) {
        Driver driver = new Driver();
        driver.setRating(rating);
        driver.setCancellationRate(cancellationRate);
        driver.setReliabilityScore(70.0); // held equal across the spec's example so it doesn't skew the comparison
        driver.setAcceptanceRate(90.0);   // same
        return driver;
    }

    /**
     * The spec's own example: "Do NOT simply choose the nearest driver."
     * Driver C is closest but has the worst rating and highest
     * cancellation rate; Driver B is furthest of the three but has the
     * best rating and lowest cancellation rate. The balanced score must
     * rank B above both A and C, proving distance alone doesn't decide it.
     */
    @Test
    void balancesDistanceAgainstRatingAndCancellationRatherThanPickingTheNearestDriver() {
        Driver driverA = driver(0.8, 4.2, 15.0);
        Driver driverB = driver(1.2, 4.8, 3.0);
        Driver driverC = driver(0.5, 3.9, 20.0);

        double scoreA = scorer.score(driverA, 0.8);
        double scoreB = scorer.score(driverB, 1.2);
        double scoreC = scorer.score(driverC, 0.5);

        assertThat(scoreB).isGreaterThan(scoreA);
        assertThat(scoreB).isGreaterThan(scoreC);
        // The nearest driver (C) does NOT win despite being closest - the whole point of the example.
        assertThat(scoreC).isLessThan(scoreA);
    }

    @Test
    void closerDriversScoreHigherAllElseEqual() {
        Driver near = driver(0.5, 4.5, 5.0);
        Driver far = driver(3.0, 4.5, 5.0);

        assertThat(scorer.score(near, 0.5)).isGreaterThan(scorer.score(far, 3.0));
    }

    @Test
    void higherRatedDriversScoreHigherAllElseEqual() {
        Driver highRated = driver(1.0, 4.9, 5.0);
        Driver lowRated = driver(1.0, 3.5, 5.0);

        assertThat(scorer.score(highRated, 1.0)).isGreaterThan(scorer.score(lowRated, 1.0));
    }

    @Test
    void higherCancellationRateReducesScore() {
        Driver reliable = driver(1.0, 4.5, 2.0);
        Driver unreliable = driver(1.0, 4.5, 40.0);

        assertThat(scorer.score(reliable, 1.0)).isGreaterThan(scorer.score(unreliable, 1.0));
    }

    @Test
    void distanceBeyondMaxRadiusContributesZeroToDistanceAndEtaButScoreStaysNonNegative() {
        Driver farAway = driver(10.0, 4.5, 5.0);
        double score = scorer.score(farAway, 10.0); // beyond the 5km max radius

        assertThat(score).isGreaterThanOrEqualTo(0.0);
    }
}
