package com.saferide.admin.dto;

import com.saferide.common.VerificationStatus;
import com.saferide.driver.DriverOnlineStatus;
import com.saferide.user.UserStatus;
import com.saferide.user.dto.UserSummaryDTO;
import java.time.Instant;

public record DriverAdminSummaryResponse(
        Long id,
        UserSummaryDTO user,
        UserStatus accountStatus,
        String licenseNumber,
        VerificationStatus verificationStatus,
        DriverOnlineStatus onlineStatus,
        Double rating,
        Double acceptanceRate,
        Double cancellationRate,
        Double completionRate,
        Double onTimeRate,
        Integer complaintCount,
        Double reliabilityScore,
        Instant createdAt) {
}
