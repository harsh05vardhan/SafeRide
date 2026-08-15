package com.saferide.auth;

import com.saferide.user.Role;
import com.saferide.user.RoleName;
import com.saferide.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Issues and validates SafeRide access tokens. Access tokens are
 * self-contained JWTs (subject = user id, plus email + roles claims) so an
 * authenticated request never needs a database round trip. Refresh tokens
 * are deliberately <em>not</em> JWTs - see {@link RefreshToken} - so they
 * can be revoked server-side, which a stateless JWT can't be.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";

    private final JwtProperties jwtProperties;

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roleNames = user.getRoles().stream().map(Role::getName).map(Enum::name).toList();

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLES, roleNames)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(jwtProperties.getAccessExpirationMs())))
                .signWith(signingKey())
                .compact();
    }

    public long getAccessExpirationMs() {
        return jwtProperties.getAccessExpirationMs();
    }

    /**
     * Parses and validates an access token, returning the authenticated
     * principal. Throws {@link JwtException} (including
     * {@link ExpiredJwtException}) on any invalid, tampered or expired
     * token - the caller (the JWT filter) treats all of those as "not
     * authenticated" rather than a 500.
     */
    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = Long.valueOf(claims.getSubject());
        String email = claims.get(CLAIM_EMAIL, String.class);
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get(CLAIM_ROLES, List.class);
        return new AuthenticatedUser(userId, email, Set.copyOf(roles));
    }

    public boolean hasRole(AuthenticatedUser user, RoleName role) {
        return user.roles().contains(role.name());
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
