import { api, type ApiResponse } from "./api";
import type { Notification } from "@/types/notification";
import type { PageResponse } from "@/types/common";

export const notificationService = {
  async listMine(unreadOnly = false, page = 0, size = 20): Promise<PageResponse<Notification>> {
    const response = await api.get<ApiResponse<PageResponse<Notification>>>("/notifications", {
      params: { unreadOnly, page, size },
    });
    return response.data.data;
  },

  async unreadCount(): Promise<number> {
    const response = await api.get<ApiResponse<number>>("/notifications/unread-count");
    return response.data.data;
  },

  async markAsRead(id: number): Promise<Notification> {
    const response = await api.put<ApiResponse<Notification>>(`/notifications/${id}/read`);
    return response.data.data;
  },

  async markAllAsRead(): Promise<void> {
    await api.put("/notifications/read-all");
  },
};
