package com.saferide.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.saferide.admin.dto.AnalyticsSummaryResponse;
import com.saferide.common.VerificationStatus;
import com.saferide.complaint.ComplaintRepository;
import com.saferide.complaint.ComplaintStatus;
import com.saferide.driver.DriverOnlineStatus;
import com.saferide.driver.DriverRepository;
import com.saferide.payment.PaymentRepository;
import com.saferide.payment.PaymentStatus;
import com.saferide.rating.RatingRepository;
import com.saferide.ride.RideDriverInfoFactory;
import com.saferide.ride.RideRepository;
import com.saferide.safety.SafetyAlertRepository;
import com.saferide.safety.SafetyAlertStatus;
import com.saferide.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Every number in {@link AnalyticsSummaryResponse} traces back to exactly
 * one repository call - this pins that wiring down with fully controlled
 * mock values, complementary to {@code AdminAnalyticsIntegrationTest}'s
 * real-database check (which can only assert numbers move in the right
 * direction, since it shares the H2 instance with the rest of the suite).
 */
@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceTest {

    @Mock private RideRepository rideRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RatingRepository ratingRepository;
    @Mock private UserRepository userRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private ComplaintRepository complaintRepository;
    @Mock private SafetyAlertRepository safetyAlertRepository;
    @Mock private RideDriverInfoFactory rideDriverInfoFactory;

    private AdminAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AdminAnalyticsService(rideRepository, paymentRepository, ratingRepository,
                userRepository, driverRepository, complaintRepository, safetyAlertRepository, rideDriverInfoFactory);
    }

    @Test
    void summaryComputesActiveRidesAsTheRemainderAndWiresEveryFieldToItsOwnQuery() {
        when(rideRepository.count()).thenReturn(100L);
        // First countByStatusIn call is for the completed-status set, second for cancelled.
        when(rideRepository.countByStatusIn(any())).thenReturn(60L, 15L);
        when(paymentRepository.sumAmountByStatus(PaymentStatus.COMPLETED)).thenReturn(123456.75);
        when(ratingRepository.averageScore()).thenReturn(4.6);
        when(userRepository.count()).thenReturn(80L);
        when(driverRepository.count()).thenReturn(20L);
        when(driverRepository.countByVerificationStatus(VerificationStatus.VERIFIED)).thenReturn(18L);
        when(driverRepository.countByOnlineStatus(DriverOnlineStatus.ONLINE)).thenReturn(7L);
        when(complaintRepository.count()).thenReturn(9L);
        when(complaintRepository.countByStatus(ComplaintStatus.OPEN)).thenReturn(2L);
        when(complaintRepository.countByStatus(ComplaintStatus.UNDER_REVIEW)).thenReturn(1L);
        when(complaintRepository.countByStatus(ComplaintStatus.RESOLVED)).thenReturn(5L);
        when(complaintRepository.countByStatus(ComplaintStatus.DISMISSED)).thenReturn(1L);
        when(safetyAlertRepository.countByStatus(SafetyAlertStatus.ACTIVE)).thenReturn(3L);

        AnalyticsSummaryResponse summary = service.summary();

        assertThat(summary.totalRides()).isEqualTo(100L);
        assertThat(summary.completedRides()).isEqualTo(60L);
        assertThat(summary.cancelledRides()).isEqualTo(15L);
        assertThat(summary.activeRides()).isEqualTo(25L); // 100 - 60 - 15, never re-derived any other way
        assertThat(summary.totalRevenue()).isEqualTo(123456.75);
        assertThat(summary.averageRating()).isEqualTo(4.6);
        assertThat(summary.totalUsers()).isEqualTo(80L);
        assertThat(summary.totalDrivers()).isEqualTo(20L);
        assertThat(summary.verifiedDrivers()).isEqualTo(18L);
        assertThat(summary.onlineDriversNow()).isEqualTo(7L);
        assertThat(summary.totalComplaints()).isEqualTo(9L);
        assertThat(summary.openComplaints()).isEqualTo(3L); // OPEN + UNDER_REVIEW, summed rather than a single query
        assertThat(summary.resolvedComplaints()).isEqualTo(5L);
        assertThat(summary.dismissedComplaints()).isEqualTo(1L);
        assertThat(summary.activeSafetyAlerts()).isEqualTo(3L);
    }

    @Test
    void activeRidesIsNeverNegativeEvenIfEveryRideIsAlreadyTerminal() {
        when(rideRepository.count()).thenReturn(10L);
        when(rideRepository.countByStatusIn(any())).thenReturn(7L, 3L); // completed + cancelled == total
        when(paymentRepository.sumAmountByStatus(any())).thenReturn(0.0);
        when(ratingRepository.averageScore()).thenReturn(0.0);

        AnalyticsSummaryResponse summary = service.summary();

        assertThat(summary.activeRides()).isZero();
    }

    // Keeps the mocked countByStatusIn(any()) stub above honest about which query is completed vs cancelled -
    // if AdminAnalyticsService ever reordered those two calls, this would catch it via Mockito's consecutive-return ordering.
    @Test
    void completedAndCancelledCountsComeFromDistinctQueriesInDeclaredOrder() {
        when(rideRepository.count()).thenReturn(50L);
        when(rideRepository.countByStatusIn(any())).thenReturn(30L, 5L);
        when(paymentRepository.sumAmountByStatus(any())).thenReturn(0.0);
        when(ratingRepository.averageScore()).thenReturn(0.0);

        AnalyticsSummaryResponse summary = service.summary();

        assertThat(summary.completedRides()).isEqualTo(30L);
        assertThat(summary.cancelledRides()).isEqualTo(5L);
    }
}
