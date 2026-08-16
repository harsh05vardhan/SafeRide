import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Navigation, MapPin, Phone, CheckCircle2, XCircle, KeyRound, MessageSquareWarning } from "lucide-react";
import { rideService } from "@/services/rideService";
import { fareService } from "@/services/fareService";
import { driverService } from "@/services/driverService";
import { useToast } from "@/contexts/ToastContext";
import { useRideStatusChannel } from "@/hooks/useRideChannel";
import { MapView } from "@/components/map/MapView";
import { PickupMarker } from "@/components/map/PickupMarker";
import { DestinationMarker } from "@/components/map/DestinationMarker";
import { RouteLine } from "@/components/map/RouteLine";
import { Button } from "@/components/common/Button";
import { Input } from "@/components/common/Input";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { FullPageSpinner } from "@/components/common/Spinner";
import { IncomingRequestCard } from "@/components/driver/IncomingRequestCard";
import { ReportIssueDialog } from "@/components/ride/ReportIssueDialog";
import { extractErrorMessage } from "@/services/api";
import { ACTIVE_TRACKING_STATUSES } from "@/types/ride";

/** Fallback cadence in case the WebSocket connection is down/reconnecting - most transitions on this page are driver-initiated and self-refresh immediately (see refresh() below); this mainly catches a customer cancelling from their side. */
const POLL_INTERVAL_MS = 10000;

export default function DriverRideDetailPage() {
  const { rideId } = useParams<{ rideId: string }>();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const [otp, setOtp] = useState("");
  const [isActing, setIsActing] = useState(false);
  const [showCancelConfirm, setShowCancelConfirm] = useState(false);
  const [showReportDialog, setShowReportDialog] = useState(false);

  const { data: ride, isPending } = useQuery({
    queryKey: ["ride", rideId],
    queryFn: () => rideService.get(Number(rideId)),
    enabled: !!rideId,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && ACTIVE_TRACKING_STATUSES.includes(status) ? POLL_INTERVAL_MS : false;
    },
  });

  useRideStatusChannel(Number(rideId) || undefined, () => {
    void queryClient.invalidateQueries({ queryKey: ["ride", rideId] });
  });

  // Reports this driver's live position every few seconds for the whole time they're actually
  // committed to the trip - PUT /api/drivers/location broadcasts it straight to the customer's
  // map (see RideTrackingPage) with no further action needed here.
  useEffect(() => {
    if (!ride || !navigator.geolocation) return;
    if (!["DRIVER_ACCEPTED", "DRIVER_ARRIVED", "RIDE_STARTED"].includes(ride.status)) return;

    const reportPosition = () => {
      navigator.geolocation.getCurrentPosition((position) => {
        void driverService.updateLocation({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        });
      });
    };
    reportPosition();
    const interval = setInterval(reportPosition, 5000);
    return () => clearInterval(interval);
    // Deliberately keyed on status alone - `ride` gets a new object identity on every poll/
    // refetch even when nothing relevant changed, which would otherwise restart this interval constantly.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ride?.status]);

  const isFinished = !!ride && ["RIDE_COMPLETED", "PAYMENT_PENDING", "PAYMENT_COMPLETED", "RATED"].includes(ride.status);
  const { data: fare } = useQuery({
    queryKey: ["ride-fare", rideId],
    queryFn: () => fareService.getRideFare(Number(rideId)),
    enabled: !!rideId && !!ride,
  });

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ["ride", rideId] });
    await queryClient.invalidateQueries({ queryKey: ["driver-rides"] });
  }

  async function accept() {
    if (!rideId) return;
    setIsActing(true);
    try {
      await rideService.accept(Number(rideId));
      showToast("Ride accepted", "success");
      await refresh();
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsActing(false);
    }
  }

  async function decline() {
    if (!rideId) return;
    setIsActing(true);
    try {
      await rideService.decline(Number(rideId));
      showToast("Ride declined", "info");
      navigate("/driver/dashboard", { replace: true });
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsActing(false);
    }
  }

  async function markArrived() {
    if (!rideId) return;
    setIsActing(true);
    try {
      await rideService.arrive(Number(rideId));
      showToast("Marked as arrived", "success");
      await refresh();
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsActing(false);
    }
  }

  async function startRide() {
    if (!rideId) return;
    setIsActing(true);
    try {
      await rideService.start(Number(rideId), otp);
      showToast("Ride started", "success");
      setOtp("");
      await refresh();
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsActing(false);
    }
  }

  async function completeRide() {
    if (!rideId) return;
    setIsActing(true);
    try {
      await rideService.complete(Number(rideId));
      showToast("Ride completed", "success");
      await refresh();
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsActing(false);
    }
  }

  async function cancelRide() {
    if (!rideId) return;
    setIsActing(true);
    try {
      await rideService.cancel(Number(rideId));
      showToast("Ride cancelled", "info");
      await refresh();
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsActing(false);
      setShowCancelConfirm(false);
    }
  }

  if (isPending || !ride) return <FullPageSpinner label="Loading ride…" />;

  const isCancelled = ride.status === "CANCELLED" || ride.status === "DRIVER_CANCELLED";

  if (ride.status === "DRIVER_ASSIGNED") {
    return (
      <div className="mx-auto max-w-md px-4 py-8">
        <IncomingRequestCard ride={ride} onAccept={accept} onDecline={decline} />
      </div>
    );
  }

  if (isCancelled) {
    return (
      <div className="mx-auto flex max-w-md flex-col items-center px-4 py-16 text-center">
        <XCircle size={48} className="mb-3 text-danger" />
        <h1 className="text-xl font-bold text-dark">Ride cancelled</h1>
        <Button className="mt-6" onClick={() => navigate("/driver/dashboard")}>
          Back to dashboard
        </Button>
      </div>
    );
  }

  if (isFinished) {
    return (
      <div className="mx-auto max-w-md px-4 py-8 text-center">
        <CheckCircle2 size={48} className="mx-auto mb-3 text-success" />
        <h1 className="text-xl font-bold text-dark">Ride completed</h1>
        <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 text-left shadow-sm">
          <p className="truncate text-sm text-foreground">{ride.pickupAddress}</p>
          <p className="truncate text-sm text-muted">→ {ride.destinationAddress}</p>
          <div className="my-4 border-t border-dashed border-slate-200" />
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-muted">Fare earned</span>
            <span className="text-xl font-extrabold text-dark">{fare ? `₹${fare.totalFare.toFixed(0)}` : "—"}</span>
          </div>
        </div>
        <button
          className="mt-4 flex w-full items-center justify-center gap-1.5 text-xs font-semibold text-muted hover:text-foreground"
          onClick={() => setShowReportDialog(true)}
        >
          <MessageSquareWarning size={14} /> Report an issue with this ride
        </button>
        <Button className="mt-4 w-full" onClick={() => navigate("/driver/dashboard")}>
          Back to dashboard
        </Button>
        <ReportIssueDialog rideId={ride.id} open={showReportDialog} onClose={() => setShowReportDialog(false)} />
      </div>
    );
  }

  const center: [number, number] = [ride.pickupLatitude, ride.pickupLongitude];
  const canCancel = ride.status === "DRIVER_ACCEPTED" || ride.status === "DRIVER_ARRIVED";

  return (
    <div className="relative h-[calc(100vh-4rem)] w-full">
      <MapView center={center} zoom={14} className="absolute inset-0">
        <PickupMarker latitude={ride.pickupLatitude} longitude={ride.pickupLongitude} address={ride.pickupAddress} />
        <DestinationMarker latitude={ride.destinationLatitude} longitude={ride.destinationLongitude} address={ride.destinationAddress} />
        <RouteLine from={[ride.pickupLatitude, ride.pickupLongitude]} to={[ride.destinationLatitude, ride.destinationLongitude]} />
      </MapView>

      <div className="absolute inset-x-0 top-0 p-4">
        <div className="mx-auto max-w-md rounded-2xl bg-white p-4 shadow-lg">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted">{statusLabel(ride.status)}</p>
          <p className="mt-1 font-semibold text-foreground">{ride.customer.name}</p>
          <div className="mt-2 space-y-1.5 text-sm">
            <p className="flex items-start gap-2">
              <Navigation size={13} className="mt-0.5 shrink-0 text-dark" />
              <span className="truncate text-foreground">{ride.pickupAddress}</span>
            </p>
            <p className="flex items-start gap-2">
              <MapPin size={13} className="mt-0.5 shrink-0 text-danger" />
              <span className="truncate text-foreground">{ride.destinationAddress}</span>
            </p>
          </div>
          {fare && (
            <div className="mt-2 flex items-center justify-between border-t border-slate-100 pt-2 text-sm">
              <span className="text-muted">Locked fare</span>
              <span className="font-bold text-dark">₹{fare.totalFare.toFixed(0)}</span>
            </div>
          )}
        </div>
      </div>

      <div className="absolute inset-x-0 bottom-0 p-4">
        <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="mx-auto max-w-md space-y-3">
          {ride.status === "DRIVER_ACCEPTED" && (
            <div className="rounded-2xl bg-white p-4 shadow-lg">
              <p className="mb-3 text-sm text-muted">Head to the pickup point, then mark yourself as arrived.</p>
              <div className="flex gap-2">
                <button
                  className="flex h-11 w-11 items-center justify-center rounded-xl bg-slate-100 text-dark"
                  onClick={() => showToast("Calling is coming soon.", "info")}
                  aria-label="Call customer"
                >
                  <Phone size={18} />
                </button>
                <Button className="flex-1" size="lg" onClick={markArrived} isLoading={isActing}>
                  Mark Arrived
                </Button>
              </div>
            </div>
          )}

          {ride.status === "DRIVER_ARRIVED" && (
            <div className="rounded-2xl bg-white p-4 shadow-lg">
              <p className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-dark">
                <KeyRound size={15} /> Enter the customer's OTP
              </p>
              <div className="flex gap-2">
                <Input
                  value={otp}
                  onChange={(e) => setOtp(e.target.value.replace(/\D/g, "").slice(0, 4))}
                  placeholder="0000"
                  inputMode="numeric"
                  className="text-center text-lg tracking-[0.5em]"
                  maxLength={4}
                />
                <Button onClick={startRide} isLoading={isActing} disabled={otp.length !== 4}>
                  Start Ride
                </Button>
              </div>
            </div>
          )}

          {ride.status === "RIDE_STARTED" && (
            <div className="rounded-2xl bg-white p-4 shadow-lg">
              <p className="mb-3 text-sm text-muted">Trip in progress. Complete it once you reach the destination.</p>
              <Button className="w-full" size="lg" onClick={completeRide} isLoading={isActing}>
                Complete Ride
              </Button>
            </div>
          )}

          {canCancel && (
            <button
              className="w-full rounded-xl bg-white py-2.5 text-xs font-semibold text-danger shadow"
              onClick={() => setShowCancelConfirm(true)}
            >
              Cancel ride
            </button>
          )}
        </motion.div>
      </div>

      <ConfirmDialog
        open={showCancelConfirm}
        title="Cancel this ride?"
        description="This will count against your cancellation rate and the ride will be reassigned to another driver."
        confirmLabel="Cancel Ride"
        cancelLabel="Keep Ride"
        destructive
        isLoading={isActing}
        onConfirm={cancelRide}
        onCancel={() => setShowCancelConfirm(false)}
      />
    </div>
  );
}

function statusLabel(status: string): string {
  switch (status) {
    case "DRIVER_ACCEPTED":
      return "Heading to pickup";
    case "DRIVER_ARRIVED":
      return "Waiting for customer";
    case "RIDE_STARTED":
      return "Ride in progress";
    default:
      return status;
  }
}
