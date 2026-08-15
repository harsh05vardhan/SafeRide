/** Shared across driver, vehicle, and document review - mirrors the backend's single common.VerificationStatus enum (see its Phase 3 note on why it's one enum, not three). */
export type VerificationStatus = "PENDING" | "VERIFIED" | "REJECTED";

export interface PageMeta {
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
}

export interface PageResponse<T> {
  content: T[];
  page: PageMeta;
}
