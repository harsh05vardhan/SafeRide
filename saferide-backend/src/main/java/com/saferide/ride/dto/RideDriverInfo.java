package com.saferide.ride.dto;

/** Lightweight driver info embedded in {@link RideResponse} once a driver is assigned - not the full driver profile. */
public record RideDriverInfo(
        Long driverId,
        String name,
        String phone,
        Double rating,
        String vehicleModel,
        String vehicleNumber,
        String vehicleColor) {
}
