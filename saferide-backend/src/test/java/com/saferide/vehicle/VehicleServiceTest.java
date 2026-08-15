package com.saferide.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saferide.common.VerificationStatus;
import com.saferide.driver.Driver;
import com.saferide.driver.DriverService;
import com.saferide.vehicle.dto.VehicleRegisterRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private DriverService driverService;

    private VehicleService vehicleService;
    private Driver driver;

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleService(vehicleRepository, driverService);
        driver = new Driver();
        driver.setId(1L);
    }

    private VehicleRegisterRequest sampleRequest(String number) {
        return new VehicleRegisterRequest(VehicleType.SEDAN, "Honda City", number, "White", 4);
    }

    @Test
    void registeringAVehicleDeactivatesThePreviouslyActiveOne() {
        when(driverService.getDriverByUserId(1L)).thenReturn(driver);
        when(vehicleRepository.existsByVehicleNumber("AS-01-NEW-0001")).thenReturn(false);

        Vehicle previouslyActive = new Vehicle();
        previouslyActive.setId(10L);
        previouslyActive.setDriver(driver);
        previouslyActive.setActive(true);
        when(vehicleRepository.findByDriverIdAndActiveTrue(1L)).thenReturn(Optional.of(previouslyActive));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        vehicleService.registerVehicle(1L, sampleRequest("AS-01-NEW-0001"));

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository, times(2)).save(captor.capture());

        Vehicle deactivated = captor.getAllValues().get(0);
        assertThat(deactivated.getId()).isEqualTo(10L);
        assertThat(deactivated.isActive()).isFalse();

        Vehicle newlyActive = captor.getAllValues().get(1);
        assertThat(newlyActive.isActive()).isTrue();
        assertThat(newlyActive.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
    }

    @Test
    void firstVehicleForADriverIsActiveWithNoPriorDeactivation() {
        when(driverService.getDriverByUserId(1L)).thenReturn(driver);
        when(vehicleRepository.existsByVehicleNumber("AS-01-FIRST-0001")).thenReturn(false);
        when(vehicleRepository.findByDriverIdAndActiveTrue(1L)).thenReturn(Optional.empty());
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = vehicleService.registerVehicle(1L, sampleRequest("AS-01-FIRST-0001"));

        assertThat(response.active()).isTrue();
        verify(vehicleRepository, times(1)).save(any(Vehicle.class));
    }
}
