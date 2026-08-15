package com.saferide.admin.dto;

import com.saferide.driver.dto.DriverDocumentResponse;
import com.saferide.vehicle.dto.VehicleResponse;
import java.util.List;

/** Everything an admin needs on one screen to decide approve vs. block - see /admin/driver-verification. */
public record DriverAdminDetailResponse(
        DriverAdminSummaryResponse driver,
        List<VehicleResponse> vehicles,
        List<DriverDocumentResponse> documents) {
}
