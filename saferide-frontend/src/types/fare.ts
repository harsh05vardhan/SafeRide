import type { VehicleType } from "./ride";

export type FareLockStatus = "LOCKED" | "UNLOCKED";

export interface FareCalculateRequest {
  distanceKm: number;
  estimatedDurationMinutes: number;
  vehicleType: VehicleType;
  discount?: number;
}

export interface FareBreakdown {
  baseFare: number;
  distanceFare: number;
  timeFare: number;
  platformFee: number;
  discount: number;
  totalFare: number;
  currency: string;
}

export interface RideFare extends FareBreakdown {
  rideId: number;
  lockStatus: FareLockStatus | null;
  lockedAmount: number | null;
  lockedAt: string | null;
  unlockedAt: string | null;
}
