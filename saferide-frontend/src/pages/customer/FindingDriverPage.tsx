import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Search, X } from "lucide-react";
import { rideService } from "@/services/rideService";
import { useToast } from "@/contexts/ToastContext";
import { useRideStatusChannel } from "@/hooks/useRideChannel";
import { useCountdown } from "@/hooks/useCountdown";
import { RIDE_SEARCH_TIMEOUT_SECONDS, formatCountdown } from "@/lib/rideSearch";
import { MapView } from "@/components/map/MapView";
import { PickupMarker } from "@/components/map/PickupMarker";
import { DestinationMarker } from "@/components/map/DestinationMarker";
import { RouteLine } from "@/components/map/RouteLine";
import { SearchRadarMarker } from "@/components/map/SearchRadarMarker";
import { Button } from "@/components/common/Button";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { extractErrorMessage } from "@/services/api";
import { FullPageSpinner } from "@/components/common/Spinner";
import { NoDriverFoundState } from "@/components/ride/NoDriverFoundState";

/** Fallback cadence in case the WebSocket connection is down/reconnecting - the real-time push via useRideStatusChannel below is what normally moves this page along within a second or two of a match. */
const POLL_INTERVAL_MS = 8000;

export default function FindingDriverPage() {
  const { rideId } = useParams<{ rideId: string }>();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const [showCancelConfirm, setShowCancelConfirm] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);

  const { data: ride } = useQuery({
    queryKey: ["ride", rideId],
    queryFn: () => rideService.get(Number(rideId)),
    enabled: !!rideId,
    refetchInterval: POLL_INTERVAL_MS,
  });

  useRideStatusChannel(Number(rideId) || undefined, () => {
    void queryClient.invalidateQueries({ queryKey: ["ride", rideId] });
  });

  const secondsLeft = useCountdown(
    ride?.status === "SEARCHING_DRIVER" ? ride.searchingSince : null,
    RIDE_SEARCH_TIMEOUT_SECONDS,
  );

  useEffect(() => {
    if (!ride) return;
    if (ride.status === "DRIVER_ASSIGNED" || ride.status === "DRIVER_ACCEPTED") {
      navigate(`/ride/${ride.id}`, { replace: true });
    } else if (ride.status === "DRIVER_CANCELLED" || (ride.status === "CANCELLED" && ride.cancellationReason !== "NO_DRIVER_FOUND")) {
      showToast("This ride was cancelled.", "info");
      navigate("/home", { replace: true });
    }
    // CANCELLED with reason NO_DRIVER_FOUND is handled by the render below instead of an
    // immediate redirect - a plain toast is too easy to miss for something the customer needs
    // to actually notice and act on (try booking again).
  }, [ride, navigate, showToast]);

  if (ride?.status === "CANCELLED" && ride.cancellationReason === "NO_DRIVER_FOUND") {
    // No CustomerLayout chrome on this route - center the message in the full viewport ourselves.
    return (
      <div className="flex h-screen w-full items-center justify-center bg-background">
        <NoDriverFoundState />
      </div>
    );
  }

  async function cancelRide() {
    if (!rideId) return;
    setIsCancelling(true);
    try {
      await rideService.cancel(Number(rideId));
      showToast("Ride cancelled", "info");
      navigate("/home", { replace: true });
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsCancelling(false);
      setShowCancelConfirm(false);
    }
  }

  if (!ride) return <FullPageSpinner label="Loading your ride…" />;

  const center: [number, number] = [ride.pickupLatitude, ride.pickupLongitude];

  return (
    // No CustomerLayout wrapper on this route (see AppRoutes) - no navbar, no bottom nav, so
    // this page owns the entire viewport and h-screen is correct, not a calc() that subtracts
    // chrome that isn't actually there.
    <div className="relative h-screen w-full">
      <MapView center={center} zoom={14} className="absolute inset-0">
        {ride.status === "SEARCHING_DRIVER" && (
          <SearchRadarMarker latitude={ride.pickupLatitude} longitude={ride.pickupLongitude} />
        )}
        <PickupMarker latitude={ride.pickupLatitude} longitude={ride.pickupLongitude} address={ride.pickupAddress} />
        <DestinationMarker latitude={ride.destinationLatitude} longitude={ride.destinationLongitude} address={ride.destinationAddress} />
        <RouteLine
          from={[ride.pickupLatitude, ride.pickupLongitude]}
          to={[ride.destinationLatitude, ride.destinationLongitude]}
        />
      </MapView>

      <div className="pointer-events-none absolute inset-x-0 top-0 flex justify-center p-4">
        <motion.div
          initial={{ opacity: 0, y: -12 }}
          animate={{ opacity: 1, y: 0 }}
          className="pointer-events-auto flex items-center gap-3 rounded-2xl bg-white dark:bg-card px-5 py-3.5 shadow-lg"
        >
          <span className="relative flex h-9 w-9 shrink-0 items-center justify-center">
            <motion.span
              className="absolute inset-0 rounded-full bg-primary/40"
              animate={{ scale: [0.6, 1.6], opacity: [0.6, 0] }}
              transition={{ duration: 1.4, repeat: Infinity, ease: "easeOut" }}
            />
            <motion.span
              className="absolute inset-0 rounded-full bg-primary/40"
              animate={{ scale: [0.6, 1.6], opacity: [0.6, 0] }}
              transition={{ duration: 1.4, repeat: Infinity, ease: "easeOut", delay: 0.7 }}
            />
            <motion.span
              animate={{ rotate: 360 }}
              transition={{ duration: 1.6, repeat: Infinity, ease: "linear" }}
              className="relative flex h-9 w-9 items-center justify-center rounded-full bg-primary/20 text-dark"
            >
              <Search size={16} />
            </motion.span>
          </span>
          <div>
            <p className="text-sm font-bold text-dark">Finding your driver</p>
            <p className="text-xs text-muted">
              {secondsLeft !== null ? `Looking nearby - ${formatCountdown(secondsLeft)} left` : "Looking for the best driver near you"}
            </p>
          </div>
        </motion.div>
      </div>

      <div className="absolute inset-x-0 bottom-0 p-4">
        <div className="mx-auto max-w-sm">
          <Button variant="outline" className="w-full bg-white dark:bg-card" onClick={() => setShowCancelConfirm(true)}>
            <X size={16} /> Cancel Ride
          </Button>
        </div>
      </div>

      <ConfirmDialog
        open={showCancelConfirm}
        title="Cancel ride?"
        description="We'll stop looking for a driver for this trip."
        confirmLabel="Cancel Ride"
        cancelLabel="Keep Ride"
        destructive
        isLoading={isCancelling}
        onConfirm={cancelRide}
        onCancel={() => setShowCancelConfirm(false)}
      />
    </div>
  );
}
