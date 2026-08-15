package com.saferide.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferide.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Writes the standard {@code ApiErrorResponse} JSON body for 403s -
 * authenticated, but the wrong role (e.g. a CUSTOMER hitting a DRIVER-only
 * endpoint). Role information the frontend supplies is never trusted;
 * this is the server-side enforcement point.
 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        var body = ApiErrorResponse.of("You do not have permission to access this resource", "ACCESS_DENIED");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
