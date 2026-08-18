import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { ArrowLeft, Lock } from "lucide-react";
import { useBooking } from "@/contexts/BookingContext";
import { useToast } from "@/contexts/ToastContext";
import { fareService } from "@/services/fareService";
import { rideService } from "@/services/rideService";
import { Button } from "@/components/common/Button";
import { Skeleton } from "@/components/common/Skeleton";
import { extractErrorMessage } from "@/services/api";

export default function FareBreakdownPage() {
  const { pickup, destination, distanceKm, estimatedDurationMinutes, vehicleType, isReadyForFareBreakdown, reset } = useBooking();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [isBooking, setIsBooking] = useState(false);

  useEffect(() => {
    if (!isReadyForFareBreakdown) navigate("/home", { replace: true });
  }, [isReadyForFareBreakdown, navigate]);

  const { data: fare, isPending, isError } = useQuery({
    queryKey: ["fare-preview", vehicleType, distanceKm, estimatedDurationMinutes],
    queryFn: () =>
      fareService.calculate({
        distanceKm: distanceKm!,
        estimatedDurationMinutes: estimatedDurationMinutes!,
        vehicleType: vehicleType!,
      }),
    enabled: isReadyForFareBreakdown,
  });

  async function confirmAndBook() {
    if (!pickup || !destination || !vehicleType) return;
    setIsBooking(true);
    try {
      const ride = await rideService.create({
        pickupAddress: pickup.address,
        destinationAddress: destination.address,
        pickupLatitude: pickup.latitude,
        pickupLongitude: pickup.longitude,
        destinationLatitude: destination.latitude,
        destinationLongitude: destination.longitude,
        vehicleType,
      });
      // Locks the fare and is what actually starts the driver search - see RideService's Fare Lock note.
      await fareService.confirmFare(ride.id);
      reset();
      navigate(`/finding-driver/${ride.id}`, { replace: true });
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
      setIsBooking(false);
    }
  }

  if (!pickup || !destination) return null;

  return (
    <div className="mx-auto max-w-md px-4 py-6 sm:px-6">
      <button
        onClick={() => navigate("/ride-options")}
        className="mb-4 flex items-center gap-1.5 text-sm font-medium text-muted hover:text-foreground"
        disabled={isBooking}
      >
        <ArrowLeft size={16} /> Back
      </button>

      <h1 className="text-2xl font-extrabold text-dark">Fare breakdown</h1>
      <p className="mt-1 text-sm text-muted">Review your fare before confirming.</p>

      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        className="mt-5 rounded-2xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-5 shadow-sm"
      >
        {isPending ? (
          <div className="space-y-3">
            {[...Array(5)].map((_, i) => (
              <Skeleton key={i} className="h-5 w-full" />
            ))}
          </div>
        ) : isError || !fare ? (
          <p className="text-sm text-danger">Couldn't load the fare. Please go back and try again.</p>
        ) : (
          <>
            <dl className="space-y-2.5 text-sm">
              <Row label="Base Fare" value={fare.baseFare} />
              <Row label="Distance" value={fare.distanceFare} />
              <Row label="Time" value={fare.timeFare} />
              <Row label="Platform Fee" value={fare.platformFee} />
              {fare.discount > 0 && <Row label="Discount" value={-fare.discount} highlight="text-success" />}
            </dl>
            <div className="my-4 border-t border-dashed border-slate-200 dark:border-card-border" />
            <div className="flex items-center justify-between">
              <span className="text-base font-bold text-dark">Total</span>
              <span className="text-2xl font-extrabold text-dark">₹{fare.totalFare.toFixed(0)}</span>
            </div>

            <div className="mt-5 flex items-center gap-2 rounded-xl bg-success/10 px-3.5 py-3 text-sm font-medium text-emerald-800">
              <Lock size={16} />
              Fare Locked once you confirm — no unexpected extra charges.
            </div>
          </>
        )}
      </motion.div>

      <div className="mt-6 flex gap-3">
        <Button variant="outline" className="flex-1" onClick={() => navigate("/ride-options")} disabled={isBooking}>
          Back
        </Button>
        <Button className="flex-1" onClick={confirmAndBook} isLoading={isBooking} disabled={isPending || isError}>
          Confirm & Book Ride
        </Button>
      </div>
    </div>
  );
}

function Row({ label, value, highlight }: { label: string; value: number; highlight?: string }) {
  return (
    <div className="flex items-center justify-between">
      <dt className="text-muted">{label}</dt>
      <dd className={highlight ?? "font-medium text-foreground"}>
        {value < 0 ? "-" : ""}₹{Math.abs(value).toFixed(0)}
      </dd>
    </div>
  );
}
