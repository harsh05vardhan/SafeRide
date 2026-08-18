import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CheckCircle2, XCircle } from "lucide-react";
import { adminComplaintService } from "@/services/adminComplaintService";
import { useToast } from "@/contexts/ToastContext";
import { Button } from "@/components/common/Button";
import { Badge } from "@/components/common/Badge";
import { Skeleton } from "@/components/common/Skeleton";
import { ErrorState } from "@/components/common/StateViews";
import { Card, CardBody } from "@/components/common/Card";
import { ReasonConfirmDialog } from "@/components/admin/ReasonConfirmDialog";
import { extractErrorMessage } from "@/services/api";
import type { ComplaintStatus } from "@/types/complaint";

const STATUS_VARIANT: Record<ComplaintStatus, "neutral" | "primary" | "success" | "danger"> = {
  OPEN: "primary",
  UNDER_REVIEW: "primary",
  RESOLVED: "success",
  DISMISSED: "neutral",
};

const TYPE_LABEL: Record<string, string> = {
  EXTRA_FARE_DEMANDED: "Extra fare demanded",
  UNSAFE_DRIVING: "Unsafe driving",
  RUDE_BEHAVIOR: "Rude behavior",
  VEHICLE_MISMATCH: "Vehicle mismatch",
  OTHER: "Other",
};

export default function AdminComplaintDetailPage() {
  const { complaintId } = useParams<{ complaintId: string }>();
  const id = Number(complaintId);
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const [busyAction, setBusyAction] = useState<"resolve" | "dismiss" | null>(null);
  const [pendingAction, setPendingAction] = useState<"resolve" | "dismiss" | null>(null);

  const { data: complaint, isPending, isError, refetch } = useQuery({
    queryKey: ["admin-complaint", id],
    queryFn: () => adminComplaintService.get(id),
    enabled: Number.isFinite(id),
  });

  async function handleAction(kind: "resolve" | "dismiss", reason: string) {
    setBusyAction(kind);
    try {
      const action = kind === "resolve" ? adminComplaintService.resolve : adminComplaintService.dismiss;
      await action(id, { reason: reason || undefined });
      showToast(kind === "resolve" ? "Complaint resolved" : "Complaint dismissed", "success");
      await queryClient.invalidateQueries({ queryKey: ["admin-complaint", id] });
      await queryClient.invalidateQueries({ queryKey: ["admin-complaints"] });
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setBusyAction(null);
      setPendingAction(null);
    }
  }

  if (isPending) {
    return (
      <div className="mx-auto max-w-2xl space-y-4 px-4 py-6 sm:px-6">
        <Skeleton className="h-8 w-40" />
        <Skeleton className="h-48 w-full rounded-2xl" />
      </div>
    );
  }

  if (isError || !complaint) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-6 sm:px-6">
        <ErrorState description="Couldn't load this complaint." onAction={() => refetch()} />
      </div>
    );
  }

  const isOpen = complaint.status === "OPEN" || complaint.status === "UNDER_REVIEW";

  return (
    <div className="mx-auto max-w-2xl space-y-5 px-4 py-6 sm:px-6">
      <Link to="/admin/complaints" className="flex items-center gap-1.5 text-sm font-medium text-muted hover:text-foreground">
        <ArrowLeft size={15} /> Back to complaints
      </Link>

      <Card>
        <CardBody>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-lg font-bold text-dark">{TYPE_LABEL[complaint.type] ?? complaint.type}</p>
              <p className="text-sm text-muted">
                Filed by {complaint.filedBy.name} ({complaint.filedBy.email}) as {complaint.filedByRole.toLowerCase()}
              </p>
              <p className="mt-1 text-xs text-muted">Ride #{complaint.rideId}</p>
            </div>
            <Badge variant={STATUS_VARIANT[complaint.status]}>{complaint.status.replaceAll("_", " ")}</Badge>
          </div>

          <div className="my-4 border-t border-slate-100 dark:border-card-border" />

          <p className="text-sm font-semibold text-dark">Description</p>
          <p className="mt-1 whitespace-pre-wrap text-sm text-foreground">{complaint.description}</p>

          {complaint.adminNotes && (
            <>
              <p className="mt-4 text-sm font-semibold text-dark">Admin notes</p>
              <p className="mt-1 whitespace-pre-wrap text-sm text-foreground">{complaint.adminNotes}</p>
            </>
          )}

          <p className="mt-4 text-xs text-muted">Filed {new Date(complaint.createdAt).toLocaleString()}</p>
          {complaint.resolvedAt && (
            <p className="text-xs text-muted">Closed {new Date(complaint.resolvedAt).toLocaleString()}</p>
          )}

          {isOpen && (
            <div className="mt-5 flex flex-wrap gap-3">
              <Button size="sm" onClick={() => setPendingAction("resolve")}>
                <CheckCircle2 size={14} /> Resolve
              </Button>
              <Button variant="outline" size="sm" onClick={() => setPendingAction("dismiss")}>
                <XCircle size={14} /> Dismiss
              </Button>
            </div>
          )}
        </CardBody>
      </Card>

      <ReasonConfirmDialog
        open={pendingAction === "resolve"}
        title="Resolve this complaint?"
        description="Add a note on how it was handled - visible in the complaint's history."
        confirmLabel="Resolve"
        isLoading={busyAction === "resolve"}
        onConfirm={(reason) => void handleAction("resolve", reason)}
        onCancel={() => setPendingAction(null)}
      />

      <ReasonConfirmDialog
        open={pendingAction === "dismiss"}
        title="Dismiss this complaint?"
        description="Use this when the report doesn't need further action - add a note explaining why."
        confirmLabel="Dismiss"
        isLoading={busyAction === "dismiss"}
        onConfirm={(reason) => void handleAction("dismiss", reason)}
        onCancel={() => setPendingAction(null)}
      />
    </div>
  );
}
