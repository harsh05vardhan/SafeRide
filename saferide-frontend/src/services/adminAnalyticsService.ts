import { api, type ApiResponse } from "./api";
import type { AnalyticsSummary } from "@/types/analytics";
import type { AdminRideSummary, TopDriver } from "@/types/admin";

export const adminAnalyticsService = {
  async getSummary(): Promise<AnalyticsSummary> {
    const response = await api.get<ApiResponse<AnalyticsSummary>>("/admin/analytics/summary");
    return response.data.data;
  },

  async recentRides(limit = 5): Promise<AdminRideSummary[]> {
    const response = await api.get<ApiResponse<AdminRideSummary[]>>("/admin/analytics/recent-rides", { params: { limit } });
    return response.data.data;
  },

  async topDrivers(): Promise<TopDriver[]> {
    const response = await api.get<ApiResponse<TopDriver[]>>("/admin/analytics/top-drivers");
    return response.data.data;
  },
};
