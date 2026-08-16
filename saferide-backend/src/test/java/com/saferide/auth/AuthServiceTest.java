package com.saferide.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saferide.auth.dto.AuthResponse;
import com.saferide.auth.dto.LoginRequest;
import com.saferide.auth.dto.RegisterRequest;
import com.saferide.driver.DriverService;
import com.saferide.exception.DuplicateResourceException;
import com.saferide.exception.InvalidCredentialsException;
import com.saferide.exception.InvalidRefreshTokenException;
import com.saferide.exception.UnauthorizedException;
import com.saferide.user.RoleName;
import com.saferide.user.User;
import com.saferide.user.UserRepository;
import com.saferide.user.UserService;
import com.saferide.user.UserStatus;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private DriverService driverService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private LoginRateLimiter loginRateLimiter;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("unit-test-secret-key-at-least-32-bytes-long!!");
        jwtProperties.setAccessExpirationMs(900_000);
        jwtProperties.setRefreshExpirationMs(604_800_000);
        jwtService = new JwtService(jwtProperties);

        authService = new AuthService(
                userRepository, userService, driverService, refreshTokenRepository,
                passwordEncoder, jwtService, jwtProperties, loginRateLimiter);
    }

    private User activeUser(String password) {
        User user = new User();
        user.setId(1L);
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setPhone("9000000000");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    @Test
    void registerRejectsSelfServiceAdminSignup() {
        RegisterRequest request = new RegisterRequest(
                "Evil Admin", "evil@example.com", "9999999999", "Password123!", RoleName.ADMIN, null);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Admin");
    }

    @Test
    void registerCreatesCustomerAndReturnsTokens() {
        RegisterRequest request = new RegisterRequest(
                "New Customer", "new@example.com", "9111111111", "Password123!", RoleName.CUSTOMER, null);
        User created = activeUser("Password123!");
        when(userService.createUser(
                eq(request.name()), eq(request.email()), eq(request.phone()), anyString(), eq(RoleName.CUSTOMER)))
                .thenReturn(created);

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("test@example.com");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void registerPropagatesDuplicateEmailFromUserService() {
        RegisterRequest request = new RegisterRequest(
                "Dup", "dup@example.com", "9222222222", "Password123!", RoleName.CUSTOMER, null);
        when(userService.createUser(any(), any(), any(), any(), any()))
                .thenThrow(new DuplicateResourceException("Email is already registered", "EMAIL_ALREADY_REGISTERED"));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void loginSucceedsWithCorrectPassword() {
        User user = activeUser("CorrectHorse1!");
        when(userRepository.findByEmailOrPhone("test@example.com")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(new LoginRequest("test@example.com", "CorrectHorse1!"));

        assertThat(response.user().email()).isEqualTo("test@example.com");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        // A successful login clears whatever failure count the identifier had built up.
        verify(loginRateLimiter).recordSuccess("test@example.com");
    }

    @Test
    void loginFailsWithWrongPassword() {
        User user = activeUser("CorrectHorse1!");
        when(userRepository.findByEmailOrPhone("test@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("test@example.com", "WrongPassword!")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(loginRateLimiter).recordFailure("test@example.com");
    }

    @Test
    void loginFailsForUnknownIdentifier() {
        when(userRepository.findByEmailOrPhone("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(loginRateLimiter).recordFailure("ghost@example.com");
    }

    @Test
    void loginFailsForBlockedAccountEvenWithCorrectPassword() {
        User user = activeUser("CorrectHorse1!");
        user.setStatus(UserStatus.BLOCKED);
        when(userRepository.findByEmailOrPhone("test@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("test@example.com", "CorrectHorse1!")))
                .isInstanceOf(UnauthorizedException.class);
        // Not counted as a rate-limited failure - see AuthService.login's class doc on why.
        verify(loginRateLimiter, org.mockito.Mockito.never()).recordFailure(anyString());
    }

    @Test
    void loginChecksTheRateLimiterBeforeTouchingCredentialsAtAll() {
        org.mockito.Mockito.doThrow(new com.saferide.exception.TooManyAttemptsException("slow down"))
                .when(loginRateLimiter).checkAllowed("test@example.com");

        assertThatThrownBy(() -> authService.login(new LoginRequest("test@example.com", "whatever")))
                .isInstanceOf(com.saferide.exception.TooManyAttemptsException.class);

        // Never even looked the account up once rate-limited.
        verify(userRepository, org.mockito.Mockito.never()).findByEmailOrPhone(anyString());
    }

    @Test
    void refreshRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("some-opaque-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshRejectsRevokedToken() {
        User user = activeUser("CorrectHorse1!");
        RefreshToken revoked = RefreshToken.builder()
                .user(user)
                .tokenHash("irrelevant")
                .expiresAt(java.time.Instant.now().plusSeconds(60))
                .revoked(true)
                .createdAt(java.time.Instant.now())
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh("revoked-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshRotatesTokenAndRevokesThePrevious() {
        User user = activeUser("CorrectHorse1!");
        RefreshToken stored = RefreshToken.builder()
                .user(user)
                .tokenHash("irrelevant")
                .expiresAt(java.time.Instant.now().plusSeconds(60))
                .revoked(false)
                .createdAt(java.time.Instant.now())
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        authService.refresh("valid-token");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).isRevoked()).isTrue(); // the old token, revoked first
    }

    @Test
    void logoutIsIdempotentForAnUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        authService.logout("never-issued-token");

        verify(refreshTokenRepository, org.mockito.Mockito.never()).save(any());
    }
}
