import { api, type ApiResponse } from "./api";
import type { AdminUserSummary } from "@/types/admin";
import type { RoleName } from "@/types/auth";
import type { PageResponse } from "@/types/common";

export const adminUserService = {
  async list(role?: RoleName, page = 0, size = 20): Promise<PageResponse<AdminUserSummary>> {
    const response = await api.get<ApiResponse<PageResponse<AdminUserSummary>>>("/admin/users", {
      params: { role, page, size },
    });
    return response.data.data;
  },

  /** Soft delete - the account can never log in again, but nothing about it is physically removed (see the backend's UserStatus.DELETED doc). */
  async delete(userId: number): Promise<void> {
    await api.delete(`/admin/users/${userId}`);
  },
};
