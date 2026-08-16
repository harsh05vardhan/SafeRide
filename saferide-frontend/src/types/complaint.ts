import type { UserSummary } from "./auth";

export type ComplaintType = "EXTRA_FARE_DEMANDED" | "UNSAFE_DRIVING" | "RUDE_BEHAVIOR" | "VEHICLE_MISMATCH" | "OTHER";
export type ComplaintStatus = "OPEN" | "UNDER_REVIEW" | "RESOLVED" | "DISMISSED";
export type ComplaintFiledBy = "CUSTOMER" | "DRIVER";

export interface Complaint {
  id: number;
  rideId: number;
  filedBy: UserSummary;
  filedByRole: ComplaintFiledBy;
  type: ComplaintType;
  description: string;
  status: ComplaintStatus;
  adminNotes: string | null;
  createdAt: string;
  resolvedAt: string | null;
}

export interface ComplaintRequest {
  type: ComplaintType;
  description: string;
}
