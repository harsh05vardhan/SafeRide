package com.saferide.ride;

import com.saferide.driver.Driver;
import com.saferide.ride.dto.RideDriverInfo;
import com.saferide.vehicle.Vehicle;
import com.saferide.vehicle.VehicleService;
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
        // Shows the driver's *current* active vehicle. A real system would snapshot which
        // vehicle was used at accept time; that field isn't in the Ride schema.
        Optional<Vehicle> vehicle = vehicleService.getActiveVehicleEntity(driver.getId());
        return new RideDriverInfo(
                driver.getId(),
                driver.getUser().getName(),
                driver.getUser().getPhone(),
                driver.getRating(),
                vehicle.map(Vehicle::getVehicleModel).orElse(null),
                vehicle.map(Vehicle::getVehicleNumber).orElse(null),
                vehicle.map(Vehicle::getVehicleColor).orElse(null));
    }
}
