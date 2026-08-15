package com.saferide.driver.dto;

import com.saferide.driver.DriverOnlineStatus;
import com.saferide.driver.DriverVerificationStatus;
import com.saferide.user.dto.UserSummaryDTO;

public record DriverProfileResponse(
        Long id,
        UserSummaryDTO user,
        String licenseNumber,
        DriverVerificationStatus verificationStatus,
        DriverOnlineStatus onlineStatus,
        Double rating,
        Double acceptanceRate,
        Double cancellationRate,
        Double completionRate,
        Double reliabilityScore) {
}
