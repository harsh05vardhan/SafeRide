package com.saferide.notification;

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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A full ride lifecycle driven entirely through real HTTP requests, with
 * every {@code com.saferide.event.DomainEvent} it fires along the way
 * checked against what actually landed in the recipient's in-app
 * notification inbox - none of this needs Kafka (see
 * {@code KafkaEventForwarderTest} for the one thing that does).
 * {@code NotificationEventListener} is {@code @TransactionalEventListener(AFTER_COMMIT)},
 * which fires synchronously as each request's own transaction commits -
 * well before MockMvc hands the response back - so no polling/waiting is
 * needed here, unlike the WebSocket subscription races in
 * {@code RideWebSocketIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;

    // Chennai - far from every other test class's fixtures.
    private static final double PICKUP_LAT = 13.0827;
    private static final double PICKUP_LNG = 80.2707;

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
                {"name":"Notif Customer","email":"%s","phone":"%s","password":"Password123!","role":"CUSTOMER"}
                """.formatted(email, phone);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        return loginAndGetToken(email, "Password123!");
    }

    private String readyDriver(String email, String phone, String license, String vehicleNumber) throws Exception {
        String payload = """
                {"name":"Notif Driver","email":"%s","phone":"%s","password":"Password123!","role":"DRIVER","licenseNumber":"%s"}
                """.formatted(email, phone, license);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        String token = loginAndGetToken(email, "Password123!");

        String adminEmail = "notif-admin-" + phone + "@example.com";
        userService.createUser("Notif Test Admin", adminEmail, "5" + phone.substring(1),
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

        mockMvc.perform(put("/api/drivers/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onlineStatus\":\"ONLINE\"}"))
                .andExpect(status().isOk());

        // Right on top of PICKUP_LAT/PICKUP_LNG - a real candidate for matching.
        mockMvc.perform(put("/api/drivers/location")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":%s,\"longitude\":%s}".formatted(PICKUP_LAT, PICKUP_LNG)))
                .andExpect(status().isOk());

        return token;
    }

    private List<String> notificationTypesFor(String token) throws Exception {
        String body = mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(body).at("/data/content");
        return content.findValuesAsText("type");
    }

    @Test
    void ridingThroughFullLifecycleNotifiesBothSidesAtEachStep() throws Exception {
        String customerToken = registerCustomer("notif-customer1@example.com", "9622200001");
        String driverToken = readyDriver("notif-driver1@example.com", "9622200002", "DL-NOTIF-1", "NOTIF-VEH-01");

        // 1. Booking: RideRequestedEvent (customer) + DriverAssignedEvent (both sides, from proactive matching).
        String createBody = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupAddress":"Chennai Central","destinationAddress":"Chennai Airport",
                                 "pickupLatitude":%s,"pickupLongitude":%s,
                                 "destinationLatitude":12.9941,"destinationLongitude":80.1709,"vehicleType":"SEDAN"}
                                """.formatted(PICKUP_LAT, PICKUP_LNG)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long rideId = objectMapper.readTree(createBody).at("/data/id").asLong();

        mockMvc.perform(post("/api/rides/" + rideId + "/fare/confirm").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());

        assertThat(notificationTypesFor(customerToken)).contains("RIDE_REQUESTED", "DRIVER_ASSIGNED");
        assertThat(notificationTypesFor(driverToken)).contains("DRIVER_ASSIGNED");

        // unread-count and mark-as-read.
        String unreadBody = mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long unreadBefore = objectMapper.readTree(unreadBody).at("/data").asLong();
        assertThat(unreadBefore).isGreaterThanOrEqualTo(2);

        String listBody = mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + customerToken))
                .andReturn().getResponse().getContentAsString();
        long firstNotificationId = objectMapper.readTree(listBody).at("/data/content/0/id").asLong();

        mockMvc.perform(put("/api/notifications/" + firstNotificationId + "/read").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        // A stranger can't mark someone else's notification as read.
        String strangerToken = registerCustomer("notif-stranger@example.com", "9622200003");
        mockMvc.perform(put("/api/notifications/" + firstNotificationId + "/read").header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/notifications/read-all").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());
        String unreadAfterBody = mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + customerToken))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(unreadAfterBody).at("/data").asLong()).isZero();

        // 2. Full ride to completion: RideCompletedEvent (both sides).
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

        assertThat(notificationTypesFor(customerToken)).contains("RIDE_COMPLETED");
        assertThat(notificationTypesFor(driverToken)).contains("RIDE_COMPLETED");

        // 3. Payment: PaymentCompletedEvent (both sides).
        mockMvc.perform(post("/api/rides/" + rideId + "/pay").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());
        assertThat(notificationTypesFor(customerToken)).contains("PAYMENT_COMPLETED");
        assertThat(notificationTypesFor(driverToken)).contains("PAYMENT_COMPLETED");

        // 4. Complaint filed + resolved: ComplaintFiledEvent then ComplaintResolvedEvent, both to the filer (the customer here).
        String complaintBody = mockMvc.perform(post("/api/rides/" + rideId + "/complaints")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"OTHER\",\"description\":\"Testing notifications\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long complaintId = objectMapper.readTree(complaintBody).at("/data/id").asLong();
        assertThat(notificationTypesFor(customerToken)).contains("COMPLAINT_FILED");

        String adminEmail = "notif-complaint-admin@example.com";
        userService.createUser("Notif Complaint Admin", adminEmail, "9622200004",
                passwordEncoder.encode("Password123!"), RoleName.ADMIN);
        String adminToken = loginAndGetToken(adminEmail, "Password123!");
        mockMvc.perform(put("/api/admin/complaints/" + complaintId + "/resolve").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        assertThat(notificationTypesFor(customerToken)).contains("COMPLAINT_RESOLVED");
    }
}
