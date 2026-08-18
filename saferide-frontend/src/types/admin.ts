import type { UserSummary, UserStatus } from "./auth";
import type { VerificationStatus } from "./common";
import type { DriverOnlineStatus } from "./driver";
import type { DriverDocument } from "./driver";
import type { Rating } from "./rating";
import type { Vehicle } from "./vehicle";

/** Mirrors the backend's DriverAdminSummaryResponse - what an admin sees in the drivers list. */
export interface DriverAdminSummary {
  id: number;
  user: UserSummary;
  accountStatus: UserStatus;
  licenseNumber: string;
  verificationStatus: VerificationStatus;
  onlineStatus: DriverOnlineStatus;
  rating: number;
  acceptanceRate: number;
  cancellationRate: number;
  completionRate: number;
  onTimeRate: number;
  complaintCount: number;
  reliabilityScore: number;
  createdAt: string;
}

/**
 * Mirrors DriverAdminDetailResponse - everything an admin needs on one
 * screen to decide approve vs. block. completedRideCount/cancelledRideCount/
 * recentRatings are detail-only (not on DriverAdminSummary) - see that
 * DTO's own backend doc for why.
 */
export interface DriverAdminDetail {
  driver: DriverAdminSummary;
  vehicles: Vehicle[];
  documents: DriverDocument[];
  completedRideCount: number;
  cancelledRideCount: number;
  recentRatings: Rating[];
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

/** Mirrors CreateAdminRequest - the one real way to make an admin account (self-registration always refuses role: ADMIN). */
export interface CreateAdminRequest {
  name: string;
  email: string;
  phone: string;
  password: string;
}
