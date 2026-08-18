import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Siren, CheckCircle2, MapPin, Phone, User, Car, Trash2 } from "lucide-react";
import { adminSafetyService } from "@/services/adminSafetyService";
import { useToast } from "@/contexts/ToastContext";
import { Badge } from "@/components/common/Badge";
import { Button } from "@/components/common/Button";
import { Skeleton } from "@/components/common/Skeleton";
import { EmptyState, ErrorState } from "@/components/common/StateViews";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { ResolveSafetyAlertDialog } from "@/components/admin/ResolveSafetyAlertDialog";
import { Avatar } from "@/components/common/Avatar";
import { extractErrorMessage } from "@/services/api";
import { cn } from "@/lib/utils";
import type { SafetyAlert, SafetyAlertStatus } from "@/types/safety";

const TABS: { label: string; value: SafetyAlertStatus | "ALL" }[] = [
  { label: "Active", value: "ACTIVE" },
  { label: "Resolved", value: "RESOLVED" },
  { label: "All", value: "ALL" },
];

/** A real, working link - opens the exact spot in Google Maps so the SafeRide team can actually navigate there, not a decorative coordinate readout. */
function mapsLink(lat: number, lng: number) {
  return `https://www.google.com/maps?q=${lat},${lng}`;
}

export default function AdminSafetyPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = (searchParams.get("status") as SafetyAlertStatus | "ALL" | null) ?? "ACTIVE";
  const [page, setPage] = useState(0);
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const [resolvingAlert, setResolvingAlert] = useState<SafetyAlert | null>(null);
  const [isResolving, setIsResolving] = useState(false);
  const [deletingAlert, setDeletingAlert] = useState<SafetyAlert | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

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

  async function resolve(notes: string) {
    if (!resolvingAlert) return;
    setIsResolving(true);
    try {
      await adminSafetyService.resolve(resolvingAlert.id, notes);
      showToast("Alert resolved", "success");
      await queryClient.invalidateQueries({ queryKey: ["admin-safety-alerts"] });
      setResolvingAlert(null);
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsResolving(false);
    }
  }

  async function deleteAlert() {
    if (!deletingAlert) return;
    setIsDeleting(true);
    try {
      await adminSafetyService.delete(deletingAlert.id);
      showToast("Alert deleted", "success");
      await queryClient.invalidateQueries({ queryKey: ["admin-safety-alerts"] });
      setDeletingAlert(null);
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-6 sm:px-6">
      <h1 className="text-2xl font-extrabold text-dark">Safety alerts</h1>
      <p className="mt-1 text-sm text-muted">SOS presses from customers and drivers mid-ride.</p>

      <div className="mt-5 flex gap-1 border-b border-slate-200 dark:border-card-border">
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

      <div className="mt-4 space-y-3">
        {isPending ? (
          [...Array(3)].map((_, i) => <Skeleton key={i} className="h-40 w-full rounded-2xl" />)
        ) : isError ? (
          <ErrorState description="Couldn't load safety alerts." onAction={() => refetch()} />
        ) : data!.content.length === 0 ? (
          <EmptyState icon={CheckCircle2} title="No alerts" description="Nothing to review right now." />
        ) : (
          data!.content.map((alert) => (
            <div
              key={alert.id}
              className={cn(
                "rounded-2xl border p-4 shadow-sm",
                alert.status === "ACTIVE" ? "border-danger/40 bg-danger/5" : "border-slate-200 dark:border-card-border bg-white dark:bg-card",
              )}
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex items-start gap-3">
                  <div
                    className={cn(
                      "flex h-9 w-9 shrink-0 items-center justify-center rounded-full",
                      alert.status === "ACTIVE" ? "bg-danger/15 text-danger" : "bg-slate-100 dark:bg-white/10 text-muted",
                    )}
                  >
                    <Siren size={16} />
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-foreground">
                      SOS from {alert.triggeredBy.name} · Ride #{alert.rideId}
                    </p>
                    <p className="text-xs text-muted">{new Date(alert.createdAt).toLocaleString()}</p>
                  </div>
                </div>
                <Badge variant={alert.status === "ACTIVE" ? "danger" : "success"}>{alert.status}</Badge>
              </div>

              <div className="my-3 border-t border-slate-100 dark:border-card-border" />

              {/* Ride detail - route + current status, so the admin doesn't have to look this up separately. */}
              <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
                <div className="min-w-0">
                  <p className="truncate text-foreground">{alert.pickupAddress}</p>
                  <p className="truncate text-xs text-muted">→ {alert.destinationAddress}</p>
                </div>
                <Badge variant="neutral">{alert.rideStatus.replaceAll("_", " ")}</Badge>
              </div>

              <div className="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-2">
                <div className="rounded-xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-3">
                  <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted">
                    <User size={12} /> Customer
                  </p>
                  <div className="mt-1.5 flex items-center gap-2">
                    <Avatar name={alert.customer.name} avatarUrl={alert.customer.avatarUrl} size={28} />
                    <div>
                      <p className="text-sm font-medium text-foreground">{alert.customer.name}</p>
                      <a href={`tel:${alert.customer.phone}`} className="flex items-center gap-1 text-xs text-dark hover:underline">
                        <Phone size={11} /> {alert.customer.phone}
                      </a>
                    </div>
                  </div>
                </div>
                <div className="rounded-xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-3">
                  <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted">
                    <Car size={12} /> Driver
                  </p>
                  {alert.driver ? (
                    <div className="mt-1.5 flex items-center gap-2">
                      <Avatar name={alert.driver.name} avatarUrl={alert.driver.avatarUrl} size={28} />
                      <div>
                        <p className="text-sm font-medium text-foreground">{alert.driver.name}</p>
                        <a href={`tel:${alert.driver.phone}`} className="flex items-center gap-1 text-xs text-dark hover:underline">
                          <Phone size={11} /> {alert.driver.phone}
                        </a>
                        {alert.driver.vehicleNumber && (
                          <p className="mt-0.5 text-xs text-muted">
                            {alert.driver.vehicleModel} · {alert.driver.vehicleNumber}
                          </p>
                        )}
                      </div>
                    </div>
                  ) : (
                    <p className="mt-1 text-xs text-muted">No driver assigned yet</p>
                  )}
                </div>
              </div>

              <a
                href={mapsLink(alert.latitude, alert.longitude)}
                target="_blank"
                rel="noreferrer"
                className="mt-3 flex items-center gap-1.5 text-xs font-medium text-dark hover:underline"
              >
                <MapPin size={13} /> Open location in Google Maps ({alert.latitude.toFixed(4)}, {alert.longitude.toFixed(4)})
              </a>

              {alert.status === "RESOLVED" ? (
                <div className="mt-3 rounded-xl bg-slate-50 dark:bg-white/5 p-3">
                  <div className="flex items-start justify-between gap-2">
                    <p className="text-xs font-semibold text-foreground">
                      Resolved by {alert.resolvedBy?.name ?? "—"}
                      {alert.resolvedAt && ` · ${new Date(alert.resolvedAt).toLocaleString()}`}
                    </p>
                    <button
                      onClick={() => setDeletingAlert(alert)}
                      aria-label="Delete this alert"
                      className="shrink-0 rounded-lg p-1 text-muted hover:bg-slate-200 hover:text-danger"
                    >
                      <Trash2 size={13} />
                    </button>
                  </div>
                  {alert.resolutionNotes && <p className="mt-1 text-sm text-muted">{alert.resolutionNotes}</p>}
                </div>
              ) : (
                <Button size="sm" className="mt-3" onClick={() => setResolvingAlert(alert)}>
                  Resolve
                </Button>
              )}
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

      <ResolveSafetyAlertDialog
        open={!!resolvingAlert}
        isLoading={isResolving}
        onConfirm={resolve}
        onCancel={() => setResolvingAlert(null)}
      />

      <ConfirmDialog
        open={!!deletingAlert}
        title="Delete this alert?"
        description="This only removes the record - it won't undo anything about the ride itself."
        confirmLabel="Delete"
        cancelLabel="Keep it"
        destructive
        isLoading={isDeleting}
        onConfirm={deleteAlert}
        onCancel={() => setDeletingAlert(null)}
      />
    </div>
  );
}
