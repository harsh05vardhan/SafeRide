import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Car, ChevronRight, ChevronLeft, X } from "lucide-react";
import { rideService } from "@/services/rideService";
import { Badge } from "@/components/common/Badge";
import { Skeleton } from "@/components/common/Skeleton";
import { EmptyState, ErrorState } from "@/components/common/StateViews";
import { Button } from "@/components/common/Button";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { useToast } from "@/contexts/ToastContext";
import { extractErrorMessage } from "@/services/api";
import type { Ride, RideStatus } from "@/types/ride";

const STATUS_VARIANT: Record<RideStatus, "success" | "danger" | "neutral" | "primary"> = {
  REQUESTED: "neutral",
  SEARCHING_DRIVER: "primary",
  DRIVER_ASSIGNED: "primary",
  DRIVER_ACCEPTED: "primary",
  DRIVER_ARRIVED: "primary",
  RIDE_STARTED: "primary",
  RIDE_COMPLETED: "success",
  PAYMENT_PENDING: "success",
  PAYMENT_COMPLETED: "success",
  RATED: "success",
  CANCELLED: "danger",
  DRIVER_CANCELLED: "danger",
};

const STATUS_LABEL: Record<RideStatus, string> = {
  REQUESTED: "Requested",
  SEARCHING_DRIVER: "Searching",
  DRIVER_ASSIGNED: "Assigned",
  DRIVER_ACCEPTED: "Accepted",
  DRIVER_ARRIVED: "Driver arrived",
  RIDE_STARTED: "In progress",
  RIDE_COMPLETED: "Completed",
  PAYMENT_PENDING: "Completed",
  PAYMENT_COMPLETED: "Completed",
  RATED: "Completed",
  CANCELLED: "Cancelled",
  DRIVER_CANCELLED: "Cancelled",
};

/** Same set RideTrackingPage's own canCancel uses - matches RideStateMachine's actual CANCELLED-reachable statuses, not guessed. */
const CANCELLABLE_STATUSES: RideStatus[] = ["REQUESTED", "SEARCHING_DRIVER", "DRIVER_ASSIGNED", "DRIVER_ACCEPTED", "DRIVER_ARRIVED"];

export default function MyRidesPage() {
  const [page, setPage] = useState(0);
  const [cancellingRide, setCancellingRide] = useState<Ride | null>(null);
  const [isCancelling, setIsCancelling] = useState(false);
  const { showToast } = useToast();
  const queryClient = useQueryClient();

  const { data, isPending, isError, refetch } = useQuery({
    queryKey: ["my-rides", page],
    queryFn: () => rideService.myRides(page, 10),
  });

  async function confirmCancel() {
    if (!cancellingRide) return;
    setIsCancelling(true);
    try {
      await rideService.cancel(cancellingRide.id);
      showToast("Ride cancelled", "info");
      await queryClient.invalidateQueries({ queryKey: ["my-rides"] });
      setCancellingRide(null);
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsCancelling(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-6 sm:px-6">
      <h1 className="text-2xl font-extrabold text-dark">My Rides</h1>
      <p className="mt-1 text-sm text-muted">Your ride history.</p>

      <div className="mt-5 space-y-3">
        {isPending ? (
          [...Array(4)].map((_, i) => <Skeleton key={i} className="h-24 w-full rounded-2xl" />)
        ) : isError ? (
          <ErrorState title="Couldn't load your rides" description="Please try again." onAction={() => refetch()} />
        ) : data!.content.length === 0 ? (
          <EmptyState icon={Car} title="No rides yet" description="Your completed and past rides will show up here." />
        ) : (
          data!.content.map((ride) => (
            <div key={ride.id} className="flex items-center gap-3 rounded-2xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-4 shadow-sm">
              <Link to={`/ride/${ride.id}`} className="flex min-w-0 flex-1 items-center gap-3">
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-slate-100 dark:bg-white/10 text-dark">
                  <Car size={20} />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold text-foreground">{ride.pickupAddress}</p>
                  <p className="truncate text-xs text-muted">→ {ride.destinationAddress}</p>
                  <p className="mt-1 text-xs text-muted">
                    {new Date(ride.createdAt).toLocaleDateString(undefined, { day: "numeric", month: "short" })} ·{" "}
                    {new Date(ride.createdAt).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}
                  </p>
                </div>
              </Link>
              <div className="flex shrink-0 flex-col items-end gap-2">
                <Badge variant={STATUS_VARIANT[ride.status]}>{STATUS_LABEL[ride.status]}</Badge>
                {CANCELLABLE_STATUSES.includes(ride.status) ? (
                  <button
                    onClick={() => setCancellingRide(ride)}
                    className="flex items-center gap-1 rounded-full bg-danger/10 px-2.5 py-1 text-xs font-semibold text-danger hover:bg-danger/15"
                  >
                    <X size={12} /> Cancel
                  </button>
                ) : (
                  <Link to={`/ride/${ride.id}`}>
                    <ChevronRight size={16} className="text-muted" />
                  </Link>
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

      <ConfirmDialog
        open={!!cancellingRide}
        title="Cancel this ride?"
        description="We'll stop looking for a driver (or notify your driver if one's already assigned)."
        confirmLabel="Cancel Ride"
        cancelLabel="Keep Ride"
        destructive
        isLoading={isCancelling}
        onConfirm={confirmCancel}
        onCancel={() => setCancellingRide(null)}
      />
    </div>
  );
}
