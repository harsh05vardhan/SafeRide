package com.saferide.fare;

import com.saferide.common.ApiResponse;
import com.saferide.fare.dto.FareBreakdownResponse;
import com.saferide.fare.dto.FareCalculateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Standalone fare preview - e.g. one call per vehicle card on the
 * ride-options page, before any ride exists. This never persists
 * anything; {@code GET/POST /api/rides/{id}/fare*} (in
 * {@code RideController}) is where a fare actually attached to a ride
 * lives.
 */
@Tag(name = "Fare")
@RestController
@RequestMapping("/api/fare")
@RequiredArgsConstructor
public class FareController {

    private final FareService fareService;

    @PostMapping("/calculate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<FareBreakdownResponse> calculate(@Valid @RequestBody FareCalculateRequest request) {
        return ApiResponse.ok(fareService.preview(request));
    }
}
