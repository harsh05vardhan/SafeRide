package com.saferide.fare.dto;

import com.saferide.fare.FareBreakdown;

public record FareBreakdownResponse(
        double baseFare,
        double distanceFare,
        double timeFare,
        double platformFee,
        double discount,
        double totalFare,
        String currency) {

    public static FareBreakdownResponse from(FareBreakdown breakdown) {
        return new FareBreakdownResponse(
                breakdown.baseFare(), breakdown.distanceFare(), breakdown.timeFare(),
                breakdown.platformFee(), breakdown.discount(), breakdown.totalFare(), breakdown.currency());
    }
}
