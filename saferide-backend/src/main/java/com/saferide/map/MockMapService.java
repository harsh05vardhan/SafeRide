package com.saferide.map;

import org.springframework.stereotype.Service;

/**
 * Development stand-in for a real routing provider. Computes straight-line
 * (Haversine) distance and inflates it by {@link #ROAD_DISTANCE_FACTOR} to
 * roughly approximate real road distance, then derives a duration from an
 * assumed average city speed. Deterministic and offline - no API key, no
 * network call, no rate limit - which is exactly what local development
 * and tests need. Swapping in Google Maps/Mapbox later means adding a
 * second {@link MapService} implementation and pointing configuration at
 * it; every caller (ride creation, fare calculation) is unaffected.
 */
@Service
public class MockMapService implements MapService {

    private static final double EARTH_RADIUS_KM = 6371.0;
    /** Real roads are never a straight line - this is a rough, deterministic stand-in for a routing engine. */
    private static final double ROAD_DISTANCE_FACTOR = 1.3;
    /** Assumed average city driving speed, used only to derive an ETA from distance. */
    private static final double AVERAGE_SPEED_KMH = 25.0;
    private static final double MINIMUM_DISTANCE_KM = 0.5;

    @Override
    public RouteEstimate estimateRoute(
            double pickupLatitude, double pickupLongitude,
            double destinationLatitude, double destinationLongitude) {

        double straightLineKm = haversineDistanceKm(
                pickupLatitude, pickupLongitude, destinationLatitude, destinationLongitude);

        double distanceKm = Math.max(straightLineKm * ROAD_DISTANCE_FACTOR, MINIMUM_DISTANCE_KM);
        double roundedDistanceKm = Math.round(distanceKm * 100.0) / 100.0;

        int durationMinutes = (int) Math.ceil((distanceKm / AVERAGE_SPEED_KMH) * 60.0);

        return new RouteEstimate(roundedDistanceKm, Math.max(durationMinutes, 1));
    }

    private double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
