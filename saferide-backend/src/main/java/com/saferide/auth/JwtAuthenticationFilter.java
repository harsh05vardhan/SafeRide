package com.saferide.auth;

import com.saferide.user.UserRepository;
import com.saferide.user.UserStatus;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs once per request, ahead of Spring MVC. If a valid {@code Bearer}
 * access token is present it populates the security context with an
 * {@link AuthenticatedUser} principal built entirely from the token's
 * claims, plus a status check against the database (see
 * {@link UserRepository#findStatusById}) - the one concession this filter
 * makes to "no database lookup", and a deliberate one: without it, an
 * account an admin blocks mid-session would keep working for up to the
 * access token's own lifetime (15 minutes) instead of being cut off
 * within seconds.
 *
 * <p>That status is cached per user for {@link #STATUS_CACHE_TTL_MS} -
 * the backend and database run in different regions (Singapore/
 * Bangalore), so a real network round trip on <em>every single</em>
 * authenticated request measurably slowed the whole app down once this
 * check was added. A page firing several parallel requests, or a page
 * polling every 15s, now pays that round trip at most once per cache
 * window instead of once per request - a blocked account is still cut
 * off within {@link #STATUS_CACHE_TTL_MS}, just not necessarily on the
 * very next request the way it briefly was.
 *
 * <p>A missing, malformed, expired, or now-blocked/deleted-account token
 * is <em>not</em> rejected here; the request simply proceeds
 * unauthenticated, and whichever endpoint requires auth (e.g.
 * {@code @PreAuthorize}) triggers {@link com.saferide.config.JwtAuthenticationEntryPoint} -
 * the frontend's axios interceptor then tries a silent refresh (which
 * {@code AuthService#refresh} rejects the same way for the same reason)
 * and, failing that, signs the user out.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final long STATUS_CACHE_TTL_MS = 15_000;

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final Map<Long, CachedStatus> statusCache = new ConcurrentHashMap<>();

    private record CachedStatus(UserStatus status, long expiresAtMs) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMs;
        }
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(AUTH_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                AuthenticatedUser principal = jwtService.parseAccessToken(token);
                UserStatus status = currentStatus(principal.userId());

                if (status == UserStatus.ACTIVE) {
                    List<GrantedAuthority> authorities = principal.roles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .map(GrantedAuthority.class::cast)
                            .toList();

                    var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    log.debug("Rejected access token for user {} with status {}", principal.userId(), status);
                    SecurityContextHolder.clearContext();
                }
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Rejected invalid access token: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Cache-through: a fresh/expired entry costs one indexed-PK read, everything within the TTL window costs nothing. */
    private UserStatus currentStatus(Long userId) {
        CachedStatus cached = statusCache.get(userId);
        if (cached != null && !cached.isExpired()) {
            return cached.status();
        }
        UserStatus status = userRepository.findStatusById(userId).orElse(null);
        statusCache.put(userId, new CachedStatus(status, System.currentTimeMillis() + STATUS_CACHE_TTL_MS));
        return status;
    }
}
