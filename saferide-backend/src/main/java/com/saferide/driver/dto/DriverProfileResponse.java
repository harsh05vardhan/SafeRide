package com.saferide.driver.dto;

import com.saferide.common.VerificationStatus;
import com.saferide.driver.DriverOnlineStatus;
import com.saferide.user.dto.UserSummaryDTO;

public record DriverProfileResponse(
        Long id,
        UserSummaryDTO user,
        String licenseNumber,
        VerificationStatus verificationStatus,
        DriverOnlineStatus onlineStatus,
        Double rating,
        Double acceptanceRate,
        Double cancellationRate,
        Double completionRate,
        Double reliabilityScore) {
}
