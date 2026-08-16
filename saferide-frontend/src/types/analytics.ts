/** Mirrors com.saferide.admin.dto.AnalyticsSummaryResponse (Phase 13) - every field is a real aggregate query, never a client-side estimate. */
export interface AnalyticsSummary {
  totalRides: number;
  activeRides: number;
  completedRides: number;
  cancelledRides: number;
  totalRevenue: number;
  averageRating: number;
  totalUsers: number;
  totalDrivers: number;
  verifiedDrivers: number;
  onlineDriversNow: number;
  totalComplaints: number;
  openComplaints: number;
  resolvedComplaints: number;
  dismissedComplaints: number;
  activeSafetyAlerts: number;
}
