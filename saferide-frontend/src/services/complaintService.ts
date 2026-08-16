import { api, type ApiResponse } from "./api";
import type { PageResponse } from "@/types/common";
import type { Complaint, ComplaintRequest } from "@/types/complaint";

export const complaintService = {
  async file(rideId: number, request: ComplaintRequest): Promise<Complaint> {
    const response = await api.post<ApiResponse<Complaint>>(`/rides/${rideId}/complaints`, request);
    return response.data.data;
  },

  /** Every complaint the caller has ever filed, as either a customer or a driver. */
  async listMine(page = 0, size = 20): Promise<PageResponse<Complaint>> {
    const response = await api.get<ApiResponse<PageResponse<Complaint>>>("/complaints", { params: { page, size } });
    return response.data.data;
  },
};
