import { api, type ApiResponse } from "./api";
import type { AdminActionRequest, DriverAdminDetail, DriverAdminSummary } from "@/types/admin";
import type { PageResponse } from "@/types/common";
import type { VerificationStatus } from "@/types/common";
import type { DriverDocument } from "@/types/driver";
import type { Vehicle } from "@/types/vehicle";

export const adminService = {
  async listDrivers(status?: VerificationStatus, page = 0, size = 20): Promise<PageResponse<DriverAdminSummary>> {
    const response = await api.get<ApiResponse<PageResponse<DriverAdminSummary>>>("/admin/drivers", {
      params: { status, page, size },
    });
    return response.data.data;
  },

  async getDriver(driverId: number): Promise<DriverAdminDetail> {
    const response = await api.get<ApiResponse<DriverAdminDetail>>(`/admin/drivers/${driverId}`);
    return response.data.data;
  },

  async approveDriver(driverId: number): Promise<DriverAdminSummary> {
    const response = await api.put<ApiResponse<DriverAdminSummary>>(`/admin/drivers/${driverId}/approve`);
    return response.data.data;
  },

  async blockDriver(driverId: number, request?: AdminActionRequest): Promise<DriverAdminSummary> {
    const response = await api.put<ApiResponse<DriverAdminSummary>>(`/admin/drivers/${driverId}/block`, request);
    return response.data.data;
  },

  async unblockDriver(driverId: number): Promise<DriverAdminSummary> {
    const response = await api.put<ApiResponse<DriverAdminSummary>>(`/admin/drivers/${driverId}/unblock`);
    return response.data.data;
  },

  async approveVehicle(vehicleId: number): Promise<Vehicle> {
    const response = await api.put<ApiResponse<Vehicle>>(`/admin/drivers/vehicles/${vehicleId}/approve`);
    return response.data.data;
  },

  async rejectVehicle(vehicleId: number, request?: AdminActionRequest): Promise<Vehicle> {
    const response = await api.put<ApiResponse<Vehicle>>(`/admin/drivers/vehicles/${vehicleId}/reject`, request);
    return response.data.data;
  },

  async approveDocument(documentId: number): Promise<DriverDocument> {
    const response = await api.put<ApiResponse<DriverDocument>>(`/admin/drivers/documents/${documentId}/approve`);
    return response.data.data;
  },

  async rejectDocument(documentId: number, request?: AdminActionRequest): Promise<DriverDocument> {
    const response = await api.put<ApiResponse<DriverDocument>>(`/admin/drivers/documents/${documentId}/reject`, request);
    return response.data.data;
  },
};
