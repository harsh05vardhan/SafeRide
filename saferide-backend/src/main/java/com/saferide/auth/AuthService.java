package com.saferide.auth;

import com.saferide.auth.dto.AuthResponse;
import com.saferide.auth.dto.LoginRequest;
import com.saferide.auth.dto.RegisterRequest;
import com.saferide.driver.DriverService;
import com.saferide.exception.InvalidCredentialsException;
import com.saferide.exception.InvalidRefreshTokenException;
import com.saferide.exception.UnauthorizedException;
import com.saferide.user.User;
import com.saferide.user.UserRepository;
import com.saferide.user.UserService;
import com.saferide.user.UserStatus;
import com.saferide.user.RoleName;
import com.saferide.user.dto.UserSummaryDTO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login, token refresh and logout. Access tokens are
 * self-contained JWTs (see {@link JwtService}); refresh tokens are opaque
 * random values, stored only as a SHA-256 hash, which is what makes
 * server-side revocation on logout possible - a bare JWT refresh token
 * can't be revoked without a separate blacklist, so we don't use one.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserService userService;
    private final DriverService driverService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final LoginRateLimiter loginRateLimiter;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.role() == RoleName.ADMIN) {
            throw new IllegalArgumentException("Admin accounts cannot be self-registered");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = userService.createUser(request.name(), request.email(), request.phone(), passwordHash, request.role());

        if (request.role() == RoleName.DRIVER) {
            driverService.createDriverProfile(user, request.licenseNumber());
        }

        return issueTokens(user);
    }

    /**
     * {@code loginRateLimiter} is checked before anything else - once an
     * identifier has failed too many times recently, this short-circuits
     * with {@code 429 TOO_MANY_ATTEMPTS} before spending a DB lookup or a
     * bcrypt comparison on it. A blocked account fails immediately for an
     * unrelated reason and isn't counted as a rate-limited failure - the
     * limiter exists to slow down password guessing, not to double-punish
     * an account an admin already blocked.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        loginRateLimiter.checkAllowed(request.identifier());

        User user = userRepository.findByEmailOrPhone(request.identifier())
                .orElseGet(() -> {
                    loginRateLimiter.recordFailure(request.identifier());
                    throw new InvalidCredentialsException();
                });

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new UnauthorizedException("This account has been blocked. Contact support.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginRateLimiter.recordFailure(request.identifier());
            throw new InvalidCredentialsException();
        }

        loginRateLimiter.recordSuccess(request.identifier());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String tokenHash = hash(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid or has expired"));

        if (!stored.isValid()) {
            throw new InvalidRefreshTokenException("Refresh token is invalid or has expired");
        }

        // Rotate: revoke the used token so it can never be replayed, issue a fresh pair.
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueTokens(stored.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
        // Unknown/already-revoked token: logout is idempotent, so this is not an error.
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = generateOpaqueToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawRefreshToken))
                .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshExpirationMs()))
                .revoked(false)
                .createdAt(Instant.now())
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.of(accessToken, rawRefreshToken, jwtService.getAccessExpirationMs(), UserSummaryDTO.from(user));
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a JDK-guaranteed algorithm; this can never actually happen.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
