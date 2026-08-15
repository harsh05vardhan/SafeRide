package com.saferide.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Plain STOMP over a native WebSocket at {@code /ws} - no SockJS fallback.
 * Every SafeRide target browser (the frontend is a modern Vite/React SPA,
 * not something needing IE11-era polyfills) supports {@code WebSocket}
 * natively, so SockJS would only add an HTTP long-polling fallback path
 * nothing here actually needs.
 *
 * <p>Three kinds of destination exist:
 * <ul>
 *   <li>{@code /topic/rides/{id}/status} - ride status change notifications,
 *       broadcast to whoever is subscribed (the ride's own customer and/or
 *       driver - see {@link StompAuthChannelInterceptor}).</li>
 *   <li>{@code /topic/rides/{id}/location} - live driver location while a
 *       ride is active.</li>
 *   <li>{@code /user/queue/assignments} - a private, per-driver channel
 *       (Spring's user-destination feature, resolved via
 *       {@link StompPrincipal#getName()}) telling a driver a new ride was
 *       just proactively assigned to them, without them needing to already
 *       know a ride id to subscribe to it.</li>
 * </ul>
 *
 * <p>No {@code /app} destination prefix is configured - the client never
 * sends anything over this connection, it only receives. All writes still
 * go through the existing authenticated REST API, which is what actually
 * changes ride/driver state; this channel purely broadcasts the result.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins.split(","));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
