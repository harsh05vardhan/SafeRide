import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Car, ChevronRight, ChevronLeft } from "lucide-react";
import { rideService } from "@/services/rideService";
import { Badge } from "@/components/common/Badge";
import { Skeleton } from "@/components/common/Skeleton";
import { EmptyState, ErrorState } from "@/components/common/StateViews";
import { Button } from "@/components/common/Button";
import type { RideStatus } from "@/types/ride";

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

export default function MyRidesPage() {
  const [page, setPage] = useState(0);

  const { data, isPending, isError, refetch } = useQuery({
    queryKey: ["my-rides", page],
    queryFn: () => rideService.myRides(page, 10),
  });

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
            <Link
              key={ride.id}
              to={`/ride/${ride.id}`}
              className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md"
            >
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-dark">
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
              <div className="flex flex-col items-end gap-2">
                <Badge variant={STATUS_VARIANT[ride.status]}>{STATUS_LABEL[ride.status]}</Badge>
                <ChevronRight size={16} className="text-muted" />
              </div>
            </Link>
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
