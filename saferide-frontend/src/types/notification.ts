/** Mirrors the backend's com.saferide.notification.NotificationType enum (Phase 13). */
export type NotificationType =
  | "RIDE_REQUESTED"
  | "DRIVER_ASSIGNED"
  | "RIDE_COMPLETED"
  | "PAYMENT_COMPLETED"
  | "COMPLAINT_FILED"
  | "COMPLAINT_RESOLVED";

export interface Notification {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  rideId: number | null;
  read: boolean;
  createdAt: string;
}
