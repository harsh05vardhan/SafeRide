package com.saferide.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferide.testsupport.AbstractIntegrationTest;
import com.saferide.user.RoleName;
import com.saferide.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/admin/analytics/summary} against the same shared
 * MySQL/Redis Testcontainers instance every other integration test class
 * in the suite runs against (see {@link AbstractIntegrationTest}) - so
 * this can only assert the real counts moved by <em>at least</em> what this
 * test itself contributed, never an exact total. Exact arithmetic is
 * {@link AdminAnalyticsServiceTest}'s job, with fully controlled mocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAnalyticsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;

    // Bengaluru - a fresh coordinate pair, unclaimed by any other test class.
    private static final double PICKUP_LAT = 12.9716;
    private static final double PICKUP_LNG = 77.5946;

    private String loginAndGetToken(String identifier, String password) throws Exception {
        String payload = "{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password);
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/accessToken").asText();
    }

    private String provisionAdminAndLogin(String email, String phone) throws Exception {
        userService.createUser("Analytics Test Admin", email, phone, passwordEncoder.encode("Password123!"), RoleName.ADMIN);
        return loginAndGetToken(email, "Password123!");
    }

    private String registerCustomer(String email, String phone) throws Exception {
        String payload = """
                {"name":"Analytics Customer","email":"%s","phone":"%s","password":"Password123!","role":"CUSTOMER"}
                """.formatted(email, phone);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        return loginAndGetToken(email, "Password123!");
    }

    private String readyDriver(String email, String phone, String license, String vehicleNumber, String adminToken) throws Exception {
        String payload = """
                {"name":"Analytics Driver","email":"%s","phone":"%s","password":"Password123!","role":"DRIVER","licenseNumber":"%s"}
                """.formatted(email, phone, license);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        String token = loginAndGetToken(email, "Password123!");

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

        mockMvc.perform(put("/api/drivers/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onlineStatus\":\"ONLINE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/drivers/location")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":%s,\"longitude\":%s}".formatted(PICKUP_LAT, PICKUP_LNG)))
                .andExpect(status().isOk());

        return token;
    }

    @Test
    void summaryReflectsARealCompletedAndPaidRide() throws Exception {
        String adminToken = provisionAdminAndLogin("analytics-admin@example.com", "9611100001");

        JsonNode before = objectMapper.readTree(
                mockMvc.perform(get("/api/admin/analytics/summary").header("Authorization", "Bearer " + adminToken))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).at("/data");

        String customerToken = registerCustomer("analytics-customer@example.com", "9611100002");
        String driverToken = readyDriver("analytics-driver@example.com", "9611100003", "DL-ANALYTICS-1", "ANALYTICS-VEH-01", adminToken);

        String createBody = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupAddress":"MG Road","destinationAddress":"Whitefield",
                                 "pickupLatitude":%s,"pickupLongitude":%s,
                                 "destinationLatitude":12.9698,"destinationLongitude":77.7500,"vehicleType":"SEDAN"}
                                """.formatted(PICKUP_LAT, PICKUP_LNG)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long rideId = objectMapper.readTree(createBody).at("/data/id").asLong();

        mockMvc.perform(post("/api/rides/" + rideId + "/fare/confirm").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rides/" + rideId + "/accept").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rides/" + rideId + "/arrive").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk());
        String rideView = mockMvc.perform(get("/api/rides/" + rideId).header("Authorization", "Bearer " + customerToken))
                .andReturn().getResponse().getContentAsString();
        String otp = objectMapper.readTree(rideView).at("/data/otp").asText();
        mockMvc.perform(post("/api/rides/" + rideId + "/start")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"" + otp + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rides/" + rideId + "/complete").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rides/" + rideId + "/pay").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());
        // pay's own response is the Ride, not the Payment - fetch the real PaymentResponse for the exact amount charged.
        String paymentBody = mockMvc.perform(get("/api/rides/" + rideId + "/payment").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        double amountPaid = objectMapper.readTree(paymentBody).at("/data/amount").asDouble();

        JsonNode after = objectMapper.readTree(
                mockMvc.perform(get("/api/admin/analytics/summary").header("Authorization", "Bearer " + adminToken))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).at("/data");

        assertThat(after.get("totalRides").asLong()).isEqualTo(before.get("totalRides").asLong() + 1);
        assertThat(after.get("completedRides").asLong()).isEqualTo(before.get("completedRides").asLong() + 1);
        assertThat(after.get("totalUsers").asLong()).isEqualTo(before.get("totalUsers").asLong() + 2); // customer + driver
        assertThat(after.get("totalDrivers").asLong()).isEqualTo(before.get("totalDrivers").asLong() + 1);
        assertThat(after.get("verifiedDrivers").asLong()).isEqualTo(before.get("verifiedDrivers").asLong() + 1);
        assertThat(after.get("onlineDriversNow").asLong()).isEqualTo(before.get("onlineDriversNow").asLong() + 1);
        assertThat(after.get("totalRevenue").asDouble()).isEqualTo(before.get("totalRevenue").asDouble() + amountPaid, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void onlyAnAdminCanReadTheSummary() throws Exception {
        String customerToken = registerCustomer("analytics-stranger@example.com", "9611100004");
        mockMvc.perform(get("/api/admin/analytics/summary").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    /** Dashboard's "recent rides" (newest first) and "top rated drivers" (verified only, ranked by real rating). */
    @Test
    void recentRidesAndTopDriversReflectRealData() throws Exception {
        String adminToken = provisionAdminAndLogin("dashboard-admin@example.com", "9611100005");
        String customerToken = registerCustomer("dashboard-customer@example.com", "9611100006");
        String driverToken = readyDriver("dashboard-driver@example.com", "9611100007", "DL-DASHBOARD-1", "DASHBOARD-VEH-01", adminToken);

        String createBody = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupAddress":"MG Road","destinationAddress":"Whitefield",
                                 "pickupLatitude":%s,"pickupLongitude":%s,
                                 "destinationLatitude":12.9698,"destinationLongitude":77.7500,"vehicleType":"SEDAN"}
                                """.formatted(PICKUP_LAT, PICKUP_LNG)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long rideId = objectMapper.readTree(createBody).at("/data/id").asLong();
        mockMvc.perform(post("/api/rides/" + rideId + "/fare/confirm").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());

        String recentRides = mockMvc.perform(get("/api/admin/analytics/recent-rides").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode rides = objectMapper.readTree(recentRides).at("/data");
        assertThat(rides.get(0).at("/id").asLong()).isEqualTo(rideId); // newest first
        assertThat(rides.get(0).at("/customer/email").asText()).isEqualTo("dashboard-customer@example.com");
        assertThat(rides.get(0).at("/driver/name").asText()).isEqualTo("Analytics Driver");

        String topDrivers = mockMvc.perform(get("/api/admin/analytics/top-drivers").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(topDrivers).at("/data").findValuesAsText("name")).contains("Analytics Driver");
    }
}
