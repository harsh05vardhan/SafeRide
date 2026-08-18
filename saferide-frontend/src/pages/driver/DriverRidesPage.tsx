import { useNavigate } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Navigation, MapPin, Inbox } from "lucide-react";
import { rideService } from "@/services/rideService";
import { useCurrentDriverRide } from "@/hooks/useCurrentDriverRide";
import { useDriverProfile } from "@/hooks/useDriverProfile";
import { useToast } from "@/contexts/ToastContext";
import { Button } from "@/components/common/Button";
import { Skeleton } from "@/components/common/Skeleton";
import { EmptyState, ErrorState } from "@/components/common/StateViews";
import { IncomingRequestCard } from "@/components/driver/IncomingRequestCard";
import { extractErrorMessage } from "@/services/api";

/**
 * The fallback path: rides that matching didn't (yet) proactively assign
 * to anyone. Only useful once online with a verified vehicle - see
 * RideService.listOpenRides on the backend for why this list is scoped
 * to the driver's own active vehicle type and never shows a ride they've
 * already declined or cancelled.
 */
export default function DriverRidesPage() {
  const { data: profile } = useDriverProfile();
  const { currentRide } = useCurrentDriverRide();
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const isOnline = profile?.onlineStatus === "ONLINE";

  const { data: openRides, isPending, isError, refetch } = useQuery({
    queryKey: ["open-rides"],
    queryFn: () => rideService.openRides(0, 20),
    enabled: isOnline && !currentRide,
    refetchInterval: isOnline && !currentRide ? 5000 : false,
  });

  async function acceptCurrentRide() {
    if (!currentRide) return;
    try {
      await rideService.accept(currentRide.id);
      showToast("Ride accepted", "success");
      navigate(`/driver/ride/${currentRide.id}`);
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    }
  }

  async function declineCurrentRide() {
    if (!currentRide) return;
    try {
      await rideService.decline(currentRide.id);
      showToast("Ride declined", "info");
      await queryClient.invalidateQueries({ queryKey: ["driver-rides"] });
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    }
  }

  async function claimRide(rideId: number) {
    try {
      await rideService.accept(rideId);
      showToast("Ride accepted", "success");
      navigate(`/driver/ride/${rideId}`);
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
      await refetch();
    }
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-6 sm:px-6">
      <h1 className="text-2xl font-extrabold text-dark">Ride requests</h1>
      <p className="mt-1 text-sm text-muted">New requests are assigned to you automatically while you're online.</p>

      <div className="mt-5 space-y-3">
        {currentRide?.status === "DRIVER_ASSIGNED" ? (
          <IncomingRequestCard ride={currentRide} onAccept={acceptCurrentRide} onDecline={declineCurrentRide} />
        ) : currentRide ? (
          <EmptyState
            icon={Navigation}
            title="You already have an active ride"
            description="Finish or cancel your current ride before browsing new requests."
            actionLabel="View current ride"
            onAction={() => navigate(`/driver/ride/${currentRide.id}`)}
          />
        ) : !isOnline ? (
          <EmptyState icon={Inbox} title="You're offline" description="Go online from the dashboard to see ride requests." />
        ) : isPending ? (
          [...Array(3)].map((_, i) => <Skeleton key={i} className="h-28 w-full rounded-2xl" />)
        ) : isError ? (
          <ErrorState description="Couldn't load open requests." onAction={() => refetch()} />
        ) : openRides!.content.length === 0 ? (
          <EmptyState icon={Inbox} title="No open requests right now" description="We'll assign you a ride automatically as soon as one matches." />
        ) : (
          openRides!.content.map((ride) => (
            <div key={ride.id} className="rounded-2xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-4 shadow-sm">
              <p className="text-sm font-semibold text-foreground">{ride.customer.name}</p>
              <div className="mt-2 space-y-1.5 text-sm">
                <p className="flex items-start gap-2">
                  <Navigation size={14} className="mt-0.5 shrink-0 text-dark" />
                  <span className="truncate text-foreground">{ride.pickupAddress}</span>
                </p>
                <p className="flex items-start gap-2">
                  <MapPin size={14} className="mt-0.5 shrink-0 text-danger" />
                  <span className="truncate text-foreground">{ride.destinationAddress}</span>
                </p>
              </div>
              <div className="mt-3 flex items-center justify-between">
                <span className="text-xs text-muted">
                  {ride.distanceKm.toFixed(1)} km · {ride.estimatedDurationMinutes} min · {ride.vehicleType}
                </span>
                <Button size="sm" onClick={() => claimRide(ride.id)}>
                  Accept
                </Button>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
