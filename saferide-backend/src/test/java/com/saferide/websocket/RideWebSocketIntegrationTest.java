package com.saferide.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferide.ride.dto.DriverLocationEvent;
import com.saferide.ride.dto.RideAssignmentEvent;
import com.saferide.ride.dto.RideStatusEvent;
import com.saferide.user.RoleName;
import com.saferide.user.UserService;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * End to end over a real WebSocket connection (not MockMvc, which can't
 * exercise an upgraded connection) - authentication on CONNECT, per-ride
 * subscription authorization, and all three broadcast channels
 * {@link com.saferide.websocket.WebSocketConfig} documents: ride status,
 * driver location, and a driver's private assignment notification.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RideWebSocketIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;

    // Far from every other test class's fixtures (Guwahati/Shillong/New Delhi) - see
    // MatchingIntegrationTest's comment on why @SpringBootTest contexts share one H2 database.
    private static final double PICKUP_LAT = 19.0760;
    private static final double PICKUP_LNG = 72.8777;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUpStompClient() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        // Reuse the app's own auto-configured ObjectMapper (JSR-310/Instant support included)
        // rather than a bare `new ObjectMapper()`, which can't deserialize the events' Instant
        // fields at all.
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
    }

    @Test
    void connectingWithoutAValidTokenIsRejected() {
        StompHeaders connectHeaders = new StompHeaders(); // no Authorization header
        CompletableFuture<StompSession> future =
                stompClient.connectAsync(wsUrl(), new WebSocketHttpHeaders(), connectHeaders, new RecordingHandler());

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void customerReceivesStatusEventsForTheirOwnRide() throws Exception {
        String customerToken = registerCustomer("ws-customer1@example.com", "9677700001");
        String driverToken = readyDriverAt("ws-driver1@example.com", "9677700002", "DL-WS-1", "WS-VEH-01");
        long rideId = createAndConfirmRide(customerToken);

        StompSession session = connect(customerToken);
        LinkedBlockingQueue<RideStatusEvent> events =
                subscribe(session, "/topic/rides/" + rideId + "/status", RideStatusEvent.class);
        awaitSubscriptionRegistered();

        // Triggers a real transition (DRIVER_ASSIGNED -> DRIVER_ACCEPTED) after the subscription
        // above is already active, so it's guaranteed to be observed.
        mockMvc.perform(post("/api/rides/" + rideId + "/accept").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk());

        RideStatusEvent event = events.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.rideId()).isEqualTo(rideId);
        assertThat(event.status().name()).isEqualTo("DRIVER_ACCEPTED");
    }

    @Test
    void aStrangerCannotSubscribeToSomeoneElsesRideTopic() throws Exception {
        String ownerToken = registerCustomer("ws-owner@example.com", "9677700003");
        readyDriverAt("ws-driver2@example.com", "9677700004", "DL-WS-2", "WS-VEH-02");
        long rideId = createAndConfirmRide(ownerToken);

        String strangerToken = registerCustomer("ws-stranger@example.com", "9677700005");
        RecordingHandler handler = new RecordingHandler();
        StompSession session = stompClient
                .connectAsync(wsUrl(), new WebSocketHttpHeaders(), authHeaders(strangerToken), handler)
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/rides/" + rideId + "/status", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return RideStatusEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                // Should never be called - the subscription itself must be rejected.
            }
        });

        // Rejecting the SUBSCRIBE frame sends a STOMP ERROR frame, which terminates the session -
        // either callback proves the subscription never succeeded.
        Throwable error = handler.errorOrTransportFailure().get(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
    }

    @Test
    void driverReceivesAPrivateNotificationWhenProactivelyAssigned() throws Exception {
        String driverEmail = "ws-driver3@example.com";
        String driverToken = readyDriverAt(driverEmail, "9677700006", "DL-WS-3", "WS-VEH-03");

        StompSession driverSession = connect(driverToken);
        LinkedBlockingQueue<RideAssignmentEvent> assignments =
                subscribe(driverSession, "/user/queue/assignments", RideAssignmentEvent.class);

        String customerToken = registerCustomer("ws-customer3@example.com", "9677700007");
        long rideId = createAndConfirmRide(customerToken);

        RideAssignmentEvent event = assignments.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.rideId()).isEqualTo(rideId);
        assertThat(event.status().name()).isEqualTo("DRIVER_ASSIGNED");
    }

    @Test
    void driverLocationUpdatesBroadcastToTheRideTopic() throws Exception {
        String customerToken = registerCustomer("ws-customer4@example.com", "9677700008");
        String driverToken = readyDriverAt("ws-driver4@example.com", "9677700009", "DL-WS-4", "WS-VEH-04");
        long rideId = createAndConfirmRide(customerToken);
        mockMvc.perform(post("/api/rides/" + rideId + "/accept").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk());

        StompSession session = connect(customerToken);
        LinkedBlockingQueue<DriverLocationEvent> locations =
                subscribe(session, "/topic/rides/" + rideId + "/location", DriverLocationEvent.class);
        awaitSubscriptionRegistered();

        mockMvc.perform(put("/api/drivers/location")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":%s,\"longitude\":%s}".formatted(PICKUP_LAT + 0.002, PICKUP_LNG)))
                .andExpect(status().isOk());

        DriverLocationEvent event = locations.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.rideId()).isEqualTo(rideId);
        assertThat(event.latitude()).isEqualTo(PICKUP_LAT + 0.002);
    }

    // ---- helpers ----

    private String wsUrl() {
        return "ws://localhost:" + port + "/ws";
    }

    private StompHeaders authHeaders(String token) {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return headers;
    }

    private StompSession connect(String token) throws Exception {
        return stompClient
                .connectAsync(wsUrl(), new WebSocketHttpHeaders(), authHeaders(token), new RecordingHandler())
                .get(5, TimeUnit.SECONDS);
    }

    /**
     * {@code StompSession.subscribe} only queues the SUBSCRIBE frame onto the async WebSocket
     * connection - it doesn't wait for the in-memory broker to actually register it. Without this,
     * a REST call fired immediately afterwards (synchronous, and fast) can trigger the broadcast
     * before the subscription exists to receive it. A short, fixed wait is simpler and just as
     * reliable as a real handshake here, since everything in this test runs in-process.
     */
    private void awaitSubscriptionRegistered() throws InterruptedException {
        Thread.sleep(300);
    }

    private <T> LinkedBlockingQueue<T> subscribe(StompSession session, String destination, Class<T> payloadType) {
        LinkedBlockingQueue<T> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((T) payload);
            }
        });
        return queue;
    }

    private static class RecordingHandler extends StompSessionHandlerAdapter {
        private final CompletableFuture<Throwable> failure = new CompletableFuture<>();

        CompletableFuture<Throwable> errorOrTransportFailure() {
            return failure;
        }

        @Override
        public void handleException(
                StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
            failure.complete(exception);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            failure.complete(exception);
        }
    }

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
                {"name":"WS Customer","email":"%s","phone":"%s","password":"Password123!","role":"CUSTOMER"}
                """.formatted(email, phone);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        return loginAndGetToken(email, "Password123!");
    }

    /** Registers, verifies, verifies a matching vehicle, goes online, and reports a location - fully matchable, mirrors MatchingIntegrationTest. */
    private String readyDriverAt(String email, String phone, String license, String vehicleNumber) throws Exception {
        String payload = """
                {"name":"WS Driver","email":"%s","phone":"%s","password":"Password123!","role":"DRIVER","licenseNumber":"%s"}
                """.formatted(email, phone, license);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        String token = loginAndGetToken(email, "Password123!");

        String adminEmail = "ws-admin-" + phone + "@example.com";
        userService.createUser("WS Test Admin", adminEmail, "7" + phone.substring(1),
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
                        .content("{\"latitude\":%s,\"longitude\":%s}".formatted(PICKUP_LAT + 0.001, PICKUP_LNG)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/drivers/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onlineStatus\":\"ONLINE\"}"))
                .andExpect(status().isOk());

        return token;
    }

    private long createAndConfirmRide(String customerToken) throws Exception {
        String payload = """
                {"pickupAddress":"Mumbai CST","destinationAddress":"Mumbai Airport",
                 "pickupLatitude":%s,"pickupLongitude":%s,
                 "destinationLatitude":19.0896,"destinationLongitude":72.8656,"vehicleType":"SEDAN"}
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
        return rideId;
    }
}
