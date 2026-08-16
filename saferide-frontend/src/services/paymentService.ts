import { api, type ApiResponse } from "./api";
import type { Payment } from "@/types/payment";
import type { Ride } from "@/types/ride";

export const paymentService = {
  /** Charges the ride's locked fare and advances it to PAYMENT_COMPLETED - returns the updated ride, not the payment itself (mirrors POST /rides/{id}/fare/confirm's shape). */
  async pay(rideId: number): Promise<Ride> {
    const response = await api.post<ApiResponse<Ride>>(`/rides/${rideId}/pay`);
    return response.data.data;
  },

  async getForRide(rideId: number): Promise<Payment> {
    const response = await api.get<ApiResponse<Payment>>(`/rides/${rideId}/payment`);
    return response.data.data;
  },
};
