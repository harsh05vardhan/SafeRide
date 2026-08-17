package com.saferide.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferide.matching.DriverGeoIndex;
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
 * End-to-end coverage of the Phase 3 onboarding pipeline: a driver signs
 * up, can't go online or accept anything until an admin reviews them, and
 * an admin can verify/reject vehicles and documents independently of the
 * driver's own verification. Admin accounts are provisioned directly
 * through {@link UserService} (bypassing the public register endpoint,
 * which correctly refuses {@code role: ADMIN}) - exactly how a real admin
 * account would be provisioned out of band.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DriverVerificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DriverGeoIndex geoIndex;

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
        userService.createUser("Test Admin", email, phone, passwordEncoder.encode("Password123!"), RoleName.ADMIN);
        return loginAndGetToken(email, "Password123!");
    }

    private String registerDriverAndLogin(String email, String phone, String license) throws Exception {
        String payload = """
                {"name":"Test Driver","email":"%s","phone":"%s","password":"Password123!","role":"DRIVER","licenseNumber":"%s"}
                """.formatted(email, phone, license);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
        return loginAndGetToken(email, "Password123!");
    }

    @Test
    void fullVerificationPipeline() throws Exception {
        String adminToken = provisionAdminAndLogin("verify-admin@example.com", "9333300001");
        String driverToken = registerDriverAndLogin("verify-driver@example.com", "9333300002", "DL-VERIFY-0001");

        // 1. Freshly registered driver is PENDING and cannot go online.
        mockMvc.perform(put("/api/drivers/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onlineStatus\":\"ONLINE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_VERIFIED"));

        // 2. The driver shows up in the admin's pending queue.
        String listBody = mockMvc.perform(get("/api/admin/drivers")
                        .param("status", "PENDING")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode list = objectMapper.readTree(listBody).at("/data/content");
        long driverId = -1;
        for (JsonNode node : list) {
            if (node.at("/user/email").asText().equals("verify-driver@example.com")) {
                driverId = node.at("/id").asLong();
            }
        }
        org.assertj.core.api.Assertions.assertThat(driverId).isPositive();

        // 3. A non-admin cannot approve drivers.
        mockMvc.perform(put("/api/admin/drivers/" + driverId + "/approve")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 4. Admin approves the driver.
        mockMvc.perform(put("/api/admin/drivers/" + driverId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));

        // 5. Now the driver can go online.
        mockMvc.perform(put("/api/drivers/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onlineStatus\":\"ONLINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onlineStatus").value("ONLINE"));

        // 6. Driver registers a vehicle - starts PENDING and active.
        String vehiclePayload = """
                {"vehicleType":"SEDAN","vehicleModel":"Honda City","vehicleNumber":"AS-01-VF-0001","vehicleColor":"Black","seatCapacity":4}
                """;
        String vehicleBody = mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehiclePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn().getResponse().getContentAsString();
        long vehicleId = objectMapper.readTree(vehicleBody).at("/data/id").asLong();

        // 7. Admin verifies the vehicle independently of the driver's own status.
        mockMvc.perform(put("/api/admin/drivers/vehicles/" + vehicleId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));

        // 8. Driver uploads a document; admin rejects it with a reason.
        String docPayload = """
                {"documentType":"DRIVING_LICENSE","documentUrl":"https://example.com/docs/license.jpg"}
                """;
        String docBody = mockMvc.perform(post("/api/drivers/documents")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(docPayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long documentId = objectMapper.readTree(docBody).at("/data/id").asLong();

        mockMvc.perform(put("/api/admin/drivers/documents/" + documentId + "/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Image is blurry\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("REJECTED"));

        // 9. Admin blocks the driver's account entirely; login now fails.
        mockMvc.perform(put("/api/admin/drivers/" + driverId + "/block")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Suspected fraud\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"verify-driver@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));

        // 10. Admin unblocks - login works again.
        mockMvc.perform(put("/api/admin/drivers/" + driverId + "/unblock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"verify-driver@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk());
    }

    /**
     * Blocking a driver sets their DB {@code onlineStatus} to OFFLINE, but
     * matching never reads that column directly - it asks
     * {@link DriverGeoIndex} for candidates. Before this fix,
     * {@code AdminDriverService#blockDriver} never removed the driver from
     * the index, so an online driver blocked mid-shift would keep being a
     * live matching candidate regardless of their account status.
     */
    @Test
    void blockingAnOnlineDriverRemovesThemFromTheMatchingGeoIndex() throws Exception {
        String adminToken = provisionAdminAndLogin("geo-admin@example.com", "9333300099");
        String driverToken = registerDriverAndLogin("geo-driver@example.com", "9333300098", "DL-GEO-0001");

        long driverId = approveAndVerifyMinimalDriver(adminToken, driverToken, "geo-driver@example.com", "AS-01-GEO-0001");

        double lat = 25.5788;
        double lng = 91.8933;
        mockMvc.perform(put("/api/drivers/location")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":%s,\"longitude\":%s}".formatted(lat, lng)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/drivers/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onlineStatus\":\"ONLINE\"}"))
                .andExpect(status().isOk());

        assertThat(geoIndex.findWithinRadius(lat, lng, 5.0)).contains(driverId);

        mockMvc.perform(put("/api/admin/drivers/" + driverId + "/block")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Suspected fraud\"}"))
                .andExpect(status().isOk());

        assertThat(geoIndex.findWithinRadius(lat, lng, 5.0)).doesNotContain(driverId);
    }

    /** Registers, admin-approves the driver, and admin-verifies one vehicle for them - the minimum to become a real matching candidate once online. */
    private long approveAndVerifyMinimalDriver(String adminToken, String driverToken, String driverEmail, String vehicleNumber)
            throws Exception {
        String listBody = mockMvc.perform(get("/api/admin/drivers")
                        .param("status", "PENDING")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        JsonNode list = objectMapper.readTree(listBody).at("/data/content");
        long driverId = -1;
        for (JsonNode node : list) {
            if (node.at("/user/email").asText().equals(driverEmail)) {
                driverId = node.at("/id").asLong();
            }
        }
        assertThat(driverId).isPositive();

        mockMvc.perform(put("/api/admin/drivers/" + driverId + "/approve").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String vehiclePayload = """
                {"vehicleType":"SEDAN","vehicleModel":"Honda City","vehicleNumber":"%s","vehicleColor":"Black","seatCapacity":4}
                """.formatted(vehicleNumber);
        String vehicleBody = mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehiclePayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long vehicleId = objectMapper.readTree(vehicleBody).at("/data/id").asLong();

        mockMvc.perform(put("/api/admin/drivers/vehicles/" + vehicleId + "/approve").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        return driverId;
    }

    @Test
    void registeringADuplicateVehicleNumberIsRejected() throws Exception {
        String driverAToken = registerDriverAndLogin("veh-owner-a@example.com", "9333300003", "DL-VEH-A");
        String driverBToken = registerDriverAndLogin("veh-owner-b@example.com", "9333300004", "DL-VEH-B");

        String payload = """
                {"vehicleType":"MINI","vehicleModel":"Alto","vehicleNumber":"AS-01-DUP-0001","vehicleColor":"Red","seatCapacity":4}
                """;
        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + driverAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + driverBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_ALREADY_REGISTERED"));
    }

    @Test
    void driverCannotActivateAnotherDriversVehicle() throws Exception {
        String driverAToken = registerDriverAndLogin("veh-a2@example.com", "9333300005", "DL-VEH-A2");
        String driverBToken = registerDriverAndLogin("veh-b2@example.com", "9333300006", "DL-VEH-B2");

        String payload = """
                {"vehicleType":"MINI","vehicleModel":"Alto","vehicleNumber":"AS-01-OWN-0001","vehicleColor":"Blue","seatCapacity":4}
                """;
        String body = mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + driverAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long vehicleId = objectMapper.readTree(body).at("/data/id").asLong();

        mockMvc.perform(put("/api/vehicles/" + vehicleId + "/activate")
                        .header("Authorization", "Bearer " + driverBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
