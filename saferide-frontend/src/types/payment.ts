export type PaymentStatus = "PENDING" | "COMPLETED" | "FAILED";

export interface Payment {
  rideId: number;
  amount: number;
  currency: string;
  status: PaymentStatus;
  transactionReference: string | null;
  paidAt: string | null;
}
