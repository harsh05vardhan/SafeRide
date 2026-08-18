package com.saferide.admin;

import com.saferide.admin.dto.AdminRideSummaryResponse;
import com.saferide.admin.dto.AnalyticsSummaryResponse;
import com.saferide.admin.dto.TopDriverResponse;
import com.saferide.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Analytics")
@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/summary")
    public ApiResponse<AnalyticsSummaryResponse> summary() {
        return ApiResponse.ok(adminAnalyticsService.summary());
    }

    @GetMapping("/recent-rides")
    public ApiResponse<List<AdminRideSummaryResponse>> recentRides(@RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(adminAnalyticsService.recentRides(Math.min(limit, 20)));
    }

    @GetMapping("/top-drivers")
    public ApiResponse<List<TopDriverResponse>> topDrivers() {
        return ApiResponse.ok(adminAnalyticsService.topDrivers());
    }
}
