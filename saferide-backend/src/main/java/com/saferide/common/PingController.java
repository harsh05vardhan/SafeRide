package com.saferide.common;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated liveness endpoint used by the frontend scaffold and by
 * infrastructure smoke tests to confirm the backend, its response envelope,
 * and CORS are wired correctly. Real domain endpoints start arriving in
 * Phase 2 (auth) onward.
 */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    public ApiResponse<Map<String, Object>> ping() {
        return ApiResponse.ok(Map.of(
                "service", "saferide-backend",
                "status", "UP",
                "timestamp", Instant.now().toString()));
    }
}
