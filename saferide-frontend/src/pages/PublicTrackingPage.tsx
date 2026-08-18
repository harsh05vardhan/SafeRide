import { useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ShieldCheck, Car, Navigation, MapPin } from "lucide-react";
import { safetyService } from "@/services/safetyService";
import { MapView } from "@/components/map/MapView";
import { PickupMarker } from "@/components/map/PickupMarker";
import { DestinationMarker } from "@/components/map/DestinationMarker";
import { RouteLine } from "@/components/map/RouteLine";
import { Badge } from "@/components/common/Badge";
import { FullPageSpinner } from "@/components/common/Spinner";
import { ErrorState } from "@/components/common/StateViews";
import type { RideStatus } from "@/types/ride";

const STATUS_LABEL: Record<RideStatus, string> = {
  REQUESTED: "Requesting a driver…",
  SEARCHING_DRIVER: "Finding a driver…",
  DRIVER_ASSIGNED: "Driver assigned",
  DRIVER_ACCEPTED: "Driver is on the way",
  DRIVER_ARRIVED: "Driver has arrived",
  RIDE_STARTED: "Ride in progress",
  RIDE_COMPLETED: "Ride completed",
  PAYMENT_PENDING: "Ride completed",
  PAYMENT_COMPLETED: "Ride completed",
  RATED: "Ride completed",
  CANCELLED: "Ride cancelled",
  DRIVER_CANCELLED: "Ride cancelled",
};

/**
 * The public side of "Share Ride" - reachable with no login at all,
 * guarded only by possessing the token itself (see SafetyService's class
 * doc on the backend). Deliberately polling-only, not WebSocket-backed:
 * the real-time channel (Phase 10) authenticates on CONNECT with a JWT a
 * visitor here never has, so this stays a plain, honest 8s poll instead of
 * pretending to a live push it can't actually offer.
 */
export default function PublicTrackingPage() {
  const { token } = useParams<{ token: string }>();

  const { data: trip, isPending, isError } = useQuery({
    queryKey: ["public-trip", token],
    queryFn: () => safetyService.getPublicTrip(token!),
    enabled: !!token,
    refetchInterval: 8000,
  });

  if (isPending) return <FullPageSpinner label="Loading trip…" />;

  if (isError || !trip) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background px-4">
        <ErrorState
          icon={ShieldCheck}
          title="This link isn't valid"
          description="It may have expired, or the trip may already be over."
        />
      </div>
    );
  }

  const center: [number, number] = [trip.pickupLatitude, trip.pickupLongitude];
  const isActive = !["CANCELLED", "DRIVER_CANCELLED"].includes(trip.status);

  return (
    <div className="relative h-screen w-full">
      <MapView center={center} zoom={13} className="absolute inset-0">
        <PickupMarker latitude={trip.pickupLatitude} longitude={trip.pickupLongitude} address={trip.pickupAddress} />
        <DestinationMarker
          latitude={trip.destinationLatitude}
          longitude={trip.destinationLongitude}
          address={trip.destinationAddress}
        />
        <RouteLine
          from={[trip.pickupLatitude, trip.pickupLongitude]}
          to={[trip.destinationLatitude, trip.destinationLongitude]}
        />
      </MapView>

      <div className="pointer-events-none absolute inset-x-0 top-0 flex justify-center p-4">
        <div className="pointer-events-auto flex items-center gap-2 rounded-2xl bg-white dark:bg-card px-4 py-2.5 shadow-lg">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <ShieldCheck size={16} />
          </div>
          <span className="text-sm font-bold text-dark">SafeRide</span>
          <span className="text-xs text-muted">· Live trip tracking</span>
        </div>
      </div>

      <div className="absolute inset-x-0 bottom-0 p-4">
        <div className="mx-auto max-w-md rounded-2xl bg-white dark:bg-card p-5 shadow-lg">
          <Badge variant={isActive ? "primary" : trip.status === "CANCELLED" || trip.status === "DRIVER_CANCELLED" ? "danger" : "success"}>
            {STATUS_LABEL[trip.status]}
          </Badge>

          <div className="mt-3 space-y-1.5 text-sm">
            <p className="flex items-start gap-2">
              <Navigation size={14} className="mt-0.5 shrink-0 text-dark" />
              <span className="truncate text-foreground">{trip.pickupAddress}</span>
            </p>
            <p className="flex items-start gap-2">
              <MapPin size={14} className="mt-0.5 shrink-0 text-danger" />
              <span className="truncate text-foreground">{trip.destinationAddress}</span>
            </p>
          </div>

          {trip.driverName && (
            <div className="mt-3 flex items-center gap-2 border-t border-slate-100 dark:border-card-border pt-3 text-sm">
              <Car size={14} className="text-muted" />
              <span className="text-foreground">
                {trip.driverName}
                {trip.vehicleModel && ` · ${trip.vehicleModel}`}
                {trip.vehicleNumber && ` · ${trip.vehicleNumber}`}
              </span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
