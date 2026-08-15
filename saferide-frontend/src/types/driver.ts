import type { UserSummary } from "./auth";
import type { VerificationStatus } from "./common";

export type DriverOnlineStatus = "ONLINE" | "OFFLINE";

export interface DriverProfile {
  id: number;
  user: UserSummary;
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
  currentLatitude: number | null;
  currentLongitude: number | null;
}

export interface DriverStatusUpdateRequest {
  onlineStatus: DriverOnlineStatus;
}

export interface DriverLocationUpdateRequest {
  latitude: number;
  longitude: number;
}

export type DocumentType = "DRIVING_LICENSE" | "VEHICLE_REGISTRATION" | "INSURANCE" | "IDENTITY_PROOF" | "PROFILE_PHOTO";

export interface DriverDocument {
  id: number;
  driverId: number;
  documentType: DocumentType;
  documentUrl: string;
  verificationStatus: VerificationStatus;
  uploadedAt: string;
  verifiedAt: string | null;
}

export interface DriverDocumentUploadRequest {
  documentType: DocumentType;
  documentUrl: string;
}
