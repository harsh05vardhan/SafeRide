import { api, type ApiResponse } from "./api";
import type { PublicTrip, SafetyAlert, SosRequest, TripShare } from "@/types/safety";

export const safetyService = {
  async triggerSos(rideId: number, request: SosRequest): Promise<SafetyAlert> {
    const response = await api.post<ApiResponse<SafetyAlert>>(`/rides/${rideId}/sos`, request);
    return response.data.data;
  },

  /** Returns the same still-valid token on repeat calls for one ride, rather than minting a new one each time. */
  async getOrCreateShareLink(rideId: number): Promise<TripShare> {
    const response = await api.post<ApiResponse<TripShare>>(`/rides/${rideId}/share`);
    return response.data.data;
  },

  /** Unauthenticated on purpose - the token itself is what guards this, not a login. Safe to call with no session at all. */
  async getPublicTrip(token: string): Promise<PublicTrip> {
    const response = await api.get<ApiResponse<PublicTrip>>(`/public/trips/${token}`);
    return response.data.data;
  },
};
