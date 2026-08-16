import { api, type ApiResponse } from "./api";
import type { Rating, RatingRequest } from "@/types/rating";
import type { Ride } from "@/types/ride";

export const ratingService = {
  /** Advances the ride to RATED - returns the updated ride, mirroring paymentService.pay's shape. */
  async rate(rideId: number, request: RatingRequest): Promise<Ride> {
    const response = await api.post<ApiResponse<Ride>>(`/rides/${rideId}/rating`, request);
    return response.data.data;
  },

  async getForRide(rideId: number): Promise<Rating> {
    const response = await api.get<ApiResponse<Rating>>(`/rides/${rideId}/rating`);
    return response.data.data;
  },
};
