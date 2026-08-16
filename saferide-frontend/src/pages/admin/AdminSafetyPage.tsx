import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Siren, CheckCircle2 } from "lucide-react";
import { adminSafetyService } from "@/services/adminSafetyService";
import { useToast } from "@/contexts/ToastContext";
import { Badge } from "@/components/common/Badge";
import { Button } from "@/components/common/Button";
import { Skeleton } from "@/components/common/Skeleton";
import { EmptyState, ErrorState } from "@/components/common/StateViews";
import { extractErrorMessage } from "@/services/api";
import { cn } from "@/lib/utils";
import type { SafetyAlertStatus } from "@/types/safety";

const TABS: { label: string; value: SafetyAlertStatus | "ALL" }[] = [
  { label: "Active", value: "ACTIVE" },
  { label: "Resolved", value: "RESOLVED" },
  { label: "All", value: "ALL" },
];

export default function AdminSafetyPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = (searchParams.get("status") as SafetyAlertStatus | "ALL" | null) ?? "ACTIVE";
  const [page, setPage] = useState(0);
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const [resolvingId, setResolvingId] = useState<number | null>(null);

  const status = activeTab === "ALL" ? undefined : activeTab;
  const { data, isPending, isError, refetch } = useQuery({
    queryKey: ["admin-safety-alerts", status, page],
    queryFn: () => adminSafetyService.list(status, page, 15),
    refetchInterval: activeTab === "ACTIVE" ? 15000 : false,
  });

  function selectTab(value: SafetyAlertStatus | "ALL") {
    setPage(0);
    setSearchParams(value === "ALL" ? {} : { status: value });
  }

  async function resolve(alertId: number) {
    setResolvingId(alertId);
    try {
      await adminSafetyService.resolve(alertId);
      showToast("Alert resolved", "success");
      await queryClient.invalidateQueries({ queryKey: ["admin-safety-alerts"] });
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setResolvingId(null);
    }
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-6 sm:px-6">
      <h1 className="text-2xl font-extrabold text-dark">Safety alerts</h1>
      <p className="mt-1 text-sm text-muted">SOS presses from customers and drivers mid-ride.</p>

      <div className="mt-5 flex gap-1 border-b border-slate-200">
        {TABS.map((tab) => (
          <button
            key={tab.value}
            onClick={() => selectTab(tab.value)}
            className={cn(
              "border-b-2 px-4 py-2.5 text-sm font-medium transition-colors",
              activeTab === tab.value ? "border-dark text-dark" : "border-transparent text-muted hover:text-foreground",
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="mt-4 space-y-2">
        {isPending ? (
          [...Array(3)].map((_, i) => <Skeleton key={i} className="h-20 w-full rounded-2xl" />)
        ) : isError ? (
          <ErrorState description="Couldn't load safety alerts." onAction={() => refetch()} />
        ) : data!.content.length === 0 ? (
          <EmptyState icon={CheckCircle2} title="No alerts" description="Nothing to review right now." />
        ) : (
          data!.content.map((alert) => (
            <div
              key={alert.id}
              className={cn(
                "flex flex-wrap items-center justify-between gap-3 rounded-2xl border p-4 shadow-sm",
                alert.status === "ACTIVE" ? "border-danger/40 bg-danger/5" : "border-slate-200 bg-white",
              )}
            >
              <div className="flex items-start gap-3">
                <div
                  className={cn(
                    "flex h-9 w-9 shrink-0 items-center justify-center rounded-full",
                    alert.status === "ACTIVE" ? "bg-danger/15 text-danger" : "bg-slate-100 text-muted",
                  )}
                >
                  <Siren size={16} />
                </div>
                <div>
                  <p className="text-sm font-semibold text-foreground">
                    {alert.triggeredBy.name} · Ride #{alert.rideId}
                  </p>
                  <p className="text-xs text-muted">
                    {alert.latitude.toFixed(4)}, {alert.longitude.toFixed(4)} · {new Date(alert.createdAt).toLocaleString()}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant={alert.status === "ACTIVE" ? "danger" : "success"}>{alert.status}</Badge>
                {alert.status === "ACTIVE" && (
                  <Button size="sm" isLoading={resolvingId === alert.id} onClick={() => resolve(alert.id)}>
                    Mark resolved
                  </Button>
                )}
              </div>
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
