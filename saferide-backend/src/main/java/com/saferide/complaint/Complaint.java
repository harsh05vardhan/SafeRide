package com.saferide.complaint;

import com.saferide.common.Auditable;
import com.saferide.ride.Ride;
import com.saferide.user.User;
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
 * A complaint filed about one side of a ride by the other - zero or more
 * per ride (unlike {@code Payment}/{@code Rating}, there's no natural
 * "only one" limit: a rider could report both an extra-fare demand and
 * unsafe driving as two separate complaints). {@code filedByRole} is what
 * {@link ComplaintService} uses to decide whether filing this one also
 * counts against the driver's {@code complaintCount} - see its class doc.
 */
@Entity
@Table(
        name = "complaints",
        indexes = {
            @Index(name = "idx_complaints_ride_id", columnList = "ride_id"),
            @Index(name = "idx_complaints_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
public class Complaint extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "filed_by_user_id", nullable = false)
    private User filedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "filed_by_role", nullable = false, length = 10)
    private ComplaintFiledBy filedByRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ComplaintType type;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComplaintStatus status = ComplaintStatus.OPEN;

    @Column(name = "admin_notes", length = 1000)
    private String adminNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
