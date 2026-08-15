package com.saferide.fare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saferide.vehicle.VehicleType;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FareCalculatorTest {

    private FareCalculator fareCalculator;

    @BeforeEach
    void setUp() {
        FareProperties.VehicleRate sedanRate = new FareProperties.VehicleRate();
        sedanRate.setBaseFare(40.0);
        sedanRate.setPerKmRate(15.0);
        sedanRate.setPerMinuteRate(2.0);

        FareProperties.VehicleRate miniRate = new FareProperties.VehicleRate();
        miniRate.setBaseFare(30.0);
        miniRate.setPerKmRate(10.0);
        miniRate.setPerMinuteRate(1.0);

        FareProperties properties = new FareProperties();
        properties.setPlatformFee(10.0);
        properties.setCurrency("INR");
        properties.setVehicleRates(Map.of(VehicleType.SEDAN, sedanRate, VehicleType.MINI, miniRate));

        fareCalculator = new FareCalculator(properties);
    }

    @Test
    void matchesTheSpecWorkedExample() {
        // Distance 8km, duration 20min, base 40, distance-rate 15/km, time-rate 2/min, platform 10, no discount
        // => 40 + 120 + 40 + 10 - 0 = 210
        FareBreakdown breakdown = fareCalculator.calculate(8.0, 20, VehicleType.SEDAN, 0.0);

        assertThat(breakdown.baseFare()).isEqualTo(40.0);
        assertThat(breakdown.distanceFare()).isEqualTo(120.0);
        assertThat(breakdown.timeFare()).isEqualTo(40.0);
        assertThat(breakdown.platformFee()).isEqualTo(10.0);
        assertThat(breakdown.discount()).isEqualTo(0.0);
        assertThat(breakdown.totalFare()).isEqualTo(210.0);
        assertThat(breakdown.currency()).isEqualTo("INR");
    }

    @Test
    void discountReducesTheTotalByExactlyItsAmount() {
        FareBreakdown breakdown = fareCalculator.calculate(8.0, 20, VehicleType.SEDAN, 25.0);
        assertThat(breakdown.discount()).isEqualTo(25.0);
        assertThat(breakdown.totalFare()).isEqualTo(185.0); // 210 - 25
    }

    @Test
    void discountIsClampedSoTotalNeverGoesNegative() {
        FareBreakdown breakdown = fareCalculator.calculate(8.0, 20, VehicleType.SEDAN, 10_000.0);
        assertThat(breakdown.totalFare()).isEqualTo(0.0);
        assertThat(breakdown.discount()).isEqualTo(210.0); // clamped to the subtotal, not the requested 10,000
    }

    @Test
    void differentVehicleTypesProduceDifferentTotalsForTheSameTrip() {
        FareBreakdown sedan = fareCalculator.calculate(8.0, 20, VehicleType.SEDAN, 0.0);
        FareBreakdown mini = fareCalculator.calculate(8.0, 20, VehicleType.MINI, 0.0);

        assertThat(mini.totalFare()).isLessThan(sedan.totalFare());
    }

    @Test
    void zeroDistanceAndDurationStillChargesBaseFarePlusPlatformFee() {
        FareBreakdown breakdown = fareCalculator.calculate(0.0, 0, VehicleType.SEDAN, 0.0);
        assertThat(breakdown.totalFare()).isEqualTo(50.0); // 40 base + 10 platform
    }

    @Test
    void rejectsNegativeDistance() {
        assertThatThrownBy(() -> fareCalculator.calculate(-1.0, 10, VehicleType.SEDAN, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeDuration() {
        assertThatThrownBy(() -> fareCalculator.calculate(5.0, -1, VehicleType.SEDAN, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAVehicleTypeWithNoConfiguredRate() {
        assertThatThrownBy(() -> fareCalculator.calculate(5.0, 10, VehicleType.SUV, 0.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUV");
    }
}
