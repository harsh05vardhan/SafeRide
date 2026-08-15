import type { VerificationStatus } from "./common";
import type { VehicleType } from "./ride";

export interface Vehicle {
  id: number;
  driverId: number;
  vehicleType: VehicleType;
  vehicleModel: string;
  vehicleNumber: string;
  vehicleColor: string;
  seatCapacity: number;
  verificationStatus: VerificationStatus;
  active: boolean;
}

export interface VehicleRegisterRequest {
  vehicleType: VehicleType;
  vehicleModel: string;
  vehicleNumber: string;
  vehicleColor: string;
  seatCapacity: number;
}
