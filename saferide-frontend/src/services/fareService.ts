import { api, type ApiResponse } from "./api";
import type { FareBreakdown, FareCalculateRequest, RideFare } from "@/types/fare";

export const fareService = {
  async calculate(request: FareCalculateRequest): Promise<FareBreakdown> {
    const response = await api.post<ApiResponse<FareBreakdown>>("/fare/calculate", request);
    return response.data.data;
  },

  async getRideFare(rideId: number): Promise<RideFare> {
    const response = await api.get<ApiResponse<RideFare>>(`/rides/${rideId}/fare`);
    return response.data.data;
  },

  async confirmFare(rideId: number): Promise<RideFare> {
    const response = await api.post<ApiResponse<RideFare>>(`/rides/${rideId}/fare/confirm`);
    return response.data.data;
  },
};
