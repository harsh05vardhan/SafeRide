package com.saferide.ride;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferide.testsupport.AbstractIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code RideService#autoExpireStaleSearches} - a ride stuck at
 * {@code SEARCHING_DRIVER} (nobody nearby, or every candidate has declined)
 * past its timeout gets auto-cancelled with {@code NO_DRIVER_FOUND} rather
 * than left searching forever; a ride still well within its timeout is left
 * alone. Calls the scheduled method directly instead of waiting on
 * {@code @Scheduled}'s real 10s cadence - what it does when it runs is what
 * this test cares about, not when Spring decides to run it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RideSearchTimeoutIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RideService rideService;
    @Autowired private RideRepository rideRepository;

    // Same "far from every other test class's drivers" reasoning as MatchingIntegrationTest's
    // remote-pickup test - guarantees no eligible driver so the ride stays SEARCHING_DRIVER.
    private static final double REMOTE_LAT = 19.0760; // Mumbai
    private static final double REMOTE_LNG = 72.8777;

    private String registerCustomer(String email, String phone) throws Exception {
        String payload = """
                {"name":"Timeout Customer","email":"%s","phone":"%s","password":"Password123!","role":"CUSTOMER"}
                """.formatted(email, phone);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"Password123!\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/accessToken").asText();
    }

    private long createAndConfirmRide(String customerToken) throws Exception {
        String payload = """
                {"pickupAddress":"Somewhere remote","destinationAddress":"Somewhere else",
                 "pickupLatitude":%s,"pickupLongitude":%s,
                 "destinationLatitude":19.2183,"destinationLongitude":72.9781,"vehicleType":"SEDAN"}
                """.formatted(REMOTE_LAT, REMOTE_LNG);
        String createBody = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long rideId = objectMapper.readTree(createBody).at("/data/id").asLong();

        mockMvc.perform(post("/api/rides/" + rideId + "/fare/confirm").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());
        return rideId;
    }

    @Test
    void staleSearchAutoCancelsWithNoDriverFoundReason() throws Exception {
        String customerToken = registerCustomer("timeout-stale@example.com", "9666700001");
        long rideId = createAndConfirmRide(customerToken);

        mockMvc.perform(get("/api/rides/" + rideId).header("Authorization", "Bearer " + customerToken))
                .andExpect(jsonPath("$.data.status").value("SEARCHING_DRIVER"))
                .andExpect(jsonPath("$.data.searchingSince").exists());

        // Back-date past any realistic configured timeout, then run the sweep synchronously.
        Ride ride = rideRepository.findById(rideId).orElseThrow();
        ride.setSearchingSince(Instant.now().minusSeconds(3600));
        rideRepository.save(ride);
        rideService.autoExpireStaleSearches();

        mockMvc.perform(get("/api/rides/" + rideId).header("Authorization", "Bearer " + customerToken))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancellationReason").value("NO_DRIVER_FOUND"));
    }

    @Test
    void freshSearchIsLeftAloneBySweep() throws Exception {
        String customerToken = registerCustomer("timeout-fresh@example.com", "9666700002");
        long rideId = createAndConfirmRide(customerToken);

        // searchingSince was just set by confirmFare - nowhere near the timeout yet.
        rideService.autoExpireStaleSearches();

        mockMvc.perform(get("/api/rides/" + rideId).header("Authorization", "Bearer " + customerToken))
                .andExpect(jsonPath("$.data.status").value("SEARCHING_DRIVER"));
    }
}
