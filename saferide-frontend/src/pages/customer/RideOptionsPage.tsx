import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useQueries } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { ArrowLeft, Car, Users } from "lucide-react";
import { useBooking } from "@/contexts/BookingContext";
import { fareService } from "@/services/fareService";
import { Button } from "@/components/common/Button";
import { Skeleton } from "@/components/common/Skeleton";
import { ErrorState } from "@/components/common/StateViews";
import { cn } from "@/lib/utils";
import type { VehicleType } from "@/types/ride";

const VEHICLES: { type: VehicleType; label: string; seats: number; icon: typeof Car }[] = [
  { type: "MINI", label: "Mini", seats: 4, icon: Car },
  { type: "SEDAN", label: "Sedan", seats: 4, icon: Car },
  { type: "SUV", label: "SUV", seats: 6, icon: Users },
];

export default function RideOptionsPage() {
  const { pickup, destination, distanceKm, estimatedDurationMinutes, vehicleType, setVehicleType, isReadyForOptions } = useBooking();
  const navigate = useNavigate();

  useEffect(() => {
    if (!isReadyForOptions) navigate("/home", { replace: true });
  }, [isReadyForOptions, navigate]);

  const fareQueries = useQueries({
    queries: VEHICLES.map(({ type }) => ({
      queryKey: ["fare-preview", type, distanceKm, estimatedDurationMinutes],
      queryFn: () =>
        fareService.calculate({
          distanceKm: distanceKm!,
          estimatedDurationMinutes: estimatedDurationMinutes!,
          vehicleType: type,
        }),
      enabled: distanceKm !== null && estimatedDurationMinutes !== null,
    })),
  });

  const hasError = fareQueries.some((q) => q.isError);

  if (!pickup || !destination || distanceKm === null) return null;

  return (
    <div className="mx-auto max-w-2xl px-4 py-6 sm:px-6">
      <button onClick={() => navigate("/home")} className="mb-4 flex items-center gap-1.5 text-sm font-medium text-muted hover:text-foreground">
        <ArrowLeft size={16} /> Back
      </button>

      <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <p className="text-xs font-semibold uppercase tracking-wide text-muted">Trip</p>
        <div className="mt-2 space-y-1 text-sm">
          <p className="truncate font-medium text-foreground">{pickup.label}</p>
          <p className="truncate text-muted">→ {destination.label}</p>
        </div>
        <div className="mt-3 flex gap-4 text-xs text-muted">
          <span>{distanceKm.toFixed(1)} km</span>
          <span>•</span>
          <span>{estimatedDurationMinutes} min ETA</span>
        </div>
      </div>

      <h2 className="mb-3 mt-6 text-lg font-bold text-dark">Choose a ride</h2>

      {hasError ? (
        <ErrorState title="Couldn't load fares" description="Please try again." onAction={() => window.location.reload()} />
      ) : (
        <div className="space-y-3">
          {VEHICLES.map(({ type, label, seats, icon: Icon }, index) => {
            const query = fareQueries[index];
            const isSelected = vehicleType === type;
            return (
              <motion.button
                key={type}
                type="button"
                onClick={() => setVehicleType(type)}
                whileTap={{ scale: 0.98 }}
                className={cn(
                  "flex w-full items-center gap-4 rounded-2xl border-2 bg-white p-4 text-left transition-all",
                  isSelected ? "border-primary bg-primary/5 shadow-md" : "border-slate-200 hover:border-slate-300",
                )}
              >
                <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-dark">
                  <Icon size={26} />
                </div>
                <div className="flex-1">
                  <p className="font-semibold text-foreground">{label}</p>
                  <p className="text-xs text-muted">{seats} seats · {estimatedDurationMinutes} min</p>
                </div>
                <div className="text-right">
                  {query.isPending ? (
                    <Skeleton className="h-6 w-14" />
                  ) : query.isError ? (
                    <span className="text-xs text-danger">—</span>
                  ) : (
                    <p className="text-lg font-bold text-dark">₹{query.data!.totalFare.toFixed(0)}</p>
                  )}
                </div>
              </motion.button>
            );
          })}
        </div>
      )}

      <Button
        className="mt-6 w-full"
        size="lg"
        disabled={!vehicleType}
        onClick={() => navigate("/fare-breakdown")}
      >
        Continue
      </Button>
    </div>
  );
}
