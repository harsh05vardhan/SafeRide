package com.saferide.map;

/**
 * Shared geo-distance math. Extracted out of {@link MockMapService} so
 * {@code DriverMatchScorer} (Phase 6) can compute a straight-line
 * driver-to-pickup distance without depending on the routing service or
 * duplicating the formula.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {
    }

    /** Great-circle distance between two lat/lng points, in kilometers. */
    public static double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
