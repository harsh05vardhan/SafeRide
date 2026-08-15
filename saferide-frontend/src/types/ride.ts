import type { UserSummary } from "./auth";

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

export interface PageResponse<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
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
