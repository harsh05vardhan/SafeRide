package com.saferide.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferide.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Writes the standard {@code ApiErrorResponse} JSON body for 401s that
 * originate in the Spring Security filter chain (missing/invalid/expired
 * token) - these never reach {@code @RestControllerAdvice}, which only
 * sees exceptions thrown inside the DispatcherServlet.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        var body = ApiErrorResponse.of("Authentication is required to access this resource", "UNAUTHENTICATED");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
