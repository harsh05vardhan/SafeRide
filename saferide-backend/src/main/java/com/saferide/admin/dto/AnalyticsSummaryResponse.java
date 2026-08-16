package com.saferide.admin.dto;

/** {@code GET /api/admin/analytics/summary} - every number computed by a real aggregate query, never estimated or cached. */
public record AnalyticsSummaryResponse(
        long totalRides,
        long activeRides,
        long completedRides,
        long cancelledRides,
        double totalRevenue,
        double averageRating,
        long totalUsers,
        long totalDrivers,
        long verifiedDrivers,
        long onlineDriversNow,
        long totalComplaints,
        long openComplaints,
        long resolvedComplaints,
        long dismissedComplaints,
        long activeSafetyAlerts) {
}
