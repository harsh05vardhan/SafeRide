package com.saferide.user.dto;

import jakarta.validation.constraints.Size;

/** Blank/null clears the avatar back to the initial-letter fallback - same "paste a hosted image URL" shape as DriverDocumentUploadRequest.documentUrl, no format validation server-side (the frontend enforces a real URL, same split as everywhere else this pattern is used). */
public record UpdateAvatarRequest(@Size(max = 500, message = "Avatar URL is too long") String avatarUrl) {
}
