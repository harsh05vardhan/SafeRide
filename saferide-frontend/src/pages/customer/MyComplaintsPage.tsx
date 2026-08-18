import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { MessageSquareWarning, ChevronLeft, ChevronRight } from "lucide-react";
import { complaintService } from "@/services/complaintService";
import { Badge } from "@/components/common/Badge";
import { Button } from "@/components/common/Button";
import { Skeleton } from "@/components/common/Skeleton";
import { EmptyState, ErrorState } from "@/components/common/StateViews";
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

/** Reachable from both the customer and driver "Report an issue" flow (see ReportIssueDialog) - either side can end up here to check what happened to a report they filed. */
export default function MyComplaintsPage() {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, refetch } = useQuery({
    queryKey: ["my-complaints", page],
    queryFn: () => complaintService.listMine(page, 10),
  });

  return (
    <div className="mx-auto max-w-2xl px-4 py-6 sm:px-6">
      <h1 className="text-2xl font-extrabold text-dark">My reports</h1>
      <p className="mt-1 text-sm text-muted">Issues you've reported about a ride.</p>

      <div className="mt-5 space-y-3">
        {isPending ? (
          [...Array(3)].map((_, i) => <Skeleton key={i} className="h-24 w-full rounded-2xl" />)
        ) : isError ? (
          <ErrorState title="Couldn't load your reports" onAction={() => refetch()} />
        ) : data!.content.length === 0 ? (
          <EmptyState icon={MessageSquareWarning} title="No reports filed" description="Anything you report about a ride will show up here." />
        ) : (
          data!.content.map((complaint) => (
            <div key={complaint.id} className="rounded-2xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-4 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-foreground">{TYPE_LABEL[complaint.type] ?? complaint.type}</p>
                  <p className="mt-1 line-clamp-2 text-xs text-muted">{complaint.description}</p>
                </div>
                <Badge variant={STATUS_VARIANT[complaint.status]}>{complaint.status.replaceAll("_", " ")}</Badge>
              </div>
              {complaint.adminNotes && (
                <p className="mt-2 border-t border-slate-100 dark:border-card-border pt-2 text-xs text-muted">
                  <span className="font-semibold text-foreground">Response: </span>
                  {complaint.adminNotes}
                </p>
              )}
              <p className="mt-2 text-xs text-muted">Ride #{complaint.rideId} · {new Date(complaint.createdAt).toLocaleDateString()}</p>
            </div>
          ))
        )}
      </div>

      {data && data.page.totalPages > 1 && (
        <div className="mt-5 flex items-center justify-center gap-3">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            <ChevronLeft size={14} /> Prev
          </Button>
          <span className="text-xs text-muted">
            Page {page + 1} of {data.page.totalPages}
          </span>
          <Button variant="outline" size="sm" disabled={page >= data.page.totalPages - 1} onClick={() => setPage((p) => p + 1)}>
            Next <ChevronRight size={14} />
          </Button>
        </div>
      )}
    </div>
  );
}
