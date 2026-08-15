package com.saferide.driver;

import com.saferide.common.VerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single document a driver submits for admin review. {@code documentUrl}
 * points at externally-hosted storage - SafeRide does not implement file
 * upload/S3 itself yet, mirroring the {@code MockMapService}/
 * {@code MockPaymentService} pattern: the interface (a URL reference) is
 * real, the storage backend is deferred.
 */
@Entity
@Table(
        name = "driver_documents",
        indexes = @Index(name = "idx_driver_documents_driver_id", columnList = "driver_id"))
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class DriverDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "document_url", nullable = false, length = 500)
    private String documentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;
}
