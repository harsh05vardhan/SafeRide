import { api, type ApiResponse } from "./api";
import type { AdminActionRequest } from "@/types/admin";
import type { PageResponse } from "@/types/common";
import type { Complaint, ComplaintStatus } from "@/types/complaint";

export const adminComplaintService = {
  async list(status?: ComplaintStatus, page = 0, size = 20): Promise<PageResponse<Complaint>> {
    const response = await api.get<ApiResponse<PageResponse<Complaint>>>("/admin/complaints", {
      params: { status, page, size },
    });
    return response.data.data;
  },

  async get(complaintId: number): Promise<Complaint> {
    const response = await api.get<ApiResponse<Complaint>>(`/admin/complaints/${complaintId}`);
    return response.data.data;
  },

  async resolve(complaintId: number, request?: AdminActionRequest): Promise<Complaint> {
    const response = await api.put<ApiResponse<Complaint>>(`/admin/complaints/${complaintId}/resolve`, request);
    return response.data.data;
  },

  async dismiss(complaintId: number, request?: AdminActionRequest): Promise<Complaint> {
    const response = await api.put<ApiResponse<Complaint>>(`/admin/complaints/${complaintId}/dismiss`, request);
    return response.data.data;
  },
};
