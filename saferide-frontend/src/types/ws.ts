import type { RideStatus } from "./ride";

/** Mirrors the backend's RideStatusEvent - broadcast to /topic/rides/{id}/status on every transition. */
export interface RideStatusEvent {
  rideId: number;
  status: RideStatus;
  updatedAt: string;
}

/** Mirrors DriverLocationEvent - broadcast to /topic/rides/{id}/location while a ride is active. */
export interface DriverLocationEvent {
  rideId: number;
  driverId: number;
  latitude: number;
  longitude: number;
  recordedAt: string;
}

/** Mirrors RideAssignmentEvent - sent privately to /user/queue/assignments when matching proactively assigns a driver. */
export interface RideAssignmentEvent {
  rideId: number;
  status: RideStatus;
}
