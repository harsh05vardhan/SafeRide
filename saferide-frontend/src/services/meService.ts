import { api, type ApiResponse } from "./api";
import type { UserProfile } from "@/types/auth";

/** Role-agnostic "my own account" actions - a photo is the same concept for a customer, driver, or admin, so this isn't split per-role the way name/phone editing is. */
export const meService = {
  /** Pass "" to clear the photo back to the initial-letter fallback. */
  async updateAvatar(avatarUrl: string): Promise<UserProfile> {
    const response = await api.put<ApiResponse<UserProfile>>("/me/avatar", { avatarUrl });
    return response.data.data;
  },
};
