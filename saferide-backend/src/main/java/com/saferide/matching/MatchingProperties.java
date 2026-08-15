package com.saferide.matching;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Backs {@code app.matching.*} - every weight {@link DriverMatchScorer} uses, kept out of code so tuning them is a config change. */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.matching")
public class MatchingProperties {

    private double distanceWeight;
    private double ratingWeight;
    private double reliabilityWeight;
    private double acceptanceWeight;
    private double etaWeight;
    private double cancellationPenaltyWeight;

    /** Candidates further than this from pickup score 0 on the distance component and are effectively out of range. */
    private double maxRadiusKm;

    /** ETA beyond this scores 0 on the ETA component. */
    private double maxEtaMinutes;

    /** Assumed average speed used only to turn distance into an ETA for scoring - independent of MapService's own estimate. */
    private double averageSpeedKmh;
}
