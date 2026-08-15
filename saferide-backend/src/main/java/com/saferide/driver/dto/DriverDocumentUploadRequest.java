package com.saferide.driver.dto;

import com.saferide.driver.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DriverDocumentUploadRequest(
        @NotNull(message = "Document type is required")
        DocumentType documentType,

        @NotBlank(message = "Document URL is required")
        @Size(max = 500, message = "Document URL is too long")
        String documentUrl) {
}
