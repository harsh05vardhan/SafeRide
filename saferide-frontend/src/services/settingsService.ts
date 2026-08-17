import { api, type ApiResponse } from "./api";
import type { PlatformSettings } from "@/types/settings";

/** Public read of platform-wide settings - reachable even when logged out, so a just-blocked account failing at the login screen can still learn how to reach support. */
export const settingsService = {
  async getSupportContact(): Promise<PlatformSettings> {
    const response = await api.get<ApiResponse<PlatformSettings>>("/settings/support-contact");
    return response.data.data;
  },
};
