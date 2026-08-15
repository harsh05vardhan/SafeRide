package com.saferide.driver;

import com.saferide.common.VerificationStatus;
import com.saferide.driver.dto.DriverDocumentResponse;
import com.saferide.driver.dto.DriverDocumentUploadRequest;
import com.saferide.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DriverDocumentService {

    private final DriverDocumentRepository documentRepository;
    private final DriverService driverService;

    @Transactional
    public DriverDocumentResponse upload(Long userId, DriverDocumentUploadRequest request) {
        Driver driver = driverService.getDriverByUserId(userId);

        DriverDocument document = new DriverDocument();
        document.setDriver(driver);
        document.setDocumentType(request.documentType());
        document.setDocumentUrl(request.documentUrl());
        document.setVerificationStatus(VerificationStatus.PENDING);
        document.setUploadedAt(Instant.now());

        return toResponse(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public List<DriverDocumentResponse> listForDriver(Long userId) {
        Driver driver = driverService.getDriverByUserId(userId);
        return documentRepository.findByDriverIdOrderByUploadedAtDesc(driver.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DriverDocumentResponse> listForDriverId(Long driverId) {
        return documentRepository.findByDriverIdOrderByUploadedAtDesc(driverId)
                .stream().map(this::toResponse).toList();
    }

    /** Admin action - no ownership check, any admin may review any document. */
    @Transactional
    public DriverDocumentResponse setVerificationStatus(Long documentId, VerificationStatus status) {
        DriverDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Driver document", documentId));
        document.setVerificationStatus(status);
        document.setVerifiedAt(Instant.now());
        return toResponse(documentRepository.save(document));
    }

    private DriverDocumentResponse toResponse(DriverDocument document) {
        return new DriverDocumentResponse(
                document.getId(),
                document.getDriver().getId(),
                document.getDocumentType(),
                document.getDocumentUrl(),
                document.getVerificationStatus(),
                document.getUploadedAt(),
                document.getVerifiedAt());
    }
}
