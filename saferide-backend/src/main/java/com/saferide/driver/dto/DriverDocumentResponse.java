package com.saferide.driver.dto;

import com.saferide.common.VerificationStatus;
import com.saferide.driver.DocumentType;
import java.time.Instant;

public record DriverDocumentResponse(
        Long id,
        Long driverId,
        DocumentType documentType,
        String documentUrl,
        VerificationStatus verificationStatus,
        Instant uploadedAt,
        Instant verifiedAt) {
}
