package com.saferide.ride;

import com.saferide.driver.Driver;
import com.saferide.ride.dto.RideDriverInfo;
import com.saferide.vehicle.Vehicle;
import com.saferide.vehicle.VehicleService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link RideDriverInfo} for a ride's assigned driver - shared by
 * every place that needs to describe "who's driving this ride" alongside
 * a ride (originally just {@code RideService#toResponse}; now also
 * {@code SafetyService}/{@code AdminSafetyService} for the safety-alert
 * detail screen), so this doesn't get re-derived slightly differently in
 * each place.
 */
@Component
@RequiredArgsConstructor
public class RideDriverInfoFactory {

    private final VehicleService vehicleService;

    /** Null if the ride has no assigned driver yet. */
    public RideDriverInfo from(Ride ride) {
        Driver driver = ride.getDriver();
        if (driver == null) {
            return null;
        }
        Vehicle vehicle = vehicleService.getActiveVehicleEntity(driver.getId()).orElse(null);
        return build(driver, vehicle);
    }

    /**
     * Batch equivalent of {@link #from}, keyed by ride id - a list
     * endpoint (My Rides, driver ride history, the admin dashboard's
     * recent-rides) calling {@link #from} once per row was a real N+1:
     * one extra database round trip per ride, and with the backend and
     * database in different regions (Singapore/Bangalore) each of those
     * round trips is genuinely slow, not just extra SQL. This fetches
     * every distinct driver's active vehicle in a single query instead.
     */
    public Map<Long, RideDriverInfo> fromBatch(List<Ride> rides) {
        Map<Long, Driver> driverByRideId = new HashMap<>();
        for (Ride ride : rides) {
            if (ride.getDriver() != null) {
                driverByRideId.put(ride.getId(), ride.getDriver());
            }
        }

        List<Long> driverIds = driverByRideId.values().stream().map(Driver::getId).distinct().toList();
        Map<Long, Vehicle> activeVehicleByDriverId = vehicleService.getActiveVehiclesByDriverIds(driverIds);

        Map<Long, RideDriverInfo> result = new HashMap<>();
        for (Map.Entry<Long, Driver> entry : driverByRideId.entrySet()) {
            Driver driver = entry.getValue();
            result.put(entry.getKey(), build(driver, activeVehicleByDriverId.get(driver.getId())));
        }
        return result;
    }

    // Shows the driver's *current* active vehicle. A real system would snapshot which vehicle
    // was used at accept time; that field isn't in the Ride schema.
    private RideDriverInfo build(Driver driver, Vehicle vehicle) {
        return new RideDriverInfo(
                driver.getId(),
                driver.getUser().getName(),
                driver.getUser().getPhone(),
                driver.getRating(),
                Optional.ofNullable(vehicle).map(Vehicle::getVehicleModel).orElse(null),
                Optional.ofNullable(vehicle).map(Vehicle::getVehicleNumber).orElse(null),
                Optional.ofNullable(vehicle).map(Vehicle::getVehicleColor).orElse(null),
                driver.getUser().getAvatarUrl());
    }
}
