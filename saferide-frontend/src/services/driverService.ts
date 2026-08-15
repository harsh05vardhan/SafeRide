import { api, type ApiResponse } from "./api";
import type {
  DriverDocument,
  DriverDocumentUploadRequest,
  DriverLocationUpdateRequest,
  DriverProfile,
  DriverStatusUpdateRequest,
} from "@/types/driver";
import type { PageResponse } from "@/types/common";
import type { Ride } from "@/types/ride";

export const driverService = {
  async getProfile(): Promise<DriverProfile> {
    const response = await api.get<ApiResponse<DriverProfile>>("/drivers/profile");
    return response.data.data;
  },

  async register(licenseNumber: string): Promise<DriverProfile> {
    const response = await api.post<ApiResponse<DriverProfile>>("/drivers/register", { licenseNumber });
    return response.data.data;
  },

  async updateStatus(request: DriverStatusUpdateRequest): Promise<DriverProfile> {
    const response = await api.put<ApiResponse<DriverProfile>>("/drivers/status", request);
    return response.data.data;
  },

  async updateLocation(request: DriverLocationUpdateRequest): Promise<DriverProfile> {
    const response = await api.put<ApiResponse<DriverProfile>>("/drivers/location", request);
    return response.data.data;
  },

  async uploadDocument(request: DriverDocumentUploadRequest): Promise<DriverDocument> {
    const response = await api.post<ApiResponse<DriverDocument>>("/drivers/documents", request);
    return response.data.data;
  },

  async listDocuments(): Promise<DriverDocument[]> {
    const response = await api.get<ApiResponse<DriverDocument[]>>("/drivers/documents");
    return response.data.data;
  },

  async myRides(page = 0, size = 20): Promise<PageResponse<Ride>> {
    const response = await api.get<ApiResponse<PageResponse<Ride>>>("/drivers/rides", { params: { page, size } });
    return response.data.data;
  },
};
