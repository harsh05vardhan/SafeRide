import { api, type ApiResponse } from "./api";
import type {
  PageResponse,
  Ride,
  RideCreateRequest,
  RideDestinationChangeRequest,
  RideEstimateRequest,
  RideEstimateResponse,
} from "@/types/ride";

export const rideService = {
  async estimate(request: RideEstimateRequest): Promise<RideEstimateResponse> {
    const response = await api.post<ApiResponse<RideEstimateResponse>>("/rides/estimate", request);
    return response.data.data;
  },

  async create(request: RideCreateRequest): Promise<Ride> {
    const response = await api.post<ApiResponse<Ride>>("/rides", request);
    return response.data.data;
  },

  async get(rideId: number): Promise<Ride> {
    const response = await api.get<ApiResponse<Ride>>(`/rides/${rideId}`);
    return response.data.data;
  },

  async myRides(page = 0, size = 20): Promise<PageResponse<Ride>> {
    const response = await api.get<ApiResponse<PageResponse<Ride>>>("/rides", { params: { page, size } });
    return response.data.data;
  },

  async changeDestination(rideId: number, request: RideDestinationChangeRequest): Promise<Ride> {
    const response = await api.put<ApiResponse<Ride>>(`/rides/${rideId}/destination`, request);
    return response.data.data;
  },

  async cancel(rideId: number): Promise<Ride> {
    const response = await api.post<ApiResponse<Ride>>(`/rides/${rideId}/cancel`);
    return response.data.data;
  },
};
