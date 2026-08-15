package com.saferide.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is centralized here so it stays a single, explicit decision instead of
 * being scattered across controllers. The allowed origin list is driven by
 * configuration ({@code app.cors.allowed-origins}) so dev, test, and prod
 * profiles can each point at their own frontend origin without code changes.
 *
 * <p>When Spring Security is introduced in Phase 2, a matching
 * {@code CorsConfigurationSource} bean will be added here for the security
 * filter chain to consume, so the two never drift apart.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins.split(",");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
