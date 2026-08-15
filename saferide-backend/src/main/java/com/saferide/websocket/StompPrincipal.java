package com.saferide.websocket;

import java.security.Principal;
import java.util.Set;

/**
 * The {@link Principal} attached to a STOMP session by
 * {@link StompAuthChannelInterceptor} once its CONNECT frame's JWT has been
 * validated. {@link #getName()} deliberately returns the user id (not the
 * email) - it's what {@code SimpMessagingTemplate.convertAndSendToUser}
 * matches against, so a private user-destination send must use the exact
 * same string a controller would build from {@code AuthenticatedUser.userId()}.
 */
public record StompPrincipal(Long userId, Set<String> roles) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
