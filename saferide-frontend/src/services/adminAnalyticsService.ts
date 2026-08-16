import { api, type ApiResponse } from "./api";
import type { AnalyticsSummary } from "@/types/analytics";

export const adminAnalyticsService = {
  async getSummary(): Promise<AnalyticsSummary> {
    const response = await api.get<ApiResponse<AnalyticsSummary>>("/admin/analytics/summary");
    return response.data.data;
  },
};
