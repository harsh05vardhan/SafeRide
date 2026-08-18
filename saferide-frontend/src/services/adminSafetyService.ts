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

  /** notes is required - a resolution with no record of what actually happened isn't a real resolution. */
  async resolve(alertId: number, notes: string): Promise<SafetyAlert> {
    const response = await api.put<ApiResponse<SafetyAlert>>(`/admin/safety/alerts/${alertId}/resolve`, { notes });
    return response.data.data;
  },

  /** Only a RESOLVED alert can be deleted - the backend refuses an ACTIVE one outright. */
  async delete(alertId: number): Promise<void> {
    await api.delete(`/admin/safety/alerts/${alertId}`);
  },
};
