package com.saferide.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * Everything that happens after {@code RIDE_COMPLETED}: paying the locked
 * fare, rating the driver, filing/reviewing a complaint, and the Safety
 * Center (SOS + trip-share link) - all four of Phase 12's domains, driven
 * through the same real ride lifecycle {@code RideIntegrationTest} already
 * covers through {@code PAYMENT_PENDING}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostRideIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;

    // Kolkata - far from every other test class's fixtures (Guwahati/Shillong/New Delhi/Mumbai).
    private static final double PICKUP_LAT = 22.5726;
    private static final double PICKUP_LNG = 88.3639;

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
                {"name":"Post-Ride Customer","email":"%s","phone":"%s","password":"Password123!","role":"CUSTOMER"}
                """.formatted(email, phone);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        return loginAndGetToken(email, "Password123!");
    }

    private String readyDriver(String email, String phone, String license, String vehicleNumber) throws Exception {
        String payload = """
                {"name":"Post-Ride Driver","email":"%s","phone":"%s","password":"Password123!","role":"DRIVER","licenseNumber":"%s"}
                """.formatted(email, phone, license);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        String token = loginAndGetToken(email, "Password123!");

        String adminEmail = "post-ride-admin-" + phone + "@example.com";
        userService.createUser("Post-Ride Test Admin", adminEmail, "6" + phone.substring(1),
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

        return token;
    }

    /** Books, confirms, accepts, arrives, starts and completes a ride, landing it at PAYMENT_PENDING - returns the ride id. */
    private long buildRideAtPaymentPending(String customerToken, String driverToken) throws Exception {
        String payload = """
                {"pickupAddress":"Kolkata Station","destinationAddress":"Kolkata Airport",
                 "pickupLatitude":%s,"pickupLongitude":%s,
                 "destinationLatitude":22.6520,"destinationLongitude":88.4463,"vehicleType":"SEDAN"}
                """.formatted(PICKUP_LAT, PICKUP_LNG);
        String createBody = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long rideId = objectMapper.readTree(createBody).at("/data/id").asLong();

        mockMvc.perform(post("/api/rides/" + rideId + "/fare/confirm").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rides/" + rideId + "/accept").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rides/" + rideId + "/arrive").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk());

        String customerView = mockMvc.perform(get("/api/rides/" + rideId).header("Authorization", "Bearer " + customerToken))
                .andReturn().getResponse().getContentAsString();
        String otp = objectMapper.readTree(customerView).at("/data/otp").asText();

        mockMvc.perform(post("/api/rides/" + rideId + "/start")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"" + otp + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rides/" + rideId + "/complete").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYMENT_PENDING"));

        return rideId;
    }

    @Test
    void payingChargesExactlyTheLockedFareAndCannotBeRepeated() throws Exception {
        String customerToken = registerCustomer("pay-customer1@example.com", "9788300001");
        String driverToken = readyDriver("pay-driver1@example.com", "9788300002", "DL-PAY-1", "PAY-VEH-01");
        long rideId = buildRideAtPaymentPending(customerToken, driverToken);

        // No payment exists yet.
        mockMvc.perform(get("/api/rides/" + rideId + "/payment").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNotFound());

        String fareBody = mockMvc.perform(get("/api/rides/" + rideId + "/fare").header("Authorization", "Bearer " + customerToken))
                .andReturn().getResponse().getContentAsString();
        double lockedAmount = objectMapper.readTree(fareBody).at("/data/lockedAmount").asDouble();

        mockMvc.perform(post("/api/rides/" + rideId + "/pay").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYMENT_COMPLETED"));

        String paymentBody = mockMvc.perform(get("/api/rides/" + rideId + "/payment").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        double charged = objectMapper.readTree(paymentBody).at("/data/amount").asDouble();
        assertThat(charged).isEqualTo(lockedAmount);

        // A second payment attempt is rejected - the ride is no longer PAYMENT_PENDING.
        mockMvc.perform(post("/api/rides/" + rideId + "/pay").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RIDE_STATE"));
    }

    @Test
    void ratingUpdatesDriverRatingAndCanOnlyHappenOnce() throws Exception {
        String customerToken = registerCustomer("rate-customer1@example.com", "9788300003");
        String driverToken = readyDriver("rate-driver1@example.com", "9788300004", "DL-RATE-1", "RATE-VEH-01");
        long rideId = buildRideAtPaymentPending(customerToken, driverToken);
        mockMvc.perform(post("/api/rides/" + rideId + "/pay").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rides/" + rideId + "/rating").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNotFound());

        // Default driver rating is 5.0 - a low score must measurably move it down (EMA, not overwrite).
        mockMvc.perform(post("/api/rides/" + rideId + "/rating")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":2,\"comment\":\"Took a longer route than necessary\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RATED"));

        String driverProfile = mockMvc.perform(get("/api/drivers/profile").header("Authorization", "Bearer " + driverToken))
                .andReturn().getResponse().getContentAsString();
        double newRating = objectMapper.readTree(driverProfile).at("/data/rating").asDouble();
        assertThat(newRating).isLessThan(5.0);

        // RATED is terminal - a second rating attempt on the same ride is rejected.
        mockMvc.perform(post("/api/rides/" + rideId + "/rating")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RIDE_STATE"));
    }

    @Test
    void filingAComplaintIncrementsDriverComplaintCountAndLowersReliability() throws Exception {
        String customerToken = registerCustomer("complain-customer1@example.com", "9788300005");
        String driverToken = readyDriver("complain-driver1@example.com", "9788300006", "DL-COMPLAIN-1", "COMPLAIN-VEH-01");
        long rideId = buildRideAtPaymentPending(customerToken, driverToken);

        String beforeProfile = mockMvc.perform(get("/api/drivers/profile").header("Authorization", "Bearer " + driverToken))
                .andReturn().getResponse().getContentAsString();
        int complaintsBefore = objectMapper.readTree(beforeProfile).at("/data/complaintCount").asInt();
        double reliabilityBefore = objectMapper.readTree(beforeProfile).at("/data/reliabilityScore").asDouble();

        // A stranger cannot file against this ride.
        String strangerToken = registerCustomer("complain-stranger@example.com", "9788300007");
        mockMvc.perform(post("/api/rides/" + rideId + "/complaints")
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXTRA_FARE_DEMANDED\",\"description\":\"Not my ride\"}"))
                .andExpect(status().isForbidden());

        String complaintBody = mockMvc.perform(post("/api/rides/" + rideId + "/complaints")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXTRA_FARE_DEMANDED\",\"description\":\"Driver asked for 200 extra in cash\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.filedByRole").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();
        long complaintId = objectMapper.readTree(complaintBody).at("/data/id").asLong();

        String afterProfile = mockMvc.perform(get("/api/drivers/profile").header("Authorization", "Bearer " + driverToken))
                .andReturn().getResponse().getContentAsString();
        int complaintsAfter = objectMapper.readTree(afterProfile).at("/data/complaintCount").asInt();
        double reliabilityAfter = objectMapper.readTree(afterProfile).at("/data/reliabilityScore").asDouble();
        assertThat(complaintsAfter).isEqualTo(complaintsBefore + 1);
        assertThat(reliabilityAfter).isLessThan(reliabilityBefore);

        // Shows up in the customer's own complaint history.
        String mine = mockMvc.perform(get("/api/complaints").header("Authorization", "Bearer " + customerToken))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(mine).at("/data/content").findValuesAsText("id")).contains(String.valueOf(complaintId));

        // Admin can see, then resolve it - and can't resolve it twice.
        userService.createUser("Complaint Admin", "complain-admin@example.com", "9788300008",
                passwordEncoder.encode("Password123!"), RoleName.ADMIN);
        String adminToken = loginAndGetToken("complain-admin@example.com", "Password123!");

        mockMvc.perform(get("/api/admin/complaints").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/complaints/" + complaintId + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Refunded the customer via support\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
        mockMvc.perform(put("/api/admin/complaints/" + complaintId + "/resolve").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_COMPLAINT_STATE"));
    }

    @Test
    void sosAlertIsVisibleToAdminAndResolvableOnce() throws Exception {
        String customerToken = registerCustomer("sos-customer1@example.com", "9788300009");
        String driverToken = readyDriver("sos-driver1@example.com", "9788300010", "DL-SOS-1", "SOS-VEH-01");
        long rideId = buildRideAtPaymentPending(customerToken, driverToken);

        String strangerToken = registerCustomer("sos-stranger@example.com", "9788300011");
        mockMvc.perform(post("/api/rides/" + rideId + "/sos")
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":22.58,\"longitude\":88.36}"))
                .andExpect(status().isForbidden());

        String alertBody = mockMvc.perform(post("/api/rides/" + rideId + "/sos")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":22.58,\"longitude\":88.36}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        long alertId = objectMapper.readTree(alertBody).at("/data/id").asLong();

        String adminEmail = "sos-admin@example.com";
        userService.createUser("SOS Admin", adminEmail, "9788300012", passwordEncoder.encode("Password123!"), RoleName.ADMIN);
        String adminToken = loginAndGetToken(adminEmail, "Password123!");

        String adminList = mockMvc.perform(get("/api/admin/safety/alerts?status=ACTIVE").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].rideId").value(rideId))
                .andExpect(jsonPath("$.data.content[0].customer.email").value("sos-customer1@example.com"))
                .andExpect(jsonPath("$.data.content[0].driver.name").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(adminList).at("/data/content").findValuesAsText("id")).contains(String.valueOf(alertId));

        // Resolving requires a real account of what happened - an empty/missing body is rejected.
        mockMvc.perform(put("/api/admin/safety/alerts/" + alertId + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/admin/safety/alerts/" + alertId + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Called the rider, false alarm - phone pocket-dialed the button.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.resolvedBy.email").value(adminEmail))
                .andExpect(jsonPath("$.data.resolutionNotes").value("Called the rider, false alarm - phone pocket-dialed the button."));

        mockMvc.perform(put("/api/admin/safety/alerts/" + alertId + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"trying again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_SAFETY_ALERT_STATE"));
    }

    @Test
    void tripShareLinkIsPubliclyReadableButOnlyTheCustomerCanCreateOne() throws Exception {
        String customerToken = registerCustomer("share-customer1@example.com", "9788300013");
        String driverToken = readyDriver("share-driver1@example.com", "9788300014", "DL-SHARE-1", "SHARE-VEH-01");
        long rideId = buildRideAtPaymentPending(customerToken, driverToken);

        // The driver cannot generate a share link for their own assigned ride.
        mockMvc.perform(post("/api/rides/" + rideId + "/share").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden());

        String shareBody = mockMvc.perform(post("/api/rides/" + rideId + "/share").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(shareBody).at("/data/shareToken").asText();
        assertThat(token).isNotBlank();

        // No Authorization header at all - this is the whole point of a share link.
        JsonNode publicTrip = objectMapper.readTree(
                mockMvc.perform(get("/api/public/trips/" + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        assertThat(publicTrip.at("/data/pickupAddress").asText()).isEqualTo("Kolkata Station");
        assertThat(publicTrip.at("/data/driverName").asText()).isNotBlank();
        // Never leaks anything that would let a stranger contact either party or start the ride.
        assertThat(publicTrip.at("/data").has("otp")).isFalse();

        mockMvc.perform(get("/api/public/trips/not-a-real-token"))
                .andExpect(status().isNotFound());

        // Calling again for the same ride returns the same still-valid token rather than a new one.
        String secondShareBody = mockMvc.perform(post("/api/rides/" + rideId + "/share").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(secondShareBody).at("/data/shareToken").asText()).isEqualTo(token);
    }
}
