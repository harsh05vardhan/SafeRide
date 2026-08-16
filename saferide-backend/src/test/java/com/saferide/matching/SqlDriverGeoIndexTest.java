package com.saferide.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.saferide.driver.Driver;
import com.saferide.driver.DriverOnlineStatus;
import com.saferide.driver.DriverRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The pre-Phase-11 full-scan-then-filter behavior, now isolated behind {@link DriverGeoIndex} - see its class doc. */
@ExtendWith(MockitoExtension.class)
class SqlDriverGeoIndexTest {

    // Guwahati Railway Station - same point RideIntegrationTest/MatchingIntegrationTest's "near" fixtures use.
    private static final double PICKUP_LAT = 26.1845;
    private static final double PICKUP_LNG = 91.7362;

    @Mock private DriverRepository driverRepository;
    private SqlDriverGeoIndex geoIndex;

    private Driver onlineDriverAt(long id, double lat, double lng) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setOnlineStatus(DriverOnlineStatus.ONLINE);
        driver.setCurrentLatitude(lat);
        driver.setCurrentLongitude(lng);
        return driver;
    }

    @Test
    void returnsOnlyDriversWithinRadiusSortedNearestFirst() {
        geoIndex = new SqlDriverGeoIndex(driverRepository);
        Driver near = onlineDriverAt(1L, PICKUP_LAT + 0.01, PICKUP_LNG);     // ~1.1km
        Driver mid = onlineDriverAt(2L, PICKUP_LAT + 0.03, PICKUP_LNG);     // ~3.3km
        Driver farOutsideRadius = onlineDriverAt(3L, PICKUP_LAT + 0.5, PICKUP_LNG); // ~55km
        when(driverRepository.findByOnlineStatus(DriverOnlineStatus.ONLINE))
                .thenReturn(List.of(farOutsideRadius, near, mid));

        List<Long> result = geoIndex.findWithinRadius(PICKUP_LAT, PICKUP_LNG, 5.0);

        assertThat(result).containsExactly(1L, 2L);
    }

    @Test
    void skipsOnlineDriversWithNoReportedLocationYet() {
        geoIndex = new SqlDriverGeoIndex(driverRepository);
        Driver noLocation = new Driver();
        noLocation.setId(9L);
        noLocation.setOnlineStatus(DriverOnlineStatus.ONLINE);
        when(driverRepository.findByOnlineStatus(DriverOnlineStatus.ONLINE)).thenReturn(List.of(noLocation));

        assertThat(geoIndex.findWithinRadius(PICKUP_LAT, PICKUP_LNG, 5.0)).isEmpty();
    }

    @Test
    void indexLocationAndRemoveDriverAreNoOpsSinceMySqlIsAlreadyTheIndex() {
        geoIndex = new SqlDriverGeoIndex(driverRepository);
        // Must not throw, and must never touch the repository - MySQL's Driver row already is the index.
        geoIndex.indexLocation(1L, PICKUP_LAT, PICKUP_LNG);
        geoIndex.removeDriver(1L);
        org.mockito.Mockito.verifyNoInteractions(driverRepository);
    }
}
