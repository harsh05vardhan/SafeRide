package com.saferide.admin;

import com.saferide.admin.dto.AdminActionRequest;
import com.saferide.auth.AuthenticatedUser;
import com.saferide.common.ApiResponse;
import com.saferide.complaint.ComplaintStatus;
import com.saferide.complaint.dto.ComplaintResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin review of filed complaints - {@code /admin/complaints}. Every route requires ADMIN. */
@Tag(name = "Admin - Complaints")
@RestController
@RequestMapping("/api/admin/complaints")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminComplaintController {

    private final AdminComplaintService adminComplaintService;

    @GetMapping
    public ApiResponse<Page<ComplaintResponse>> list(
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ApiResponse.ok(adminComplaintService.list(status, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<ComplaintResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(adminComplaintService.getDetail(id));
    }

    @PutMapping("/{id}/resolve")
    public ApiResponse<ComplaintResponse> resolve(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminActionRequest request) {
        String notes = request == null ? null : request.reason();
        return ApiResponse.ok("Complaint resolved", adminComplaintService.resolve(admin.userId(), id, notes));
    }

    @PutMapping("/{id}/dismiss")
    public ApiResponse<ComplaintResponse> dismiss(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminActionRequest request) {
        String notes = request == null ? null : request.reason();
        return ApiResponse.ok("Complaint dismissed", adminComplaintService.dismiss(admin.userId(), id, notes));
    }
}
