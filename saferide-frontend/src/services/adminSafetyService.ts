import { api, type ApiResponse } from "./api";
import type { PageResponse } from "@/types/common";
import type { SafetyAlert, SafetyAlertStatus } from "@/types/safety";

export const adminSafetyService = {
  async list(status?: SafetyAlertStatus, page = 0, size = 20): Promise<PageResponse<SafetyAlert>> {
    const response = await api.get<ApiResponse<PageResponse<SafetyAlert>>>("/admin/safety/alerts", {
      params: { status, page, size },
    });
    return response.data.data;
  },

  async resolve(alertId: number): Promise<SafetyAlert> {
    const response = await api.put<ApiResponse<SafetyAlert>>(`/admin/safety/alerts/${alertId}/resolve`);
    return response.data.data;
  },
};
