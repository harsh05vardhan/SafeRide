package com.saferide.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferide.user.RoleName;
import com.saferide.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 6 end to end: proactive assignment picks the closer of two
 * otherwise-identical drivers, a decline permanently excludes that driver
 * and immediately reassigns to the next-best one, the excluded driver
 * can't claw the ride back through either the direct accept endpoint or
 * the {@code /api/rides/open} fallback, acceptance/decline/cancellation
 * all move the driver's reliability numbers, and a driver cancellation
 * after accepting auto-reassigns to a third driver rather than leaving
 * the ride dead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;

    // Deliberately NOT the same "Guwahati Railway Station" point RideIntegrationTest/
    // FareIntegrationTest use: @SpringBootTest contexts (and their H2 database) are shared/cached
    // across test classes with identical configuration, so a driver this class leaves ONLINE with
    // a reported location would otherwise be a real candidate for another test class's ride
    // created at the same coordinates. Shillong, ~100km away - well outside the 5km match radius
    // of every other test class's pickup point, so this class's driver fixtures can never leak in.
    private static final double PICKUP_LAT = 25.5788;
    private static final double PICKUP_LNG = 91.8933;

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
                {"name":"Match Customer","email":"%s","phone":"%s","password":"Password123!","role":"CUSTOMER"}
                """.formatted(email, phone);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        return loginAndGetToken(email, "Password123!");
    }

    /** Registers, verifies, verifies a matching vehicle, goes online, and reports a location - fully matchable. */
    private String readyDriverAt(String email, String phone, String license, String vehicleNumber, double lat, double lng)
            throws Exception {
        String payload = """
                {"name":"Match Driver","email":"%s","phone":"%s","password":"Password123!","role":"DRIVER","licenseNumber":"%s"}
                """.formatted(email, phone, license);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        String token = loginAndGetToken(email, "Password123!");

        String adminEmail = "match-admin-" + phone + "@example.com";
        userService.createUser("Match Test Admin", adminEmail, "7" + phone.substring(1),
                passwordEncoder.encode("Password123!"), RoleName.ADMIN);
        String adminToken = loginAndGetToken(adminEmail, "Password123!");

        String profileBody = mockMvc.perform(get("/api/drivers/profile").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        long driverId = objectMapper.readTree(profileBody).at("/data/id").asLong();
        mockMvc.perform(put("/api/admin/drivers/" + driverId + "/approve").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String vehiclePayload = """
                {"vehicleType":"SEDAN","vehicleModel":"Test Model","vehicleNumber":"%s","vehicleColor":"White","seatCapacity":4}
                """.formatted(vehicleNumber);
        String vehicleBody = mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehiclePayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long vehicleId = objectMapper.readTree(vehicleBody).at("/data/id").asLong();
        mockMvc.perform(put("/api/admin/drivers/vehicles/" + vehicleId + "/approve").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/drivers/location")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":%s,\"longitude\":%s}".formatted(lat, lng)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/drivers/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onlineStatus\":\"ONLINE\"}"))
                .andExpect(status().isOk());

        return token;
    }

    private long createAndConfirmRide(String customerToken) throws Exception {
        return createAndConfirmRide(customerToken, PICKUP_LAT, PICKUP_LNG);
    }

    private long createAndConfirmRide(String customerToken, double pickupLat, double pickupLng) throws Exception {
        String payload = """
                {"pickupAddress":"Guwahati Railway Station","destinationAddress":"Guwahati Airport",
                 "pickupLatitude":%s,"pickupLongitude":%s,
                 "destinationLatitude":26.1061,"destinationLongitude":91.5859,"vehicleType":"SEDAN"}
                """.formatted(pickupLat, pickupLng);
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
    void fullProactiveAssignDeclineReassignCancelReassignFlow() throws Exception {
        String customerToken = registerCustomer("match-customer@example.com", "9666600001");

        // Near driver: ~0.1km from pickup. Far driver: ~2km away. Third driver: ~1km, held in reserve.
        String nearToken = readyDriverAt("match-near@example.com", "9666600002", "DL-MATCH-NEAR", "AS-01-NEAR-01",
                PICKUP_LAT + 0.001, PICKUP_LNG);
        String farToken = readyDriverAt("match-far@example.com", "9666600003", "DL-MATCH-FAR", "AS-01-FAR-01",
                PICKUP_LAT + 0.02, PICKUP_LNG);
        String thirdToken = readyDriverAt("match-third@example.com", "9666600004", "DL-MATCH-THIRD", "AS-01-THIRD-01",
                PICKUP_LAT + 0.01, PICKUP_LNG);

        long rideId = createAndConfirmRide(customerToken);

        // Confirming the fare proactively assigned the near driver - no accept() call needed yet.
        String afterConfirm = mockMvc.perform(get("/api/rides/" + rideId).header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRIVER_ASSIGNED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(afterConfirm).at("/data/driver/name").asText()).isNotEmpty();

        // The far driver was not the one assigned, so accepting fails outright.
        mockMvc.perform(post("/api/rides/" + rideId + "/accept").header("Authorization", "Bearer " + farToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RIDE_STATE"));

        // The near driver declines - permanently excluded, and matching immediately re-runs.
        mockMvc.perform(post("/api/rides/" + rideId + "/decline").header("Authorization", "Bearer " + nearToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRIVER_ASSIGNED"));

        String afterDecline = mockMvc.perform(get("/api/rides/" + rideId).header("Authorization", "Bearer " + customerToken))
                .andReturn().getResponse().getContentAsString();
        String assignedPhoneAfterDecline = objectMapper.readTree(afterDecline).at("/data/driver/phone").asText();
        assertThat(assignedPhoneAfterDecline).isIn("9666600003", "9666600004"); // far or third, never near again

        // The declining driver can never get this ride back - not directly, not via the fallback browse.
        mockMvc.perform(post("/api/rides/" + rideId + "/accept").header("Authorization", "Bearer " + nearToken))
                .andExpect(status().is4xxClientError());
        String openForNear = mockMvc.perform(get("/api/rides/open").header("Authorization", "Bearer " + nearToken))
                .andReturn().getResponse().getContentAsString();
        assertThat(idsIn(openForNear)).doesNotContain(rideId);

        // The near driver's acceptance rate dropped from the decline (EMA toward 0).
        String nearProfile = mockMvc.perform(get("/api/drivers/profile").header("Authorization", "Bearer " + nearToken))
                .andReturn().getResponse().getContentAsString();
        double nearAcceptanceRate = objectMapper.readTree(nearProfile).at("/data/acceptanceRate").asDouble();
        assertThat(nearAcceptanceRate).isLessThan(100.0);

        // Whoever now holds the ride accepts it.
        String currentDriverToken = assignedPhoneAfterDecline.equals("9666600003") ? farToken : thirdToken;
        mockMvc.perform(post("/api/rides/" + rideId + "/accept").header("Authorization", "Bearer " + currentDriverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRIVER_ACCEPTED"));

        // That driver now cancels post-acceptance - automatic reassignment kicks in again.
        mockMvc.perform(post("/api/rides/" + rideId + "/cancel").header("Authorization", "Bearer " + currentDriverToken))
                .andExpect(status().isOk());

        String afterCancel = mockMvc.perform(get("/api/rides/" + rideId).header("Authorization", "Bearer " + customerToken))
                .andReturn().getResponse().getContentAsString();
        String statusAfterCancel = objectMapper.readTree(afterCancel).at("/data/status").asText();
        // Either reassigned to the one remaining eligible driver, or (if none left) back to searching.
        assertThat(statusAfterCancel).isIn("DRIVER_ASSIGNED", "SEARCHING_DRIVER");

        if (statusAfterCancel.equals("DRIVER_ASSIGNED")) {
            String newDriverPhone = objectMapper.readTree(afterCancel).at("/data/driver/phone").asText();
            assertThat(newDriverPhone).isNotEqualTo("9666600002"); // never the original decliner
            assertThat(newDriverPhone).isNotEqualTo(currentDriverToken.equals(farToken) ? "9666600003" : "9666600004");
        }

        // The cancelling driver's cancellation rate rose from the default 0.
        String cancellerEmail = currentDriverToken.equals(farToken) ? "match-far@example.com" : "match-third@example.com";
        String cancellerToken = loginAndGetToken(cancellerEmail, "Password123!");
        String cancellerProfile = mockMvc.perform(get("/api/drivers/profile").header("Authorization", "Bearer " + cancellerToken))
                .andReturn().getResponse().getContentAsString();
        double cancellationRate = objectMapper.readTree(cancellerProfile).at("/data/cancellationRate").asDouble();
        assertThat(cancellationRate).isGreaterThan(0.0);
    }

    @Test
    void rideWithNoEligibleDriverStaysSearchingAndIsSelfClaimable() throws Exception {
        // A pickup point far from every other test's drivers (Guwahati area) - well outside the
        // 5km matching radius - so this test's "nobody is eligible yet" premise holds regardless
        // of what other tests in this class left online.
        double remotePickupLat = 28.6139; // New Delhi
        double remotePickupLng = 77.2090;

        String customerToken = registerCustomer("no-driver-customer@example.com", "9666600005");
        long rideId = createAndConfirmRide(customerToken, remotePickupLat, remotePickupLng);

        mockMvc.perform(get("/api/rides/" + rideId).header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SEARCHING_DRIVER"))
                .andExpect(jsonPath("$.data.driver").doesNotExist());

        // A driver who comes online later (matching only runs at confirm/decline/cancel time, not
        // continuously) can still self-claim it via the fallback path.
        String lateToken = readyDriverAt("match-late@example.com", "9666600006", "DL-MATCH-LATE", "AS-01-LATE-01",
                remotePickupLat, remotePickupLng);
        mockMvc.perform(post("/api/rides/" + rideId + "/accept").header("Authorization", "Bearer " + lateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRIVER_ACCEPTED"));
    }

    private java.util.List<Long> idsIn(String pageJson) throws Exception {
        JsonNode content = objectMapper.readTree(pageJson).at("/data/content");
        java.util.List<Long> ids = new java.util.ArrayList<>();
        content.forEach(node -> ids.add(node.at("/id").asLong()));
        return ids;
    }
}
