package com.saferide.driver;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Backs {@code app.reliability.*} - weights for {@link ReliabilityCalculator} and the smoothing factor for {@link DriverReliabilityService}. */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.reliability")
public class ReliabilityProperties {

    private double ratingWeight;
    private double acceptanceWeight;
    private double completionWeight;
    private double onTimeWeight;
    private double cancellationPenaltyWeight;
    private double complaintPenaltyPerComplaint;
    private double complaintPenaltyCap;

    /**
     * Exponential-moving-average smoothing factor applied to each new
     * event (0-1): how much a single ride's outcome shifts a rate versus
     * how much history is retained. 0.2 means one event moves a rate 20%
     * of the way from its old value to the event's outcome.
     */
    private double emaAlpha;
}
