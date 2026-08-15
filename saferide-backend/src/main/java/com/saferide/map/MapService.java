package com.saferide.map;

/**
 * The one seam between SafeRide and whatever powers distance/duration
 * lookups. {@link MockMapService} is the only implementation today; a real
 * provider (Google Maps, Mapbox) becomes a second {@code @Service}
 * implementation later - nothing outside this package changes. Ride and
 * fare calculation both depend on this interface, never on a concrete
 * provider, and never trust a distance/duration the frontend supplies.
 */
public interface MapService {

    /**
     * @param pickupLatitude pickup point latitude
     * @param pickupLongitude pickup point longitude
     * @param destinationLatitude destination point latitude
     * @param destinationLongitude destination point longitude
     * @return road distance and expected travel time between the two points
     */
    RouteEstimate estimateRoute(
            double pickupLatitude, double pickupLongitude,
            double destinationLatitude, double destinationLongitude);
}
