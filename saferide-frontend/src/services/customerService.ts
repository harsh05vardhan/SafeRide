import { api, type ApiResponse } from "./api";
import type { UpdateProfileRequest, UserProfile } from "@/types/auth";

export const customerService = {
  async getProfile(): Promise<UserProfile> {
    const response = await api.get<ApiResponse<UserProfile>>("/customers/profile");
    return response.data.data;
  },

  async updateProfile(request: UpdateProfileRequest): Promise<UserProfile> {
    const response = await api.put<ApiResponse<UserProfile>>("/customers/profile", request);
    return response.data.data;
  },
};
