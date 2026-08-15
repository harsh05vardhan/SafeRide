package com.saferide.fare;

/** Pure output of {@link FareCalculator} - not persisted itself, just the numbers behind a {@code Fare} row or a preview call. */
public record FareBreakdown(
        double baseFare,
        double distanceFare,
        double timeFare,
        double platformFee,
        double discount,
        double totalFare,
        String currency) {
}
