import { Link } from "react-router-dom";
import { useQueries, useQuery } from "@tanstack/react-query";
import { Users, Clock, CheckCircle2, XCircle, ChevronRight } from "lucide-react";
import { adminService } from "@/services/adminService";
import { Skeleton } from "@/components/common/Skeleton";
import { EmptyState } from "@/components/common/StateViews";
import { StatCard } from "@/components/driver/StatCard";
import { VerificationBadge } from "@/components/admin/StatusBadges";
import type { VerificationStatus } from "@/types/common";

const STATUS_FILTERS: (VerificationStatus | undefined)[] = [undefined, "PENDING", "VERIFIED", "REJECTED"];

/**
 * Overview stats, computed from GET /api/admin/drivers?status=... - the
 * backend has no dedicated /admin/summary endpoint yet, so this reads the
 * same paginated list four times (size=1, only the total count matters)
 * rather than the frontend inventing numbers the backend never returned.
 */
export default function AdminDashboardPage() {
  const results = useQueries({
    queries: STATUS_FILTERS.map((status) => ({
      queryKey: ["admin-driver-count", status],
      queryFn: () => adminService.listDrivers(status, 0, 1),
    })),
  });
  const [totalQ, pendingQ, verifiedQ, rejectedQ] = results;

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-6 sm:px-6">
      <div>
        <h1 className="text-2xl font-extrabold text-dark">Admin dashboard</h1>
        <p className="mt-1 text-sm text-muted">Driver verification overview.</p>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {results.some((r) => r.isPending) ? (
          [...Array(4)].map((_, i) => <Skeleton key={i} className="h-24 rounded-2xl" />)
        ) : (
          <>
            <StatCard icon={Users} label="Total drivers" value={String(totalQ.data?.page.totalElements ?? 0)} />
            <StatCard
              icon={Clock}
              label="Pending review"
              value={String(pendingQ.data?.page.totalElements ?? 0)}
              tone={(pendingQ.data?.page.totalElements ?? 0) > 0 ? "warning" : "neutral"}
            />
            <StatCard icon={CheckCircle2} label="Verified" value={String(verifiedQ.data?.page.totalElements ?? 0)} />
            <StatCard icon={XCircle} label="Rejected" value={String(rejectedQ.data?.page.totalElements ?? 0)} />
          </>
        )}
      </div>

      <PendingQueue />
    </div>
  );
}

/** A separate component so the actual pending rows (not just the count above) get their own query and loading state. */
function PendingQueue() {
  const { data, isPending } = useQuery({
    queryKey: ["admin-drivers", "PENDING", 0, 5],
    queryFn: () => adminService.listDrivers("PENDING", 0, 5),
  });

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-dark">Awaiting review</h2>
        <Link to="/admin/drivers?status=PENDING" className="flex items-center gap-1 text-xs font-semibold text-dark hover:underline">
          View all <ChevronRight size={14} />
        </Link>
      </div>

      {isPending ? (
        <div className="space-y-2">
          {[...Array(3)].map((_, i) => (
            <Skeleton key={i} className="h-16 w-full rounded-2xl" />
          ))}
        </div>
      ) : data && data.content.length === 0 ? (
        <EmptyState icon={CheckCircle2} title="Nothing waiting on you" description="Every driver has been reviewed." />
      ) : (
        <div className="space-y-2">
          {data?.content.map((driver) => (
            <Link
              key={driver.id}
              to={`/admin/drivers/${driver.id}`}
              className="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md"
            >
              <div>
                <p className="text-sm font-semibold text-foreground">{driver.user.name}</p>
                <p className="text-xs text-muted">
                  {driver.user.email} · License {driver.licenseNumber}
                </p>
              </div>
              <div className="flex items-center gap-3">
                <VerificationBadge status={driver.verificationStatus} />
                <ChevronRight size={16} className="text-muted" />
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
