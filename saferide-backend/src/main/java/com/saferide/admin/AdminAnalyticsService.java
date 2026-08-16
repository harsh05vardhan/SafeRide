package com.saferide.admin;

import com.saferide.admin.dto.AnalyticsSummaryResponse;
import com.saferide.common.VerificationStatus;
import com.saferide.complaint.ComplaintRepository;
import com.saferide.complaint.ComplaintStatus;
import com.saferide.driver.DriverOnlineStatus;
import com.saferide.driver.DriverRepository;
import com.saferide.payment.PaymentRepository;
import com.saferide.payment.PaymentStatus;
import com.saferide.rating.RatingRepository;
import com.saferide.ride.RideRepository;
import com.saferide.ride.RideStatus;
import com.saferide.safety.SafetyAlertRepository;
import com.saferide.safety.SafetyAlertStatus;
import com.saferide.user.UserRepository;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only aggregate stats for {@code /admin/analytics} - every field is a
 * real query (a {@code COUNT}, {@code SUM}, or {@code AVG}), never an
 * estimate, a cached snapshot, or something computed by loading full rows
 * into Java and summing them in memory. The one deliberate simplification:
 * {@code totalUsers} is every account regardless of role, since splitting
 * it by role would need a join against {@code User.roles} this phase
 * didn't need for anything else - {@code totalDrivers} already answers
 * "how many are drivers" on its own.
 */
@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    private static final Set<RideStatus> COMPLETED_STATUSES =
            EnumSet.of(RideStatus.RIDE_COMPLETED, RideStatus.PAYMENT_PENDING, RideStatus.PAYMENT_COMPLETED, RideStatus.RATED);
    private static final Set<RideStatus> CANCELLED_STATUSES =
            EnumSet.of(RideStatus.CANCELLED, RideStatus.DRIVER_CANCELLED);

    private final RideRepository rideRepository;
    private final PaymentRepository paymentRepository;
    private final RatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final ComplaintRepository complaintRepository;
    private final SafetyAlertRepository safetyAlertRepository;

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary() {
        long totalRides = rideRepository.count();
        long completedRides = rideRepository.countByStatusIn(COMPLETED_STATUSES);
        long cancelledRides = rideRepository.countByStatusIn(CANCELLED_STATUSES);
        long activeRides = totalRides - completedRides - cancelledRides;

        return new AnalyticsSummaryResponse(
                totalRides,
                activeRides,
                completedRides,
                cancelledRides,
                paymentRepository.sumAmountByStatus(PaymentStatus.COMPLETED),
                ratingRepository.averageScore(),
                userRepository.count(),
                driverRepository.count(),
                driverRepository.countByVerificationStatus(VerificationStatus.VERIFIED),
                driverRepository.countByOnlineStatus(DriverOnlineStatus.ONLINE),
                complaintRepository.count(),
                complaintRepository.countByStatus(ComplaintStatus.OPEN) + complaintRepository.countByStatus(ComplaintStatus.UNDER_REVIEW),
                complaintRepository.countByStatus(ComplaintStatus.RESOLVED),
                complaintRepository.countByStatus(ComplaintStatus.DISMISSED),
                safetyAlertRepository.countByStatus(SafetyAlertStatus.ACTIVE));
    }
}
