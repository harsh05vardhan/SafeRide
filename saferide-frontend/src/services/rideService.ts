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

  // --- Driver-side actions ---

  /** Fallback browse-and-claim for whenever proactive matching hasn't (yet) found anyone - see RideService's class doc. */
  async openRides(page = 0, size = 20): Promise<PageResponse<Ride>> {
    const response = await api.get<ApiResponse<PageResponse<Ride>>>("/rides/open", { params: { page, size } });
    return response.data.data;
  },

  /** Confirms a proactive assignment, or self-claims an open ride as a fallback - the backend decides which based on the ride's current status. */
  async accept(rideId: number): Promise<Ride> {
    const response = await api.post<ApiResponse<Ride>>(`/rides/${rideId}/accept`);
    return response.data.data;
  },

  /** Turns down a proactive assignment - permanently excludes this driver from the ride and triggers immediate reassignment. */
  async decline(rideId: number): Promise<Ride> {
    const response = await api.post<ApiResponse<Ride>>(`/rides/${rideId}/decline`);
    return response.data.data;
  },

  async arrive(rideId: number): Promise<Ride> {
    const response = await api.post<ApiResponse<Ride>>(`/rides/${rideId}/arrive`);
    return response.data.data;
  },

  async start(rideId: number, otp: string): Promise<Ride> {
    const response = await api.post<ApiResponse<Ride>>(`/rides/${rideId}/start`, { otp });
    return response.data.data;
  },

  async complete(rideId: number): Promise<Ride> {
    const response = await api.post<ApiResponse<Ride>>(`/rides/${rideId}/complete`);
    return response.data.data;
  },
};
