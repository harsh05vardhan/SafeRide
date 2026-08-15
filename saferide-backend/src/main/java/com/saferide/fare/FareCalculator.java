package com.saferide.fare;

import com.saferide.vehicle.VehicleType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The entire fare formula lives here and nowhere else:
 *
 * <pre>
 *   total = baseFare + (distanceKm * perKmRate) + (durationMinutes * perMinuteRate)
 *           + platformFee - discount
 * </pre>
 *
 * Pure and stateless - no persistence, no HTTP, no ride lookups - so it's
 * trivially unit-testable and is the one place {@code FareService} (real
 * rides) and {@code FareController} (standalone previews) both call
 * through, guaranteeing a preview and the fare actually attached to a
 * ride can never silently drift apart.
 */
@Component
@RequiredArgsConstructor
public class FareCalculator {

    private final FareProperties fareProperties;

    public FareBreakdown calculate(double distanceKm, int durationMinutes, VehicleType vehicleType, double discount) {
        if (distanceKm < 0) {
            throw new IllegalArgumentException("Distance cannot be negative");
        }
        if (durationMinutes < 0) {
            throw new IllegalArgumentException("Duration cannot be negative");
        }
        if (discount < 0) {
            throw new IllegalArgumentException("Discount cannot be negative");
        }

        FareProperties.VehicleRate rate = fareProperties.getVehicleRates().get(vehicleType);
        if (rate == null) {
            throw new IllegalStateException("No fare rate configured for vehicle type: " + vehicleType);
        }

        double baseFare = round(rate.getBaseFare());
        double distanceFare = round(distanceKm * rate.getPerKmRate());
        double timeFare = round(durationMinutes * rate.getPerMinuteRate());
        double platformFee = round(fareProperties.getPlatformFee());

        double subtotal = baseFare + distanceFare + timeFare + platformFee;
        // A discount can never take the fare below zero - clamp rather than trust the caller's number.
        double effectiveDiscount = round(Math.min(discount, subtotal));
        double totalFare = round(subtotal - effectiveDiscount);

        return new FareBreakdown(
                baseFare, distanceFare, timeFare, platformFee, effectiveDiscount, totalFare, fareProperties.getCurrency());
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
