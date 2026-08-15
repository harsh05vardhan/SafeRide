package com.saferide.config;

import com.saferide.auth.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Fails startup instead of silently running insecurely if the "prod"
 * profile is active but {@code JWT_SECRET} was never overridden from its
 * (intentionally obvious, dev-only) default value.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProdSafetyChecks {

    private static final String DEV_ONLY_SECRET = "dev-only-secret-change-me-dev-only-secret-change-me";

    private final JwtProperties jwtProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void verifySecretsAreOverridden() {
        if (DEV_ONLY_SECRET.equals(jwtProperties.getSecret())) {
            throw new IllegalStateException(
                    "Refusing to start with the dev-only JWT_SECRET while the 'prod' profile is active. "
                            + "Set a real JWT_SECRET environment variable.");
        }
    }
}
