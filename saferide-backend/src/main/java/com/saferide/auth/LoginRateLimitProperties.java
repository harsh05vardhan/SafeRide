package com.saferide.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Backs {@code app.security.login-rate-limit.*} - shared by whichever {@link LoginRateLimiter} implementation is active. */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.security.login-rate-limit")
public class LoginRateLimitProperties {

    /** Failed attempts allowed for one identifier within the window before {@link com.saferide.exception.TooManyAttemptsException}. */
    private int maxAttempts;

    private int windowMinutes;
}
