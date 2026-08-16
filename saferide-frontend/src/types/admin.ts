import type { UserSummary, UserStatus } from "./auth";
import type { VerificationStatus } from "./common";
import type { DriverOnlineStatus } from "./driver";
import type { DriverDocument } from "./driver";
import type { Vehicle } from "./vehicle";

/** Mirrors the backend's DriverAdminSummaryResponse - what an admin sees in the drivers list. */
export interface DriverAdminSummary {
  id: number;
  user: UserSummary;
  accountStatus: UserStatus;
  licenseNumber: string;
  verificationStatus: VerificationStatus;
  onlineStatus: DriverOnlineStatus;
  reliabilityScore: number;
  createdAt: string;
}

/** Mirrors DriverAdminDetailResponse - everything an admin needs on one screen to decide approve vs. block. */
export interface DriverAdminDetail {
  driver: DriverAdminSummary;
  vehicles: Vehicle[];
  documents: DriverDocument[];
}

export interface AdminActionRequest {
  reason?: string;
}

/** Mirrors AdminUserSummaryResponse - every account regardless of role, for /admin/users. */
export interface AdminUserSummary {
  user: UserSummary;
  status: UserStatus;
  createdAt: string;
}
