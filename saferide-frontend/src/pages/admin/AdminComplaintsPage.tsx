import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, MessageSquareWarning } from "lucide-react";
import { adminComplaintService } from "@/services/adminComplaintService";
import { Badge } from "@/components/common/Badge";
import { Button } from "@/components/common/Button";
import { Skeleton } from "@/components/common/Skeleton";
import { EmptyState, ErrorState } from "@/components/common/StateViews";
import { cn } from "@/lib/utils";
import type { ComplaintStatus } from "@/types/complaint";

const TABS: { label: string; value: ComplaintStatus | "ALL" }[] = [
  { label: "All", value: "ALL" },
  { label: "Open", value: "OPEN" },
  { label: "Under review", value: "UNDER_REVIEW" },
  { label: "Resolved", value: "RESOLVED" },
  { label: "Dismissed", value: "DISMISSED" },
];

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

export default function AdminComplaintsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = (searchParams.get("status") as ComplaintStatus | null) ?? "ALL";
  const [page, setPage] = useState(0);

  const status = activeTab === "ALL" ? undefined : activeTab;
  const { data, isPending, isError, refetch } = useQuery({
    queryKey: ["admin-complaints", status, page],
    queryFn: () => adminComplaintService.list(status, page, 15),
  });

  function selectTab(value: ComplaintStatus | "ALL") {
    setPage(0);
    setSearchParams(value === "ALL" ? {} : { status: value });
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 sm:px-6">
      <h1 className="text-2xl font-extrabold text-dark">Complaints</h1>
      <p className="mt-1 text-sm text-muted">Reports filed by customers and drivers about each other.</p>

      <div className="mt-5 flex gap-1 overflow-x-auto border-b border-slate-200">
        {TABS.map((tab) => (
          <button
            key={tab.value}
            onClick={() => selectTab(tab.value)}
            className={cn(
              "shrink-0 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors",
              activeTab === tab.value ? "border-dark text-dark" : "border-transparent text-muted hover:text-foreground",
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="mt-4 overflow-x-auto rounded-2xl border border-slate-200 bg-white shadow-sm">
        {isPending ? (
          <div className="space-y-2 p-4">
            {[...Array(5)].map((_, i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : isError ? (
          <div className="p-4">
            <ErrorState description="Couldn't load complaints." onAction={() => refetch()} />
          </div>
        ) : data!.content.length === 0 ? (
          <div className="p-4">
            <EmptyState icon={MessageSquareWarning} title="No complaints found" description="No complaints match this filter." />
          </div>
        ) : (
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead>
              <tr className="border-b border-slate-100 text-xs font-semibold uppercase tracking-wide text-muted">
                <th className="px-4 py-3">Filed by</th>
                <th className="px-4 py-3">Type</th>
                <th className="px-4 py-3">Ride</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Filed</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {data!.content.map((complaint) => (
                <tr key={complaint.id} className="border-b border-slate-50 last:border-0 hover:bg-slate-50/60">
                  <td className="px-4 py-3">
                    <p className="font-medium text-foreground">{complaint.filedBy.name}</p>
                    <p className="text-xs text-muted">as {complaint.filedByRole.toLowerCase()}</p>
                  </td>
                  <td className="px-4 py-3 text-foreground">{TYPE_LABEL[complaint.type] ?? complaint.type}</td>
                  <td className="px-4 py-3 text-foreground">#{complaint.rideId}</td>
                  <td className="px-4 py-3">
                    <Badge variant={STATUS_VARIANT[complaint.status]}>{complaint.status.replaceAll("_", " ")}</Badge>
                  </td>
                  <td className="px-4 py-3 text-xs text-muted">{new Date(complaint.createdAt).toLocaleDateString()}</td>
                  <td className="px-4 py-3 text-right">
                    <Link to={`/admin/complaints/${complaint.id}`}>
                      <Badge variant="dark">Review</Badge>
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
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
