import type { UserSummary } from "./auth";
import type { RideDriverInfo, RideStatus } from "./ride";

export type SafetyAlertStatus = "ACTIVE" | "RESOLVED";

/**
 * Mirrors the backend's SafetyAlertResponse - everything an admin needs to
 * resolve an SOS without leaving this one screen: the full ride it
 * happened on (both customer and driver, pickup/destination, current
 * status), not just a bare ride id. resolvedBy/resolutionNotes are null
 * until resolved.
 */
export interface SafetyAlert {
  id: number;
  rideId: number;
  rideStatus: RideStatus;
  pickupAddress: string;
  destinationAddress: string;
  customer: UserSummary;
  driver?: RideDriverInfo | null;
  triggeredBy: UserSummary;
  latitude: number;
  longitude: number;
  status: SafetyAlertStatus;
  createdAt: string;
  // Jackson omits these entirely (non_null inclusion) rather than sending an explicit null while
  // an alert is still ACTIVE - optional, not just nullable, to match what actually arrives.
  resolvedBy?: UserSummary | null;
  resolvedAt?: string | null;
  resolutionNotes?: string | null;
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
