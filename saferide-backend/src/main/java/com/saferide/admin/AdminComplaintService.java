package com.saferide.admin;

import com.saferide.common.AuditLogService;
import com.saferide.complaint.Complaint;
import com.saferide.complaint.ComplaintRepository;
import com.saferide.complaint.ComplaintStatus;
import com.saferide.complaint.dto.ComplaintResponse;
import com.saferide.event.ComplaintResolvedEvent;
import com.saferide.exception.InvalidComplaintStateException;
import com.saferide.exception.ResourceNotFoundException;
import com.saferide.user.UserService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin review of filed complaints ({@code /admin/complaints}) - the
 * investigation half of the dispute system {@link com.saferide.complaint.ComplaintService}
 * starts. Every resolution is audit-logged, same as driver verification.
 */
@Service
@RequiredArgsConstructor
public class AdminComplaintService {

    private final ComplaintRepository complaintRepository;
    private final AuditLogService auditLogService;
    private final UserService userService;
    private final ApplicationEventPublisher domainEvents;

    @Transactional(readOnly = true)
    public Page<ComplaintResponse> list(ComplaintStatus statusFilter, Pageable pageable) {
        Page<Complaint> page = statusFilter == null
                ? complaintRepository.findAll(pageable)
                : complaintRepository.findByStatusOrderByCreatedAtAsc(statusFilter, pageable);
        return page.map(ComplaintResponse::from);
    }

    @Transactional(readOnly = true)
    public ComplaintResponse getDetail(Long complaintId) {
        return ComplaintResponse.from(getOrThrow(complaintId));
    }

    @Transactional
    public ComplaintResponse resolve(Long adminUserId, Long complaintId, String notes) {
        Complaint complaint = transitionToTerminal(adminUserId, complaintId, ComplaintStatus.RESOLVED, notes);
        auditLogService.record(adminUserId, complaint.getRide().getId(), "COMPLAINT_RESOLVED",
                "Resolved complaint #" + complaintId + (notes == null ? "" : ": " + notes));
        return ComplaintResponse.from(complaint);
    }

    @Transactional
    public ComplaintResponse dismiss(Long adminUserId, Long complaintId, String notes) {
        Complaint complaint = transitionToTerminal(adminUserId, complaintId, ComplaintStatus.DISMISSED, notes);
        auditLogService.record(adminUserId, complaint.getRide().getId(), "COMPLAINT_DISMISSED",
                "Dismissed complaint #" + complaintId + (notes == null ? "" : ": " + notes));
        return ComplaintResponse.from(complaint);
    }

    private Complaint transitionToTerminal(Long adminUserId, Long complaintId, ComplaintStatus target, String notes) {
        Complaint complaint = getOrThrow(complaintId);
        if (complaint.getStatus() == ComplaintStatus.RESOLVED || complaint.getStatus() == ComplaintStatus.DISMISSED) {
            throw new InvalidComplaintStateException("This complaint has already been " + complaint.getStatus().name().toLowerCase());
        }
        complaint.setStatus(target);
        complaint.setAdminNotes(notes);
        complaint.setResolvedBy(userService.getById(adminUserId));
        complaint.setResolvedAt(Instant.now());
        Complaint saved = complaintRepository.save(complaint);

        domainEvents.publishEvent(new ComplaintResolvedEvent(
                saved.getId(), saved.getFiledBy().getId(), target.name(), Instant.now()));

        return saved;
    }

    private Complaint getOrThrow(Long complaintId) {
        return complaintRepository.findById(complaintId)
                .orElseThrow(() -> ResourceNotFoundException.of("Complaint", complaintId));
    }
}
