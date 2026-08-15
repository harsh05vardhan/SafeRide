import { CheckCircle2, Clock, XCircle, Ban } from "lucide-react";
import { Badge } from "@/components/common/Badge";
import type { VerificationStatus } from "@/types/common";
import type { UserStatus } from "@/types/auth";

/** Shared badge rendering for the one VerificationStatus enum the backend reuses across DRIVER, VEHICLE and DRIVER_DOCUMENT - see common/VerificationStatus.java. */
export function VerificationBadge({ status }: { status: VerificationStatus }) {
  switch (status) {
    case "VERIFIED":
      return (
        <Badge variant="success">
          <CheckCircle2 size={12} /> Verified
        </Badge>
      );
    case "REJECTED":
      return (
        <Badge variant="danger">
          <XCircle size={12} /> Rejected
        </Badge>
      );
    default:
      return (
        <Badge variant="primary">
          <Clock size={12} /> Pending
        </Badge>
      );
  }
}

export function AccountStatusBadge({ status }: { status: UserStatus }) {
  return status === "BLOCKED" ? (
    <Badge variant="danger">
      <Ban size={12} /> Blocked
    </Badge>
  ) : (
    <Badge variant="success">Active</Badge>
  );
}
