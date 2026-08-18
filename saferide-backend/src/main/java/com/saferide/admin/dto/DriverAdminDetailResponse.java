package com.saferide.admin.dto;

import com.saferide.driver.dto.DriverDocumentResponse;
import com.saferide.rating.dto.RatingResponse;
import com.saferide.vehicle.dto.VehicleResponse;
import java.util.List;

/**
 * Everything an admin needs on one screen to decide approve vs. block -
 * see /admin/driver-verification. {@code completedRideCount}/
 * {@code cancelledRideCount} and {@code recentRatings} are detail-only
 * (not on {@link DriverAdminSummaryResponse}) - computing them needs
 * extra queries per driver that would turn the driver *list* screen into
 * an N+1 query storm; a single detail screen paying that cost once is fine.
 */
public record DriverAdminDetailResponse(
        DriverAdminSummaryResponse driver,
        List<VehicleResponse> vehicles,
        List<DriverDocumentResponse> documents,
        Long completedRideCount,
        Long cancelledRideCount,
        Double totalEarnings,
        List<RatingResponse> recentRatings) {
}
