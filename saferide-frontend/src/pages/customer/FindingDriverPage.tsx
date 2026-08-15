import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Search, X } from "lucide-react";
import { rideService } from "@/services/rideService";
import { useToast } from "@/contexts/ToastContext";
import { useRideStatusChannel } from "@/hooks/useRideChannel";
import { MapView } from "@/components/map/MapView";
import { PickupMarker } from "@/components/map/PickupMarker";
import { DestinationMarker } from "@/components/map/DestinationMarker";
import { RouteLine } from "@/components/map/RouteLine";
import { Button } from "@/components/common/Button";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { extractErrorMessage } from "@/services/api";
import { FullPageSpinner } from "@/components/common/Spinner";

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

  useEffect(() => {
    if (!ride) return;
    if (ride.status === "DRIVER_ASSIGNED" || ride.status === "DRIVER_ACCEPTED") {
      navigate(`/ride/${ride.id}`, { replace: true });
    } else if (ride.status === "CANCELLED" || ride.status === "DRIVER_CANCELLED") {
      showToast("This ride was cancelled.", "info");
      navigate("/home", { replace: true });
    }
  }, [ride, navigate, showToast]);

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
    <div className="relative h-[calc(100vh-4rem)] w-full">
      <MapView center={center} zoom={14} className="absolute inset-0">
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
          className="pointer-events-auto flex items-center gap-3 rounded-2xl bg-white px-5 py-3.5 shadow-lg"
        >
          <motion.span
            animate={{ rotate: 360 }}
            transition={{ duration: 1.6, repeat: Infinity, ease: "linear" }}
            className="flex h-9 w-9 items-center justify-center rounded-full bg-primary/20 text-dark"
          >
            <Search size={16} />
          </motion.span>
          <div>
            <p className="text-sm font-bold text-dark">Finding your driver</p>
            <p className="text-xs text-muted">Looking for the best driver near you</p>
          </div>
        </motion.div>
      </div>

      <div className="absolute inset-x-0 bottom-0 p-4">
        <div className="mx-auto max-w-sm">
          <Button variant="outline" className="w-full bg-white" onClick={() => setShowCancelConfirm(true)}>
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
