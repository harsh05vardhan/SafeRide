package com.saferide.exception;

import com.saferide.common.ApiErrorResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates every exception that escapes a controller into the standard
 * {@code ApiErrorResponse} envelope, so the frontend never has to branch on
 * response shape by endpoint.
 *
 * <p>Note: a 401 for a missing/invalid/expired token is thrown by the JWT
 * filter itself, <em>outside</em> the DispatcherServlet, so it never
 * reaches this advice - it's handled separately by
 * {@link com.saferide.config.JwtAuthenticationEntryPoint}. A 403 from
 * {@code @PreAuthorize}, however, is thrown by the method-security AOP
 * interceptor <em>while the controller method is executing inside the
 * DispatcherServlet</em>, so Spring MVC's exception resolution (this
 * class) sees it first - {@link com.saferide.config.JwtAccessDeniedHandler}
 * only ever fires for a hypothetical future URL-pattern-level denial.
 * Both paths are kept so the envelope is identical either way.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        log.warn("Handled API exception [{}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(ApiErrorResponse.of(ex.getMessage(), ex.getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of("Validation failed", "VALIDATION_ERROR", fieldErrors));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of("You do not have permission to access this resource", "ACCESS_DENIED"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ex.getMessage(), "BAD_REQUEST"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("An unexpected error occurred", "INTERNAL_ERROR"));
    }
}
