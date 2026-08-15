package com.saferide.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saferide.user.Role;
import com.saferide.user.RoleName;
import com.saferide.user.User;
import com.saferide.user.UserStatus;
import io.jsonwebtoken.ExpiredJwtException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-at-least-32-bytes-long!!");
        properties.setAccessExpirationMs(60_000);
        properties.setRefreshExpirationMs(600_000);
        jwtService = new JwtService(properties);
    }

    private User sampleUser() {
        User user = new User();
        user.setId(42L);
        user.setName("Ada Lovelace");
        user.setEmail("ada@example.com");
        user.setPhone("9876543210");
        user.setPasswordHash("irrelevant-for-this-test");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(new Role(1L, RoleName.CUSTOMER)));
        return user;
    }

    @Test
    void generatedTokenRoundTripsToTheSameUserAndRoles() {
        User user = sampleUser();

        String token = jwtService.generateAccessToken(user);
        AuthenticatedUser parsed = jwtService.parseAccessToken(token);

        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.email()).isEqualTo("ada@example.com");
        assertThat(parsed.roles()).containsExactly("CUSTOMER");
    }

    @Test
    void expiredTokenIsRejected() {
        JwtProperties expiredProps = new JwtProperties();
        expiredProps.setSecret("unit-test-secret-key-at-least-32-bytes-long!!");
        expiredProps.setAccessExpirationMs(-1_000); // already expired the instant it's issued
        expiredProps.setRefreshExpirationMs(600_000);
        JwtService expiredJwtService = new JwtService(expiredProps);

        String token = expiredJwtService.generateAccessToken(sampleUser());

        assertThatThrownBy(() -> expiredJwtService.parseAccessToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateAccessToken(sampleUser());
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.parseAccessToken(tampered))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }
}
