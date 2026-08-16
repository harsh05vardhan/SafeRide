import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import {
  AlertOctagon,
  Phone,
  MessageCircle,
  Star,
  ShieldAlert,
  X,
  CheckCircle2,
  XCircle,
  MessageSquareWarning,
} from "lucide-react";
import { rideService } from "@/services/rideService";
import { fareService } from "@/services/fareService";
import { paymentService } from "@/services/paymentService";
import { ratingService } from "@/services/ratingService";
import { safetyService } from "@/services/safetyService";
import { useToast } from "@/contexts/ToastContext";
import { useRideLocationChannel, useRideStatusChannel } from "@/hooks/useRideChannel";
import { MapView } from "@/components/map/MapView";
import { PickupMarker } from "@/components/map/PickupMarker";
import { DestinationMarker } from "@/components/map/DestinationMarker";
import { DriverMarker } from "@/components/map/DriverMarker";
import { RouteLine } from "@/components/map/RouteLine";
import { Button } from "@/components/common/Button";
import { Badge } from "@/components/common/Badge";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { FullPageSpinner } from "@/components/common/Spinner";
import { RatingStars } from "@/components/ride/RatingStars";
import { ShareRideDialog } from "@/components/ride/ShareRideDialog";
import { ReportIssueDialog } from "@/components/ride/ReportIssueDialog";
import { extractErrorMessage } from "@/services/api";
import { ACTIVE_TRACKING_STATUSES, type RideStatus } from "@/types/ride";

/** Fallback cadence in case the WebSocket connection is down/reconnecting - useRideStatusChannel below is what normally keeps this page current within a second or two of any change. */
const POLL_INTERVAL_MS = 10000;

const STATUS_LABEL: Record<RideStatus, string> = {
  REQUESTED: "Requesting…",
  SEARCHING_DRIVER: "Finding your driver…",
  DRIVER_ASSIGNED: "Driver assigned",
  DRIVER_ACCEPTED: "Driver is on the way",
  DRIVER_ARRIVED: "Your driver has arrived",
  RIDE_STARTED: "Ride in progress",
  RIDE_COMPLETED: "Ride completed",
  PAYMENT_PENDING: "Payment pending",
  PAYMENT_COMPLETED: "Payment completed",
  RATED: "Trip rated",
  CANCELLED: "Ride cancelled",
  DRIVER_CANCELLED: "Driver cancelled",
};

export default function RideTrackingPage() {
  const { rideId } = useParams<{ rideId: string }>();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const [showCancelConfirm, setShowCancelConfirm] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  const [driverLocation, setDriverLocation] = useState<{ latitude: number; longitude: number } | null>(null);
  const [showSosConfirm, setShowSosConfirm] = useState(false);
  const [isSendingSos, setIsSendingSos] = useState(false);
  const [showShareDialog, setShowShareDialog] = useState(false);
  const [showReportDialog, setShowReportDialog] = useState(false);
  const [isPaying, setIsPaying] = useState(false);
  const [ratingScore, setRatingScore] = useState(0);
  const [ratingComment, setRatingComment] = useState("");
  const [isSubmittingRating, setIsSubmittingRating] = useState(false);

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

  // No REST round trip needed for this one - unlike status, a location has no customer/driver
  // asymmetry to protect, so the event carries the real coordinates directly (see DriverLocationEvent).
  useRideLocationChannel(Number(rideId) || undefined, (event) => {
    setDriverLocation({ latitude: event.latitude, longitude: event.longitude });
  });

  async function cancelRide() {
    if (!rideId) return;
    setIsCancelling(true);
    try {
      await rideService.cancel(Number(rideId));
      showToast("Ride cancelled", "info");
      await queryClient.invalidateQueries({ queryKey: ["ride", rideId] });
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsCancelling(false);
      setShowCancelConfirm(false);
    }
  }

  async function sendSos() {
    if (!rideId || !navigator.geolocation) return;
    setIsSendingSos(true);
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        try {
          await safetyService.triggerSos(Number(rideId), {
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
          });
          showToast("Alert sent - our safety team has been notified", "success");
        } catch (error) {
          showToast(extractErrorMessage(error), "error");
        } finally {
          setIsSendingSos(false);
          setShowSosConfirm(false);
        }
      },
      () => {
        showToast("Couldn't get your location - please enable location access and try again", "error");
        setIsSendingSos(false);
      },
    );
  }

  async function payNow() {
    if (!rideId) return;
    setIsPaying(true);
    try {
      await paymentService.pay(Number(rideId));
      showToast("Payment successful", "success");
      await queryClient.invalidateQueries({ queryKey: ["ride", rideId] });
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsPaying(false);
    }
  }

  async function submitRating() {
    if (!rideId || ratingScore === 0) return;
    setIsSubmittingRating(true);
    try {
      await ratingService.rate(Number(rideId), { score: ratingScore, comment: ratingComment.trim() || undefined });
      showToast("Thanks for your feedback", "success");
      await queryClient.invalidateQueries({ queryKey: ["ride", rideId] });
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsSubmittingRating(false);
    }
  }

  const isFinished = !!ride && ["RIDE_COMPLETED", "PAYMENT_PENDING", "PAYMENT_COMPLETED", "RATED"].includes(ride.status);

  const { data: fare } = useQuery({
    queryKey: ["ride-fare", rideId],
    queryFn: () => fareService.getRideFare(Number(rideId)),
    enabled: !!rideId && isFinished,
  });

  const { data: submittedRating } = useQuery({
    queryKey: ["rating", rideId],
    queryFn: () => ratingService.getForRide(Number(rideId)),
    enabled: !!rideId && ride?.status === "RATED",
  });

  if (isPending || !ride) return <FullPageSpinner label="Loading your ride…" />;
  const isCancelled = ride.status === "CANCELLED" || ride.status === "DRIVER_CANCELLED";
  // Matches RideStateMachine's own graph exactly (REQUESTED/SEARCHING_DRIVER -> CANCELLED is
  // legal there too) - reachable here whenever a still-searching ride is opened from My Rides
  // or the home page's recent-rides list rather than the fresh-booking flow (which shows
  // FindingDriverPage's own cancel button first and only lands here once a driver is assigned).
  const canCancel = ["REQUESTED", "SEARCHING_DRIVER", "DRIVER_ASSIGNED", "DRIVER_ACCEPTED", "DRIVER_ARRIVED"].includes(
    ride.status,
  );
  const canSendSafetySignals = ["DRIVER_ACCEPTED", "DRIVER_ARRIVED", "RIDE_STARTED"].includes(ride.status);
  const center: [number, number] = [ride.pickupLatitude, ride.pickupLongitude];

  if (isCancelled) {
    return (
      <div className="mx-auto flex max-w-md flex-col items-center px-4 py-16 text-center">
        <XCircle size={48} className="mb-3 text-danger" />
        <h1 className="text-xl font-bold text-dark">{STATUS_LABEL[ride.status]}</h1>
        <p className="mt-1 text-sm text-muted">This trip did not go ahead.</p>
        <Button className="mt-6" onClick={() => navigate("/home")}>
          Book another ride
        </Button>
      </div>
    );
  }

  if (isFinished) {
    return (
      <div className="mx-auto max-w-md px-4 py-8">
        <div className="flex flex-col items-center text-center">
          <CheckCircle2 size={48} className="mb-3 text-success" />
          <h1 className="text-xl font-bold text-dark">Ride completed</h1>
          <p className="mt-1 text-sm text-muted">Hope you had a safe trip!</p>
        </div>

        <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <RouteSummary pickup={ride.pickupAddress} destination={ride.destinationAddress} />
          <div className="my-4 border-t border-dashed border-slate-200" />
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-muted">Fare</span>
            <span className="text-xl font-extrabold text-dark">{fare ? `₹${fare.totalFare.toFixed(0)}` : "—"}</span>
          </div>
          <p className="mt-2 text-xs text-muted">
            Locked before the ride began - this is exactly what was agreed, nothing more.
          </p>
        </div>

        {ride.status === "PAYMENT_PENDING" && (
          <div className="mt-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <Button className="w-full" size="lg" onClick={payNow} isLoading={isPaying}>
              Pay {fare ? `₹${fare.totalFare.toFixed(0)}` : "now"}
            </Button>
          </div>
        )}

        {ride.status === "PAYMENT_COMPLETED" && (
          <div className="mt-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <p className="text-sm font-semibold text-dark">Rate your driver</p>
            <div className="mt-3 flex justify-center">
              <RatingStars value={ratingScore} onChange={setRatingScore} />
            </div>
            <textarea
              value={ratingComment}
              onChange={(e) => setRatingComment(e.target.value)}
              rows={3}
              maxLength={500}
              placeholder="Anything you'd like to add? (optional)"
              className="mt-3 w-full resize-none rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-foreground placeholder:text-muted focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/50"
            />
            <Button className="mt-3 w-full" onClick={submitRating} isLoading={isSubmittingRating} disabled={ratingScore === 0}>
              Submit rating
            </Button>
          </div>
        )}

        {ride.status === "RATED" && submittedRating && (
          <div className="mt-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <p className="text-sm font-semibold text-dark">Your rating</p>
            <div className="mt-2 flex items-center gap-1">
              {[1, 2, 3, 4, 5].map((star) => (
                <Star
                  key={star}
                  size={20}
                  className={star <= submittedRating.score ? "fill-primary text-primary" : "fill-transparent text-slate-300"}
                />
              ))}
            </div>
            {submittedRating.comment && <p className="mt-2 text-sm text-muted">"{submittedRating.comment}"</p>}
          </div>
        )}

        <button
          className="mt-4 flex w-full items-center justify-center gap-1.5 text-xs font-semibold text-muted hover:text-foreground"
          onClick={() => setShowReportDialog(true)}
        >
          <MessageSquareWarning size={14} /> Report an issue with this ride
        </button>

        <Button className="mt-4 w-full" variant="outline" onClick={() => navigate("/rides")}>
          View my rides
        </Button>

        <ReportIssueDialog rideId={ride.id} open={showReportDialog} onClose={() => setShowReportDialog(false)} />
      </div>
    );
  }

  return (
    <div className="relative h-[calc(100vh-4rem)] w-full">
      <MapView center={center} zoom={14} className="absolute inset-0">
        <PickupMarker latitude={ride.pickupLatitude} longitude={ride.pickupLongitude} address={ride.pickupAddress} />
        <DestinationMarker latitude={ride.destinationLatitude} longitude={ride.destinationLongitude} address={ride.destinationAddress} />
        <RouteLine from={[ride.pickupLatitude, ride.pickupLongitude]} to={[ride.destinationLatitude, ride.destinationLongitude]} />
        {driverLocation && ride.driver && (
          <DriverMarker latitude={driverLocation.latitude} longitude={driverLocation.longitude} label={ride.driver.name} />
        )}
      </MapView>

      {/* Top bar */}
      <div className="pointer-events-none absolute inset-x-0 top-0 flex items-center justify-between p-4">
        <Badge variant="dark" className="pointer-events-auto">
          {STATUS_LABEL[ride.status]}
        </Badge>
        {canSendSafetySignals && (
          <button
            className="pointer-events-auto flex items-center gap-1.5 rounded-full bg-danger px-4 py-2 text-xs font-bold text-white shadow-lg"
            onClick={() => setShowSosConfirm(true)}
          >
            <AlertOctagon size={14} /> SOS
          </button>
        )}
      </div>

      {/* OTP banner while waiting for the driver to arrive-confirm */}
      {ride.status === "DRIVER_ARRIVED" && ride.otp && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="absolute inset-x-0 top-16 flex justify-center px-4"
        >
          <div className="rounded-2xl bg-white px-6 py-4 text-center shadow-lg">
            <p className="text-xs font-semibold uppercase tracking-wide text-muted">Share this OTP with your driver</p>
            <p className="mt-1 text-3xl font-extrabold tracking-[0.4em] text-dark">{ride.otp}</p>
          </div>
        </motion.div>
      )}

      {/* Bottom driver card */}
      <div className="absolute inset-x-0 bottom-0 p-4">
        <div className="mx-auto max-w-md space-y-3">
          {ride.driver && (
            <div className="rounded-2xl bg-white p-4 shadow-lg">
              <div className="flex items-center gap-3">
                <div className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-lg font-bold text-dark">
                  {ride.driver.name.charAt(0)}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold text-foreground">{ride.driver.name}</p>
                  <p className="flex items-center gap-1 text-xs text-muted">
                    <Star size={12} className="fill-primary text-primary" /> {ride.driver.rating.toFixed(1)} ·{" "}
                    {ride.driver.vehicleModel} · {ride.driver.vehicleNumber}
                  </p>
                </div>
                <button
                  className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-100 text-dark"
                  onClick={() => showToast("Calling is coming soon.", "info")}
                  aria-label="Call driver"
                >
                  <Phone size={16} />
                </button>
                <button
                  className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-100 text-dark"
                  onClick={() => showToast("Chat is coming soon.", "info")}
                  aria-label="Chat with driver"
                >
                  <MessageCircle size={16} />
                </button>
              </div>
              <RouteSummary pickup={ride.pickupAddress} destination={ride.destinationAddress} compact />
            </div>
          )}

          <div className="flex gap-2">
            <button
              className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-white py-2.5 text-xs font-semibold text-foreground shadow"
              onClick={() => setShowShareDialog(true)}
            >
              <ShieldAlert size={14} /> Share Ride
            </button>
            {canCancel && (
              <button
                className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-white py-2.5 text-xs font-semibold text-danger shadow"
                onClick={() => setShowCancelConfirm(true)}
              >
                <X size={14} /> Cancel
              </button>
            )}
          </div>
        </div>
      </div>

      <ConfirmDialog
        open={showCancelConfirm}
        title="Cancel ride?"
        description="Your driver has already been notified - cancelling now may affect their schedule."
        confirmLabel="Cancel Ride"
        cancelLabel="Keep Ride"
        destructive
        isLoading={isCancelling}
        onConfirm={cancelRide}
        onCancel={() => setShowCancelConfirm(false)}
      />

      <ConfirmDialog
        open={showSosConfirm}
        title="Send an SOS alert?"
        description="Your current location will be shared with our safety team immediately."
        confirmLabel="Send Alert"
        cancelLabel="Cancel"
        destructive
        isLoading={isSendingSos}
        onConfirm={sendSos}
        onCancel={() => setShowSosConfirm(false)}
      />

      <ShareRideDialog rideId={ride.id} open={showShareDialog} onClose={() => setShowShareDialog(false)} />
    </div>
  );
}

function RouteSummary({ pickup, destination, compact }: { pickup: string; destination: string; compact?: boolean }) {
  return (
    <div className={compact ? "mt-3 space-y-1 border-t border-slate-100 pt-3 text-xs" : "space-y-1.5 text-sm"}>
      <p className="flex items-start gap-2">
        <span className="mt-0.5 h-2 w-2 shrink-0 rounded-full bg-dark" />
        <span className="truncate text-foreground">{pickup}</span>
      </p>
      <p className="flex items-start gap-2">
        <span className="mt-0.5 h-2 w-2 shrink-0 rounded-full bg-danger" />
        <span className="truncate text-foreground">{destination}</span>
      </p>
    </div>
  );
}
