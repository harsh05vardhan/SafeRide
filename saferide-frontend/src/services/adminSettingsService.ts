import { api, type ApiResponse } from "./api";
import type { PlatformSettings } from "@/types/settings";

export const adminSettingsService = {
  /** Pass "" to intentionally clear the number - the backend treats a blank string the same as null. */
  async updateSupportContact(supportPhoneNumber: string): Promise<PlatformSettings> {
    const response = await api.put<ApiResponse<PlatformSettings>>("/admin/settings/support-contact", { supportPhoneNumber });
    return response.data.data;
  },
};
