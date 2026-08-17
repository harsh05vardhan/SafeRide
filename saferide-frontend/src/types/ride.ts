import type { UserSummary } from "./auth";
export type { PageResponse } from "./common";

export type VehicleType = "MINI" | "SEDAN" | "SUV";

export type RideStatus =
  | "REQUESTED"
  | "SEARCHING_DRIVER"
  | "DRIVER_ASSIGNED"
  | "DRIVER_ACCEPTED"
  | "DRIVER_ARRIVED"
  | "RIDE_STARTED"
  | "RIDE_COMPLETED"
  | "PAYMENT_PENDING"
  | "PAYMENT_COMPLETED"
  | "RATED"
  | "CANCELLED"
  | "DRIVER_CANCELLED";

export interface RideDriverInfo {
  driverId: number;
  name: string;
  phone: string;
  rating: number;
  vehicleModel: string | null;
  vehicleNumber: string | null;
  vehicleColor: string | null;
}

export type RideCancellationReason = "CUSTOMER" | "DRIVER" | "NO_DRIVER_FOUND";

export interface Ride {
  id: number;
  customer: UserSummary;
  driver: RideDriverInfo | null;
  vehicleType: VehicleType;
  pickupAddress: string;
  destinationAddress: string;
  pickupLatitude: number;
  pickupLongitude: number;
  destinationLatitude: number;
  destinationLongitude: number;
  distanceKm: number;
  estimatedDurationMinutes: number;
  status: RideStatus;
  otp?: string | null;
  createdAt: string;
  acceptedAt?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  cancelledAt?: string | null;
  /** Only meaningful once status is CANCELLED/DRIVER_CANCELLED - "no driver was available in time" vs a customer/driver-initiated cancel. */
  cancellationReason?: RideCancellationReason | null;
  /** Only meaningful while status is SEARCHING_DRIVER - when the current search attempt started, so the countdown survives a page reload. */
  searchingSince?: string | null;
}

export interface RideEstimateRequest {
  pickupLatitude: number;
  pickupLongitude: number;
  destinationLatitude: number;
  destinationLongitude: number;
}

export interface RideEstimateResponse {
  distanceKm: number;
  estimatedDurationMinutes: number;
}

export interface RideCreateRequest {
  pickupAddress: string;
  destinationAddress: string;
  pickupLatitude: number;
  pickupLongitude: number;
  destinationLatitude: number;
  destinationLongitude: number;
  vehicleType: VehicleType;
}

export interface RideDestinationChangeRequest {
  destinationAddress: string;
  destinationLatitude: number;
  destinationLongitude: number;
}

/** Non-terminal ride statuses still being actively tracked on the live ride screen. */
export const ACTIVE_TRACKING_STATUSES: RideStatus[] = [
  "REQUESTED",
  "SEARCHING_DRIVER",
  "DRIVER_ASSIGNED",
  "DRIVER_ACCEPTED",
  "DRIVER_ARRIVED",
  "RIDE_STARTED",
];

/** Mirrors the backend's ActiveRideStatuses.DRIVER - a driver with a ride in one of these is "busy" / has a current ride. */
export const ACTIVE_DRIVER_RIDE_STATUSES: RideStatus[] = [
  "DRIVER_ASSIGNED",
  "DRIVER_ACCEPTED",
  "DRIVER_ARRIVED",
  "RIDE_STARTED",
];
