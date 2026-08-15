package com.saferide.fare;

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
 * The core "no unauthorized extra fare" guarantee, exercised end to end
 * over real HTTP: a ride's fare is always server-calculated, invisible to
 * drivers until the customer locks it, genuinely un-editable by anyone
 * once locked, and any change to it (only ever via a destination change)
 * requires unlocking and an explicit fresh confirmation - never a silent
 * increase.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FareIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;

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
                {"name":"Fare Customer","email":"%s","phone":"%s","password":"Password123!","role":"CUSTOMER"}
                """.formatted(email, phone);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        return loginAndGetToken(email, "Password123!");
    }

    private String readyDriver(String email, String phone, String license, String vehicleType, String vehicleNumber) throws Exception {
        String payload = """
                {"name":"Fare Driver","email":"%s","phone":"%s","password":"Password123!","role":"DRIVER","licenseNumber":"%s"}
                """.formatted(email, phone, license);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        String token = loginAndGetToken(email, "Password123!");

        String adminEmail = "fare-admin-" + phone + "@example.com";
        userService.createUser("Fare Test Admin", adminEmail, "7" + phone.substring(1),
                passwordEncoder.encode("Password123!"), RoleName.ADMIN);
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

    private long createRide(String customerToken, String vehicleType) throws Exception {
        String payload = """
                {"pickupAddress":"Guwahati Railway Station","destinationAddress":"Guwahati Airport",
                 "pickupLatitude":26.1845,"pickupLongitude":91.7362,
                 "destinationLatitude":26.1061,"destinationLongitude":91.5859,"vehicleType":"%s"}
                """.formatted(vehicleType);
        String body = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asLong();
    }

    @Test
    void previewFareDiffersByVehicleType() throws Exception {
        String customerToken = registerCustomer("fare-preview@example.com", "9555500001");

        String miniBody = mockMvc.perform(post("/api/fare/calculate")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distanceKm\":8.0,\"estimatedDurationMinutes\":20,\"vehicleType\":\"MINI\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String suvBody = mockMvc.perform(post("/api/fare/calculate")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distanceKm\":8.0,\"estimatedDurationMinutes\":20,\"vehicleType\":\"SUV\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        double miniTotal = objectMapper.readTree(miniBody).at("/data/totalFare").asDouble();
        double suvTotal = objectMapper.readTree(suvBody).at("/data/totalFare").asDouble();
        assertThat(miniTotal).isLessThan(suvTotal);
    }

    @Test
    void newRideHasAnUnlockedFareUntilConfirmed() throws Exception {
        String customerToken = registerCustomer("fare-new-ride@example.com", "9555500002");
        long rideId = createRide(customerToken, "SEDAN");

        mockMvc.perform(get("/api/rides/" + rideId + "/fare").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalFare").value(org.hamcrest.Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.data.lockStatus").doesNotExist());
    }

    @Test
    void confirmingLocksTheFareAndConfirmingTwiceFails() throws Exception {
        String customerToken = registerCustomer("fare-confirm@example.com", "9555500003");
        long rideId = createRide(customerToken, "SEDAN");

        String confirmBody = mockMvc.perform(post("/api/rides/" + rideId + "/fare/confirm")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockStatus").value("LOCKED"))
                .andReturn().getResponse().getContentAsString();
        double lockedAmount = objectMapper.readTree(confirmBody).at("/data/lockedAmount").asDouble();
        double totalFare = objectMapper.readTree(confirmBody).at("/data/totalFare").asDouble();
        assertThat(lockedAmount).isEqualTo(totalFare);

        // The ride is now searching - the fare being locked is what made that happen.
        mockMvc.perform(get("/api/rides/" + rideId).header("Authorization", "Bearer " + customerToken))
                .andExpect(jsonPath("$.data.status").value("SEARCHING_DRIVER"));

        mockMvc.perform(post("/api/rides/" + rideId + "/fare/confirm").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FARE_LOCKED"));
    }

    @Test
    void driverCanSeeTheLockedFareButHasNoEndpointToChangeIt() throws Exception {
        String customerToken = registerCustomer("fare-driver-view@example.com", "9555500004");
        String driverToken = readyDriver("fare-driver@example.com", "9555500005", "DL-FARE-01", "SEDAN", "AS-01-FARE-01");

        long rideId = createRide(customerToken, "SEDAN");
        mockMvc.perform(post("/api/rides/" + rideId + "/fare/confirm").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rides/" + rideId + "/accept").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk());

        // The driver sees exactly the same locked amount the customer confirmed.
        mockMvc.perform(get("/api/rides/" + rideId + "/fare").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockStatus").value("LOCKED"));

        // There is no fare-mutating endpoint reachable by a driver at all: the only write path
        // (changing destination) is customer-only, so a driver calling it is simply denied.
        mockMvc.perform(put("/api/rides/" + rideId + "/destination")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationAddress\":\"Somewhere else\",\"destinationLatitude\":26.0,\"destinationLongitude\":91.0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void changingDestinationUnlocksTheFareAndPullsTheRideBackFromSearch() throws Exception {
        String customerToken = registerCustomer("fare-dest-change@example.com", "9555500006");
        String driverToken = readyDriver("fare-dest-driver@example.com", "9555500007", "DL-FARE-02", "SEDAN", "AS-01-FARE-02");

        long rideId = createRide(customerToken, "SEDAN");
        String firstConfirm = mockMvc.perform(post("/api/rides/" + rideId + "/fare/confirm")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        double originalTotal = objectMapper.readTree(firstConfirm).at("/data/totalFare").asDouble();

        // Visible to a matching driver while SEARCHING_DRIVER with a locked fare.
        String openListBefore = mockMvc.perform(get("/api/rides/open").header("Authorization", "Bearer " + driverToken))
                .andReturn().getResponse().getContentAsString();
        assertThat(idsIn(openListBefore)).contains(rideId);

        // A much further destination changes the fare.
        String destPayload = """
                {"destinationAddress":"Far Away Place","destinationLatitude":27.5,"destinationLongitude":93.5}
                """;
        mockMvc.perform(put("/api/rides/" + rideId + "/destination")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(destPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REQUESTED")); // pulled back from search

        mockMvc.perform(get("/api/rides/" + rideId + "/fare").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockStatus").value("UNLOCKED"))
                .andExpect(jsonPath("$.data.unlockedAt").exists());

        // No longer visible to drivers until re-confirmed.
        String openListAfter = mockMvc.perform(get("/api/rides/open").header("Authorization", "Bearer " + driverToken))
                .andReturn().getResponse().getContentAsString();
        assertThat(idsIn(openListAfter)).doesNotContain(rideId);

        // Re-confirming locks the new (different) amount and resumes the search.
        String reconfirmBody = mockMvc.perform(post("/api/rides/" + rideId + "/fare/confirm")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockStatus").value("LOCKED"))
                .andReturn().getResponse().getContentAsString();
        double newTotal = objectMapper.readTree(reconfirmBody).at("/data/totalFare").asDouble();
        assertThat(newTotal).isNotEqualTo(originalTotal);

        mockMvc.perform(get("/api/rides/" + rideId).header("Authorization", "Bearer " + customerToken))
                .andExpect(jsonPath("$.data.status").value("SEARCHING_DRIVER"));
    }

    @Test
    void cannotChangeDestinationOnceADriverHasAccepted() throws Exception {
        String customerToken = registerCustomer("fare-locked-dest@example.com", "9555500008");
        String driverToken = readyDriver("fare-locked-driver@example.com", "9555500009", "DL-FARE-03", "SEDAN", "AS-01-FARE-03");

        long rideId = createRide(customerToken, "SEDAN");
        mockMvc.perform(post("/api/rides/" + rideId + "/fare/confirm").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rides/" + rideId + "/accept").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk());

        String destPayload = """
                {"destinationAddress":"Somewhere else","destinationLatitude":26.0,"destinationLongitude":91.0}
                """;
        mockMvc.perform(put("/api/rides/" + rideId + "/destination")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(destPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RIDE_STATE"));
    }

    private java.util.List<Long> idsIn(String pageJson) throws Exception {
        JsonNode content = objectMapper.readTree(pageJson).at("/data/content");
        java.util.List<Long> ids = new java.util.ArrayList<>();
        content.forEach(node -> ids.add(node.at("/id").asLong()));
        return ids;
    }
}
