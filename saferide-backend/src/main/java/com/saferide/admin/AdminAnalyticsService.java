package com.saferide.admin;

import com.saferide.admin.dto.AdminRideSummaryResponse;
import com.saferide.admin.dto.AnalyticsSummaryResponse;
import com.saferide.admin.dto.TopDriverResponse;
import com.saferide.common.VerificationStatus;
import com.saferide.complaint.ComplaintRepository;
import com.saferide.complaint.ComplaintStatus;
import com.saferide.driver.Driver;
import com.saferide.driver.DriverOnlineStatus;
import com.saferide.driver.DriverRepository;
import com.saferide.payment.PaymentRepository;
import com.saferide.payment.PaymentStatus;
import com.saferide.rating.RatingRepository;
import com.saferide.ride.Ride;
import com.saferide.ride.RideDriverInfoFactory;
import com.saferide.ride.RideRepository;
import com.saferide.ride.TerminalRideStatuses;
import com.saferide.ride.dto.RideDriverInfo;
import com.saferide.safety.SafetyAlertRepository;
import com.saferide.safety.SafetyAlertStatus;
import com.saferide.user.UserRepository;
import com.saferide.user.dto.UserSummaryDTO;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    private final RideRepository rideRepository;
    private final PaymentRepository paymentRepository;
    private final RatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final ComplaintRepository complaintRepository;
    private final SafetyAlertRepository safetyAlertRepository;
    private final RideDriverInfoFactory rideDriverInfoFactory;

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary() {
        long totalRides = rideRepository.count();
        long completedRides = rideRepository.countByStatusIn(TerminalRideStatuses.COMPLETED);
        long cancelledRides = rideRepository.countByStatusIn(TerminalRideStatuses.CANCELLED);
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

    /** Most recently created rides, newest first - the admin dashboard's "recent rides" list. */
    @Transactional(readOnly = true)
    public List<AdminRideSummaryResponse> recentRides(int limit) {
        List<Ride> rides = rideRepository.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        // One query for every distinct driver across the whole list, not one query per row -
        // same reasoning as RideService#listForCustomer/listForDriver.
        Map<Long, RideDriverInfo> driverInfoByRideId = rideDriverInfoFactory.fromBatch(rides);
        return rides.stream().map(ride -> toRideSummary(ride, driverInfoByRideId.get(ride.getId()))).toList();
    }

    /**
     * Verified drivers ranked by rating - a bounded 5 extra completed-ride-count
     * queries here (one per leaderboard row) is fine; this is not the same
     * N+1 shape as running that per-row inside a paginated list.
     */
    @Transactional(readOnly = true)
    public List<TopDriverResponse> topDrivers() {
        List<Driver> drivers = driverRepository.findTop5ByVerificationStatusOrderByRatingDesc(VerificationStatus.VERIFIED);
        return drivers.stream()
                .map(driver -> new TopDriverResponse(
                        driver.getId(),
                        driver.getUser().getName(),
                        driver.getUser().getAvatarUrl(),
                        driver.getRating(),
                        rideRepository.countByDriverIdAndStatusIn(driver.getId(), TerminalRideStatuses.COMPLETED)))
                .toList();
    }

    private AdminRideSummaryResponse toRideSummary(Ride ride, RideDriverInfo driverInfo) {
        return new AdminRideSummaryResponse(
                ride.getId(),
                UserSummaryDTO.from(ride.getCustomer()),
                driverInfo,
                ride.getPickupAddress(),
                ride.getDestinationAddress(),
                ride.getStatus(),
                ride.getCreatedAt());
    }
}
