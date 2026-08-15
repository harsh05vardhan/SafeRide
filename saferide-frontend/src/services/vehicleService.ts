import { api, type ApiResponse } from "./api";
import type { Vehicle, VehicleRegisterRequest } from "@/types/vehicle";

export const vehicleService = {
  async register(request: VehicleRegisterRequest): Promise<Vehicle> {
    const response = await api.post<ApiResponse<Vehicle>>("/vehicles", request);
    return response.data.data;
  },

  async list(): Promise<Vehicle[]> {
    const response = await api.get<ApiResponse<Vehicle[]>>("/vehicles");
    return response.data.data;
  },

  async activate(vehicleId: number): Promise<Vehicle> {
    const response = await api.put<ApiResponse<Vehicle>>(`/vehicles/${vehicleId}/activate`);
    return response.data.data;
  },
};
