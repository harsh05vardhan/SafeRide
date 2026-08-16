package com.saferide.complaint.dto;

import com.saferide.complaint.Complaint;
import com.saferide.complaint.ComplaintFiledBy;
import com.saferide.complaint.ComplaintStatus;
import com.saferide.complaint.ComplaintType;
import com.saferide.user.dto.UserSummaryDTO;
import java.time.Instant;

public record ComplaintResponse(
        Long id,
        Long rideId,
        UserSummaryDTO filedBy,
        ComplaintFiledBy filedByRole,
        ComplaintType type,
        String description,
        ComplaintStatus status,
        String adminNotes,
        Instant createdAt,
        Instant resolvedAt) {

    public static ComplaintResponse from(Complaint complaint) {
        return new ComplaintResponse(
                complaint.getId(),
                complaint.getRide().getId(),
                UserSummaryDTO.from(complaint.getFiledBy()),
                complaint.getFiledByRole(),
                complaint.getType(),
                complaint.getDescription(),
                complaint.getStatus(),
                complaint.getAdminNotes(),
                complaint.getCreatedAt(),
                complaint.getResolvedAt());
    }
}
