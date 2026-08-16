import type { UserSummary } from "./auth";
import type { RideStatus } from "./ride";

export type SafetyAlertStatus = "ACTIVE" | "RESOLVED";

export interface SafetyAlert {
  id: number;
  rideId: number;
  triggeredBy: UserSummary;
  latitude: number;
  longitude: number;
  status: SafetyAlertStatus;
  createdAt: string;
  resolvedAt: string | null;
}

export interface SosRequest {
  latitude: number;
  longitude: number;
}

export interface TripShare {
  shareToken: string;
  expiresAt: string;
}

/** What GET /api/public/trips/{token} returns - no OTP, no phone numbers, nothing that identifies either party beyond a name. */
export interface PublicTrip {
  status: RideStatus;
  pickupAddress: string;
  destinationAddress: string;
  pickupLatitude: number;
  pickupLongitude: number;
  destinationLatitude: number;
  destinationLongitude: number;
  driverName: string | null;
  vehicleModel: string | null;
  vehicleNumber: string | null;
}
