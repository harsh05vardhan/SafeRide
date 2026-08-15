package com.saferide.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@code app.jwt.*} (backed by the {@code JWT_SECRET},
 * {@code JWT_EXPIRATION}, {@code JWT_REFRESH_EXPIRATION} environment
 * variables - see application.yml) instead of scattering
 * {@code @Value} lookups across the auth package.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HMAC-SHA256 signing key. Must be at least 256 bits (32 chars) - never commit a real value. */
    private String secret;

    /** Access token lifetime in milliseconds. Kept short (default 15 min) since it can't be revoked. */
    private long accessExpirationMs;

    /** Refresh token lifetime in milliseconds (default 7 days). Revocable - tracked in the refresh_tokens table. */
    private long refreshExpirationMs;
}
