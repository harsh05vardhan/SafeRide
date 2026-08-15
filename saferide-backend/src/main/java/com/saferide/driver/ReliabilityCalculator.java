package com.saferide.driver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns a driver's individual rate fields into the single 0-100
 * {@code reliabilityScore} that {@code DriverMatchScorer} (Phase 6
 * matching) and the driver dashboard both read. Pure and stateless -
 * takes plain numbers, returns a plain number - so it's trivially
 * unit-testable independent of the {@link Driver} entity or persistence.
 *
 * <pre>
 *   score = ratingContribution + acceptanceContribution + completionContribution
 *           + onTimeContribution - cancellationPenalty - complaintPenalty
 * </pre>
 *
 * clamped to [0, 100].
 */
@Component
@RequiredArgsConstructor
public class ReliabilityCalculator {

    private final ReliabilityProperties properties;

    public double calculate(
            double rating, double acceptanceRate, double completionRate,
            double onTimeRate, double cancellationRate, int complaintCount) {

        double ratingContribution = (clamp01(rating / 5.0)) * properties.getRatingWeight();
        double acceptanceContribution = clamp01(acceptanceRate / 100.0) * properties.getAcceptanceWeight();
        double completionContribution = clamp01(completionRate / 100.0) * properties.getCompletionWeight();
        double onTimeContribution = clamp01(onTimeRate / 100.0) * properties.getOnTimeWeight();

        double cancellationPenalty = clamp01(cancellationRate / 100.0) * properties.getCancellationPenaltyWeight();
        double complaintPenalty = Math.min(
                complaintCount * properties.getComplaintPenaltyPerComplaint(), properties.getComplaintPenaltyCap());

        double score = ratingContribution + acceptanceContribution + completionContribution
                + onTimeContribution - cancellationPenalty - complaintPenalty;

        return Math.max(0.0, Math.min(100.0, round(score)));
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
