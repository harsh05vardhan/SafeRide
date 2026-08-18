import { api, type ApiResponse } from "./api";
import type { AdminUserSummary, CreateAdminRequest } from "@/types/admin";
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

  /** The only real way to create an admin - self-registration always refuses role: ADMIN. */
  async createAdmin(request: CreateAdminRequest): Promise<AdminUserSummary> {
    const response = await api.post<ApiResponse<AdminUserSummary>>("/admin/users/admins", request);
    return response.data.data;
  },
};
