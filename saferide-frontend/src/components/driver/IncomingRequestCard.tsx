import { useState } from "react";
import { motion } from "framer-motion";
import { useQuery } from "@tanstack/react-query";
import { Navigation, MapPin, Check, X } from "lucide-react";
import { fareService } from "@/services/fareService";
import { Button } from "@/components/common/Button";
import { Skeleton } from "@/components/common/Skeleton";
import type { Ride } from "@/types/ride";

interface IncomingRequestCardProps {
  ride: Ride;
  onAccept: () => Promise<void>;
  onDecline: () => Promise<void>;
}

/** A ride matching has proactively assigned to this driver (DRIVER_ASSIGNED) - they must explicitly accept or decline. */
export function IncomingRequestCard({ ride, onAccept, onDecline }: IncomingRequestCardProps) {
  const [busy, setBusy] = useState<"accept" | "decline" | null>(null);

  const { data: fare } = useQuery({
    queryKey: ["ride-fare", ride.id],
    queryFn: () => fareService.getRideFare(ride.id),
  });

  async function handleAccept() {
    setBusy("accept");
    try {
      await onAccept();
    } finally {
      setBusy(null);
    }
  }

  async function handleDecline() {
    setBusy("decline");
    try {
      await onDecline();
    } finally {
      setBusy(null);
    }
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0 }}
      className="overflow-hidden rounded-2xl border-2 border-primary bg-white dark:bg-card shadow-md"
    >
      <div className="flex items-center justify-between bg-primary/15 px-4 py-2.5">
        <p className="text-sm font-bold text-dark">New ride request</p>
        <p className="text-xs font-semibold text-dark">{ride.vehicleType}</p>
      </div>
      <div className="p-4">
        <p className="text-sm font-semibold text-foreground">{ride.customer.name}</p>

        <div className="mt-3 space-y-2 text-sm">
          <p className="flex items-start gap-2">
            <Navigation size={14} className="mt-0.5 shrink-0 text-dark" />
            <span className="truncate text-foreground">{ride.pickupAddress}</span>
          </p>
          <p className="flex items-start gap-2">
            <MapPin size={14} className="mt-0.5 shrink-0 text-danger" />
            <span className="truncate text-foreground">{ride.destinationAddress}</span>
          </p>
        </div>

        <div className="mt-3 flex items-center justify-between rounded-xl bg-slate-50 dark:bg-white/5 px-3 py-2.5 text-sm">
          <span className="text-muted">{ride.distanceKm.toFixed(1)} km · {ride.estimatedDurationMinutes} min</span>
          {fare ? (
            <span className="font-bold text-dark">₹{fare.totalFare.toFixed(0)}</span>
          ) : (
            <Skeleton className="h-5 w-12" />
          )}
        </div>

        <div className="mt-4 flex gap-3">
          <Button variant="outline" className="flex-1" onClick={handleDecline} isLoading={busy === "decline"} disabled={!!busy}>
            <X size={16} /> Reject
          </Button>
          <Button className="flex-1" onClick={handleAccept} isLoading={busy === "accept"} disabled={!!busy}>
            <Check size={16} /> Accept
          </Button>
        </div>
      </div>
    </motion.div>
  );
}
