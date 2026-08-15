package com.saferide.fare;

import com.saferide.vehicle.VehicleType;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Backs {@code app.fare.*} in application.yml. Rates are per vehicle
 * class (a Mini costs less per km than an SUV) - keeping them in
 * configuration rather than hardcoded in {@link FareCalculator} means a
 * fare adjustment is a config change, not a code change and redeploy.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.fare")
public class FareProperties {

    /** Flat fee added to every ride regardless of distance/time, same across vehicle types. */
    private double platformFee;

    private String currency;

    private Map<VehicleType, VehicleRate> vehicleRates;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class VehicleRate {
        private double baseFare;
        private double perKmRate;
        private double perMinuteRate;
    }
}
