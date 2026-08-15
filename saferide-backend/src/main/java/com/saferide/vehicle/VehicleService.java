package com.saferide.vehicle;

import com.saferide.common.VerificationStatus;
import com.saferide.driver.Driver;
import com.saferide.driver.DriverService;
import com.saferide.exception.DuplicateResourceException;
import com.saferide.exception.ResourceNotFoundException;
import com.saferide.exception.UnauthorizedException;
import com.saferide.vehicle.dto.VehicleRegisterRequest;
import com.saferide.vehicle.dto.VehicleResponse;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final DriverService driverService;

    /**
     * Registers a new vehicle for the calling driver and makes it the
     * active one (deactivating whatever was active before) - "add a
     * vehicle" and "switch vehicles" are the same operation from the
     * driver's point of view.
     */
    @Transactional
    public VehicleResponse registerVehicle(Long userId, VehicleRegisterRequest request) {
        Driver driver = driverService.getDriverByUserId(userId);

        if (vehicleRepository.existsByVehicleNumber(request.vehicleNumber())) {
            throw new DuplicateResourceException("Vehicle number is already registered", "VEHICLE_ALREADY_REGISTERED");
        }

        vehicleRepository.findByDriverIdAndActiveTrue(driver.getId()).ifPresent(current -> {
            current.setActive(false);
            vehicleRepository.save(current);
        });

        Vehicle vehicle = new Vehicle();
        vehicle.setDriver(driver);
        vehicle.setVehicleType(request.vehicleType());
        vehicle.setVehicleModel(request.vehicleModel());
        vehicle.setVehicleNumber(request.vehicleNumber());
        vehicle.setVehicleColor(request.vehicleColor());
        vehicle.setSeatCapacity(request.seatCapacity());
        vehicle.setVerificationStatus(VerificationStatus.PENDING);
        vehicle.setActive(true);

        return toResponse(vehicleRepository.save(vehicle));
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listForDriver(Long userId) {
        Driver driver = driverService.getDriverByUserId(userId);
        return vehicleRepository.findByDriverIdOrderByCreatedAtDesc(driver.getId())
                .stream().map(this::toResponse).toList();
    }

    /** Switches which of the driver's own (already-registered) vehicles is active, without re-registering it. */
    @Transactional
    public VehicleResponse activate(Long userId, Long vehicleId) {
        Driver driver = driverService.getDriverByUserId(userId);
        Vehicle target = getOwnedVehicle(driver.getId(), vehicleId);

        vehicleRepository.findByDriverIdAndActiveTrue(driver.getId()).ifPresent(current -> {
            if (!current.getId().equals(target.getId())) {
                current.setActive(false);
                vehicleRepository.save(current);
            }
        });

        target.setActive(true);
        return toResponse(vehicleRepository.save(target));
    }

    /** Admin action - no ownership check, any admin may review any vehicle. */
    @Transactional
    public VehicleResponse setVerificationStatus(Long vehicleId, VerificationStatus status) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> ResourceNotFoundException.of("Vehicle", vehicleId));
        vehicle.setVerificationStatus(status);
        return toResponse(vehicleRepository.save(vehicle));
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listForDriverId(Long driverId) {
        return vehicleRepository.findByDriverIdOrderByCreatedAtDesc(driverId).stream().map(this::toResponse).toList();
    }

    /**
     * Cross-domain entry point for ride booking/matching: the entity (not
     * a DTO) is returned deliberately so callers can check both
     * {@code vehicleType} and {@code verificationStatus} without a second
     * round trip.
     */
    @Transactional(readOnly = true)
    public Optional<Vehicle> getActiveVehicleEntity(Long driverId) {
        return vehicleRepository.findByDriverIdAndActiveTrue(driverId);
    }

    private Vehicle getOwnedVehicle(Long driverId, Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> ResourceNotFoundException.of("Vehicle", vehicleId));
        if (!vehicle.getDriver().getId().equals(driverId)) {
            throw new UnauthorizedException("This vehicle does not belong to your driver profile");
        }
        return vehicle;
    }

    private VehicleResponse toResponse(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getDriver().getId(),
                vehicle.getVehicleType(),
                vehicle.getVehicleModel(),
                vehicle.getVehicleNumber(),
                vehicle.getVehicleColor(),
                vehicle.getSeatCapacity(),
                vehicle.getVerificationStatus(),
                vehicle.isActive());
    }
}
