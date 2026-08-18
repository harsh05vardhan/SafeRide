import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Users } from "lucide-react";
import { adminService } from "@/services/adminService";
import { Badge } from "@/components/common/Badge";
import { Button } from "@/components/common/Button";
import { Skeleton } from "@/components/common/Skeleton";
import { EmptyState, ErrorState } from "@/components/common/StateViews";
import { VerificationBadge, AccountStatusBadge } from "@/components/admin/StatusBadges";
import { Avatar } from "@/components/common/Avatar";
import { cn } from "@/lib/utils";
import type { VerificationStatus } from "@/types/common";

const TABS: { label: string; value: VerificationStatus | "ALL" }[] = [
  { label: "All", value: "ALL" },
  { label: "Pending", value: "PENDING" },
  { label: "Verified", value: "VERIFIED" },
  { label: "Rejected", value: "REJECTED" },
];

export default function AdminDriversPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = (searchParams.get("status") as VerificationStatus | null) ?? "ALL";
  const [page, setPage] = useState(0);

  const status = activeTab === "ALL" ? undefined : activeTab;
  const { data, isPending, isError, refetch } = useQuery({
    queryKey: ["admin-drivers", status, page],
    queryFn: () => adminService.listDrivers(status, page, 15),
  });

  function selectTab(value: VerificationStatus | "ALL") {
    setPage(0);
    if (value === "ALL") {
      setSearchParams({});
    } else {
      setSearchParams({ status: value });
    }
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 sm:px-6">
      <h1 className="text-2xl font-extrabold text-dark">Drivers</h1>
      <p className="mt-1 text-sm text-muted">Review driver, vehicle and document verification.</p>

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

      <div className="mt-4 overflow-x-auto rounded-2xl border border-slate-200 bg-white shadow-sm">
        {isPending ? (
          <div className="space-y-2 p-4">
            {[...Array(5)].map((_, i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : isError ? (
          <div className="p-4">
            <ErrorState description="Couldn't load drivers." onAction={() => refetch()} />
          </div>
        ) : data!.content.length === 0 ? (
          <div className="p-4">
            <EmptyState icon={Users} title="No drivers found" description="No drivers match this filter." />
          </div>
        ) : (
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead>
              <tr className="border-b border-slate-100 text-xs font-semibold uppercase tracking-wide text-muted">
                <th className="px-4 py-3">Driver</th>
                <th className="px-4 py-3">License</th>
                <th className="px-4 py-3">Reliability</th>
                <th className="px-4 py-3">Verification</th>
                <th className="px-4 py-3">Account</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {data!.content.map((driver) => (
                <tr key={driver.id} className="border-b border-slate-50 last:border-0 hover:bg-slate-50/60">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2.5">
                      <Avatar name={driver.user.name} avatarUrl={driver.user.avatarUrl} size={30} />
                      <div>
                        <p className="font-medium text-foreground">{driver.user.name}</p>
                        <p className="text-xs text-muted">{driver.user.email}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-foreground">{driver.licenseNumber}</td>
                  <td className="px-4 py-3 text-foreground">{driver.reliabilityScore.toFixed(0)}</td>
                  <td className="px-4 py-3">
                    <VerificationBadge status={driver.verificationStatus} />
                  </td>
                  <td className="px-4 py-3">
                    <AccountStatusBadge status={driver.accountStatus} />
                  </td>
                  <td className="px-4 py-3 text-right">
                    <Link to={`/admin/drivers/${driver.id}`}>
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
