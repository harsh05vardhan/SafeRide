package com.saferide.driver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates a driver's rate fields after ride events and recomputes
 * {@code reliabilityScore} in the same call, so callers ({@code RideService})
 * never have to remember a separate "now recompute" step.
 *
 * <p>Rates are updated via an exponential moving average rather than a
 * full recount over ride history each time - {@code newRate = oldRate *
 * (1 - alpha) + outcome * alpha}, where {@code outcome} is 100 for a
 * positive event and 0 for a negative one. This is O(1) per event
 * instead of O(all past rides), and is a standard, well-understood way to
 * track a rate that should adapt to recent behavior without a full
 * historical scan.
 */
@Service
@RequiredArgsConstructor
public class DriverReliabilityService {

    private final DriverRepository driverRepository;
    private final ReliabilityProperties properties;
    private final ReliabilityCalculator reliabilityCalculator;

    /** A driver accepted an assigned/open ride. */
    @Transactional
    public void recordAcceptance(Driver driver) {
        driver.setAcceptanceRate(ema(driver.getAcceptanceRate(), 100.0));
        recomputeAndSave(driver);
    }

    /** A driver explicitly declined a ride the matching engine assigned to them. */
    @Transactional
    public void recordDecline(Driver driver) {
        driver.setAcceptanceRate(ema(driver.getAcceptanceRate(), 0.0));
        recomputeAndSave(driver);
    }

    /** A driver completed a ride start to finish. */
    @Transactional
    public void recordCompletion(Driver driver) {
        driver.setCompletionRate(ema(driver.getCompletionRate(), 100.0));
        recomputeAndSave(driver);
    }

    /** A driver cancelled a ride after accepting it. */
    @Transactional
    public void recordCancellation(Driver driver) {
        driver.setCancellationRate(ema(driver.getCancellationRate(), 100.0));
        driver.setCompletionRate(ema(driver.getCompletionRate(), 0.0));
        recomputeAndSave(driver);
    }

    private void recomputeAndSave(Driver driver) {
        double score = reliabilityCalculator.calculate(
                driver.getRating(), driver.getAcceptanceRate(), driver.getCompletionRate(),
                driver.getOnTimeRate(), driver.getCancellationRate(), driver.getComplaintCount());
        driver.setReliabilityScore(score);
        driverRepository.save(driver);
    }

    private double ema(double oldRate, double outcome) {
        double alpha = properties.getEmaAlpha();
        double newRate = oldRate * (1 - alpha) + outcome * alpha;
        return Math.round(newRate * 100.0) / 100.0;
    }
}
