package com.saferide.driver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReliabilityCalculatorTest {

    private ReliabilityCalculator calculator;

    @BeforeEach
    void setUp() {
        ReliabilityProperties properties = new ReliabilityProperties();
        properties.setRatingWeight(30.0);
        properties.setAcceptanceWeight(25.0);
        properties.setCompletionWeight(25.0);
        properties.setOnTimeWeight(20.0);
        properties.setCancellationPenaltyWeight(30.0);
        properties.setComplaintPenaltyPerComplaint(5.0);
        properties.setComplaintPenaltyCap(30.0);
        properties.setEmaAlpha(0.2);
        calculator = new ReliabilityCalculator(properties);
    }

    @Test
    void perfectDriverScoresTheFullOneHundred() {
        double score = calculator.calculate(5.0, 100.0, 100.0, 100.0, 0.0, 0);
        assertThat(score).isEqualTo(100.0);
    }

    @Test
    void worstPossibleDriverScoresZeroNotNegative() {
        double score = calculator.calculate(0.0, 0.0, 0.0, 0.0, 100.0, 100);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void cancellationRateReducesScoreProportionally() {
        double noCancellations = calculator.calculate(5.0, 100.0, 100.0, 100.0, 0.0, 0);
        double halfCancellations = calculator.calculate(5.0, 100.0, 100.0, 100.0, 50.0, 0);
        assertThat(halfCancellations).isEqualTo(noCancellations - 15.0); // 50% of the 30-point penalty weight
    }

    @Test
    void complaintPenaltyIsCappedRatherThanUnbounded() {
        double fewComplaints = calculator.calculate(5.0, 100.0, 100.0, 100.0, 0.0, 3); // 15-point penalty
        double manyComplaints = calculator.calculate(5.0, 100.0, 100.0, 100.0, 0.0, 50); // would be 250 uncapped

        assertThat(fewComplaints).isEqualTo(85.0); // 100 - 15
        assertThat(manyComplaints).isEqualTo(70.0); // 100 - 30 (capped), not negative
    }

    @Test
    void averageDriverScoresInTheMiddleOfTheRange() {
        double score = calculator.calculate(3.5, 70.0, 70.0, 70.0, 10.0, 1);
        assertThat(score).isBetween(30.0, 70.0);
    }
}
