package com.saferide.admin.dto;

import jakarta.validation.constraints.Size;

/** Body for block/reject admin actions. {@code reason} is optional but stored in the audit log when given. */
public record AdminActionRequest(@Size(max = 500, message = "Reason is too long") String reason) {
}
