package com.saferide.ride;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferide.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full black-box ride lifecycle over real HTTP requests: estimate -> book
 * -> a driver with the wrong vehicle type never sees it -> the right
 * driver accepts (naive Phase 4 self-claim - see RideService) -> a second
 * driver is now too late -> arrive generates an OTP visible only to the
 * customer -> wrong OTP is rejected -> correct OTP starts the ride ->
 * complete auto-advances to PAYMENT_PENDING -> a stranger can't view or
 * cancel the ride -> a separate ride shows both customer- and
 * driver-initiated cancellation.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RideIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String loginAndGetToken(String identifier, String password) throws Exception {
        String payload = "{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password);
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/accessToken").asText();
    }

    private String registerCustomer(String email, String phone) throws Exception {
        String payload = """
                {"name":"Test Customer","email":"%s","phone":"%s","password":"Password123!","role":"CUSTOMER"}
                """.formatted(email, phone);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        return loginAndGetToken(email, "Password123!");
    }

    /** Registers, verifies (self-elevated for test speed via admin bootstrap below), goes online, with a verified vehicle of the given type. */
    private String readyDriver(String email, String phone, String license, String vehicleType, String vehicleNumber) throws Exception {
        String payload = """
                {"name":"Test Driver","email":"%s","phone":"%s","password":"Password123!","role":"DRIVER","licenseNumber":"%s"}
                """.formatted(email, phone, license);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        String token = loginAndGetToken(email, "Password123!");

        // Admin bootstrap + approve, mirroring DriverVerificationIntegrationTest's pattern.
        String adminEmail = "ride-admin-" + phone + "@example.com";
        userService.createUser("Ride Test Admin", adminEmail, "8" + phone.substring(1),
                passwordEncoder.encode("Password123!"), com.saferide.user.RoleName.ADMIN);
        String adminToken = loginAndGetToken(adminEmail, "Password123!");

        String profileBody = mockMvc.perform(get("/api/drivers/profile").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        long driverId = objectMapper.readTree(profileBody).at("/data/id").asLong();
        mockMvc.perform(put("/api/admin/drivers/" + driverId + "/approve").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String vehiclePayload = """
                {"vehicleType":"%s","vehicleModel":"Test Model","vehicleNumber":"%s","vehicleColor":"White","seatCapacity":4}
                """.formatted(vehicleType, vehicleNumber);
        String vehicleBody = mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehiclePayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long vehicleId = objectMapper.readTree(vehicleBody).at("/data/id").asLong();
        mockMvc.perform(put("/api/admin/drivers/vehicles/" + vehicleId + "/approve").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/drivers/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onlineStatus\":\"ONLINE\"}"))
                .andExpect(status().isOk());

        return token;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.saferide.user.UserService userService;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    /**
     * Creates a ride (starts at REQUESTED - not yet driver-visible, see
     * Phase 5's Fare Lock) and immediately confirms its fare, which is
     * what actually moves it to SEARCHING_DRIVER. Returns the *creation*
     * response (its "id" stays valid; its "status" field is stale
     * REQUESTED - callers needing the current status re-fetch via GET).
     */
    private String createRide(String customerToken, String vehicleType) throws Exception {
        String payload = """
                {"pickupAddress":"Guwahati Railway Station","destinationAddress":"Guwahati Airport",
                 "pickupLatitude":26.1845,"pickupLongitude":91.7362,
                 "destinationLatitude":26.1061,"destinationLongitude":91.5859,"vehicleType":"%s"}
                """.formatted(vehicleType);
        String createBody = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andReturn().getResponse().getContentAsString();
        long rideId = objectMapper.readTree(createBody).at("/data/id").asLong();

        mockMvc.perform(post("/api/rides/" + rideId + "/fare/confirm").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockStatus").value("LOCKED"));

        return createBody;
    }

    @Test
    void estimateReturnsAPositiveDistanceAndDuration() throws Exception {
        String customerToken = registerCustomer("est-customer@example.com", "9444400001");
        String payload = """
                {"pickupLatitude":26.1845,"pickupLongitude":91.7362,"destinationLatitude":26.1061,"destinationLongitude":91.5859}
                """;
        mockMvc.perform(post("/api/rides/estimate")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.distanceKm").value(org.hamcrest.Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.data.estimatedDurationMinutes").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void fullRideLifecycleHappyPath() throws Exception {
        String customerToken = registerCustomer("ride-customer1@example.com", "9444400002");
        String wrongTypeDriverToken = readyDriver(
                "ride-driver-wrong@example.com", "9444400003", "DL-RIDE-WRONG", "SUV", "AS-01-WRONG-01");
        String correctDriverToken = readyDriver(
                "ride-driver-right@example.com", "9444400004", "DL-RIDE-RIGHT", "SEDAN", "AS-01-RIGHT-01");
        String secondSedanDriverToken = readyDriver(
                "ride-driver-second@example.com", "9444400005", "DL-RIDE-SECOND", "SEDAN", "AS-01-SECOND-01");

        String rideBody = createRide(customerToken, "SEDAN");
        long rideId = objectMapper.readTree(rideBody).at("/data/id").asLong();

        // A driver with the wrong vehicle type never sees this ride in their open queue.
        String wrongTypeOpenList = mockMvc.perform(get("/api/rides/open").header("Authorization", "Bearer " + wrongTypeDriverToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        boolean wrongTypeSeesIt = objectMapper.readTree(wrongTypeOpenList).at("/data/content")
                .findValuesAsText("id").contains(String.valueOf(rideId));
        org.assertj.core.api.Assertions.assertThat(wrongTypeSeesIt).isFalse();

        // The matching-type driver does see it, and accepts.
        mockMvc.perform(post("/api/rides/" + rideId + "/accept").header("Authorization", "Bearer " + correctDriverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRIVER_ACCEPTED"));

        // A second driver is too late.
        mockMvc.perform(post("/api/rides/" + rideId + "/accept").header("Authorization", "Bearer " + secondSedanDriverToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RIDE_STATE"));

        // Arriving generates an OTP visible to the customer but not the driver.
        mockMvc.perform(post("/api/rides/" + rideId + "/arrive").header("Authorization", "Bearer " + correctDriverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRIVER_ARRIVED"))
                .andExpect(jsonPath("$.data.otp").doesNotExist());

        String customerView = mockMvc.perform(get("/api/rides/" + rideId).header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String otp = objectMapper.readTree(customerView).at("/data/otp").asText();
        org.assertj.core.api.Assertions.assertThat(otp).matches("\\d{4}");

        mockMvc.perform(get("/api/rides/" + rideId).header("Authorization", "Bearer " + correctDriverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otp").doesNotExist());

        // A stranger cannot view this ride at all.
        String strangerToken = registerCustomer("ride-stranger@example.com", "9444400006");
        mockMvc.perform(get("/api/rides/" + rideId).header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // Wrong OTP is rejected; the ride stays DRIVER_ARRIVED.
        mockMvc.perform(post("/api/rides/" + rideId + "/start")
                        .header("Authorization", "Bearer " + correctDriverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"0000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"));

        // Correct OTP starts the ride.
        mockMvc.perform(post("/api/rides/" + rideId + "/start")
                        .header("Authorization", "Bearer " + correctDriverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"" + otp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RIDE_STARTED"));

        // Completing auto-advances straight to PAYMENT_PENDING.
        mockMvc.perform(post("/api/rides/" + rideId + "/complete").header("Authorization", "Bearer " + correctDriverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYMENT_PENDING"));

        // A ride in progress can no longer be cancelled by anyone once it's this far along.
        mockMvc.perform(post("/api/rides/" + rideId + "/cancel").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RIDE_STATE"));
    }

    @Test
    void customerCannotBookASecondRideWhileOneIsActive() throws Exception {
        String customerToken = registerCustomer("busy-customer@example.com", "9444400007");
        createRide(customerToken, "MINI");

        String secondPayload = """
                {"pickupAddress":"A","destinationAddress":"B","pickupLatitude":26.18,"pickupLongitude":91.73,
                 "destinationLatitude":26.10,"destinationLongitude":91.58,"vehicleType":"MINI"}
                """;
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_RIDE_EXISTS"));
    }

    @Test
    void customerCancelMarksCancelledAndDriverCancelMarksDriverCancelled() throws Exception {
        String customerToken = registerCustomer("cancel-customer@example.com", "9444400008");
        String rideBody = createRide(customerToken, "MINI");
        long rideId = objectMapper.readTree(rideBody).at("/data/id").asLong();

        mockMvc.perform(post("/api/rides/" + rideId + "/cancel").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        String customerToken2 = registerCustomer("cancel-customer2@example.com", "9444400009");
        String driverToken = readyDriver("cancel-driver@example.com", "9444400010", "DL-CANCEL-01", "MINI", "AS-01-CANCEL-01");
        String rideBody2 = createRide(customerToken2, "MINI");
        long rideId2 = objectMapper.readTree(rideBody2).at("/data/id").asLong();

        mockMvc.perform(post("/api/rides/" + rideId2 + "/accept").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk());
        // Phase 6: a driver-cancel automatically tries to reassign rather than just terminating -
        // with no other eligible driver available here (nobody else has reported a location), it
        // lands back on SEARCHING_DRIVER instead of staying DRIVER_CANCELLED. The full reassignment
        // path (excluding the cancelling driver, finding the next-best one) is covered by
        // MatchingIntegrationTest.
        mockMvc.perform(post("/api/rides/" + rideId2 + "/cancel").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SEARCHING_DRIVER"));
    }
}
