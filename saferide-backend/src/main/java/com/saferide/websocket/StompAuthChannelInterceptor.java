package com.saferide.websocket;

import com.saferide.auth.AuthenticatedUser;
import com.saferide.auth.JwtService;
import com.saferide.ride.RideRepository;
import io.jsonwebtoken.JwtException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * The WebSocket equivalent of {@link com.saferide.auth.JwtAuthenticationFilter}
 * + a per-destination authorization check, rolled into one interceptor
 * because STOMP has no separate "authenticate this connection" vs.
 * "authorize this destination" request cycle the way HTTP does.
 *
 * <p><b>Why authentication happens on CONNECT, not the HTTP handshake:</b> a
 * browser's native {@code WebSocket} API cannot set an {@code Authorization}
 * header on the handshake request, so {@code JwtAuthenticationFilter} (which
 * only ever reads HTTP headers) has nothing to check for {@code /ws} - see
 * {@code SecurityConfig}, which deliberately leaves {@code /ws/**} at the
 * HTTP layer as {@code permitAll()}. Instead, the access token travels as a
 * native STOMP header on the CONNECT frame itself (something the STOMP.js
 * client on the frontend can set freely), gets validated exactly like an
 * HTTP request would be, and the resulting {@link StompPrincipal} is
 * attached to the STOMP session for the lifetime of the connection - every
 * later frame on this channel already carries {@code accessor.getUser()}.
 *
 * <p><b>Why SUBSCRIBE is checked here and not on each broadcast:</b> ride
 * topics ({@code /topic/rides/{id}/**}) are looked up by a guessable
 * sequential id, so without this check any authenticated user could
 * subscribe to any ride's live status/location. Rejecting the SUBSCRIBE
 * frame itself (rather than filtering what gets sent later) means an
 * unauthorized client's subscription never succeeds in the first place.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern RIDE_TOPIC_PATTERN = Pattern.compile("^/topic/rides/(\\d+)/.+$");

    private final JwtService jwtService;
    private final RideRepository rideRepository;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor));
        } else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    private StompPrincipal authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new MessagingException("Missing Authorization header on CONNECT");
        }
        try {
            AuthenticatedUser user = jwtService.parseAccessToken(header.substring(BEARER_PREFIX.length()));
            return new StompPrincipal(user.userId(), user.roles());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected WebSocket CONNECT with invalid token: {}", ex.getMessage());
            throw new MessagingException("Invalid or expired token");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/topic/rides/")) {
            // /queue/** (per-user destinations) needs no extra check here - Spring only ever
            // delivers a convertAndSendToUser(userId, ...) message to that same userId's own
            // session, so being authenticated at all is already sufficient authorization.
            return;
        }

        Matcher matcher = RIDE_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            throw new MessagingException("Unrecognized ride topic: " + destination);
        }

        if (!(accessor.getUser() instanceof StompPrincipal principal)) {
            throw new MessagingException("Not authenticated");
        }

        Long rideId = Long.valueOf(matcher.group(1));
        boolean isAdmin = principal.roles().contains("ADMIN");
        boolean isParticipant = isAdmin
                || rideRepository.existsByIdAndCustomerId(rideId, principal.userId())
                || rideRepository.existsByIdAndDriver_User_Id(rideId, principal.userId());

        if (!isParticipant) {
            log.debug("Rejected subscription to {} by user {}", destination, principal.userId());
            throw new MessagingException("Not authorized for ride " + rideId);
        }
    }
}
