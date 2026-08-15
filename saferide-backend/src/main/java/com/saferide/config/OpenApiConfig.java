package com.saferide.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI is served at {@code /swagger-ui.html} and the raw spec at
 * {@code /v3/api-docs}. The "bearerAuth" security scheme is declared now so
 * that once JWT auth lands in Phase 2, controllers only need
 * {@code @SecurityRequirement(name = "bearerAuth")} to show the padlock in
 * the UI - no further OpenAPI wiring required.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI safeRideOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SafeRide API")
                        .description("REST API for the SafeRide ride-booking platform")
                        .version("v0.1")
                        .contact(new Contact().name("SafeRide Engineering"))
                        .license(new License().name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .schemaRequirement("bearerAuth", new SecurityScheme()
                        .name("bearerAuth")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));
    }
}
