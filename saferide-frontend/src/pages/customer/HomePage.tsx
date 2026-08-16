import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { ArrowLeftRight, Car, ChevronRight, MapPin, Search } from "lucide-react";
import { LocationPicker } from "@/components/ride/LocationPicker";
import { MapView } from "@/components/map/MapView";
import { PickupMarker } from "@/components/map/PickupMarker";
import { DestinationMarker } from "@/components/map/DestinationMarker";
import { RouteLine } from "@/components/map/RouteLine";
import { Button } from "@/components/common/Button";
import { Badge } from "@/components/common/Badge";
import { Skeleton } from "@/components/common/Skeleton";
import { useAuth } from "@/contexts/AuthContext";
import { useBooking, type LocationPoint } from "@/contexts/BookingContext";
import { useToast } from "@/contexts/ToastContext";
import { rideService } from "@/services/rideService";
import { extractErrorMessage } from "@/services/api";
import { SUGGESTED_LOCATIONS, MAP_DEFAULT_CENTER } from "@/lib/suggestedLocations";
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

export default function HomePage() {
  const { user } = useAuth();
  const { pickup, destination, setPickup, setDestination, setEstimate } = useBooking();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [isEstimating, setIsEstimating] = useState(false);
  const [pinTarget, setPinTarget] = useState<"pickup" | "destination" | null>(null);

  const { data: recentRides, isPending: recentRidesPending } = useQuery({
    queryKey: ["my-rides", "recent"],
    queryFn: () => rideService.myRides(0, 3),
  });

  const center: [number, number] = pickup
    ? [pickup.latitude, pickup.longitude]
    : destination
      ? [destination.latitude, destination.longitude]
      : MAP_DEFAULT_CENTER;

  function handleMapClick(latitude: number, longitude: number) {
    if (!pinTarget) return;
    const point: LocationPoint = { label: "Dropped pin", address: "Custom map location", latitude, longitude };
    if (pinTarget === "pickup") setPickup(point);
    else setDestination(point);
    setPinTarget(null);
  }

  function swap() {
    const p = pickup;
    setPickup(destination);
    setDestination(p);
  }

  /** Tapping a suggestion fills whichever of pickup/destination is still empty - pickup first, matching how someone naturally plans a trip (where am I, then where am I going). */
  function pickSuggestion(point: LocationPoint) {
    if (!pickup) setPickup(point);
    else setDestination(point);
  }

  async function findRide() {
    if (!pickup || !destination) return;
    setIsEstimating(true);
    try {
      const estimate = await rideService.estimate({
        pickupLatitude: pickup.latitude,
        pickupLongitude: pickup.longitude,
        destinationLatitude: destination.latitude,
        destinationLongitude: destination.longitude,
      });
      setEstimate(estimate.distanceKm, estimate.estimatedDurationMinutes);
      navigate("/ride-options");
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsEstimating(false);
    }
  }

  const firstName = user?.name?.split(" ")[0] ?? "there";

  return (
    <div className="mx-auto max-w-7xl space-y-6 p-4 sm:p-6">
      <div>
        <p className="text-sm text-muted">Hello, {firstName} 👋</p>
        <h1 className="text-2xl font-extrabold text-dark sm:text-3xl">Where are you going?</h1>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[420px_1fr] lg:items-start">
        {/* Booking card */}
        <motion.div
          initial={{ opacity: 0, x: -16 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.35 }}
          className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6"
        >
          <div className="relative space-y-2">
            <LocationPicker label="Pickup" placeholder="Choose pickup location" value={pickup} onChange={setPickup} accent="pickup" />
            <div className="flex justify-center">
              <button
                onClick={swap}
                type="button"
                aria-label="Swap pickup and destination"
                className="flex h-8 w-8 items-center justify-center rounded-full border border-slate-200 bg-white text-muted shadow-sm hover:text-dark"
              >
                <ArrowLeftRight size={14} className="rotate-90" />
              </button>
            </div>
            <LocationPicker
              label="Destination"
              placeholder="Choose destination"
              value={destination}
              onChange={setDestination}
              accent="destination"
            />
          </div>

          <button
            type="button"
            onClick={() => setPinTarget(pinTarget ? null : "pickup")}
            className="mt-3 text-xs font-medium text-dark underline-offset-2 hover:underline"
          >
            {pinTarget ? `Tap the map to set your ${pinTarget}…` : "Or drop a pin on the map instead"}
          </button>

          {/* Suggested places - one tap fills whichever field is still empty, no need to open the picker dropdown first. */}
          <div className="mt-5">
            <p className="text-xs font-semibold uppercase tracking-wide text-muted">Suggested places</p>
            <div className="mt-2 space-y-1.5">
              {SUGGESTED_LOCATIONS.slice(0, 3).map((location) => (
                <button
                  key={location.label}
                  type="button"
                  onClick={() => pickSuggestion(location)}
                  className="flex w-full items-center gap-2.5 rounded-xl px-2.5 py-2 text-left transition-colors hover:bg-slate-50"
                >
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-muted">
                    <MapPin size={14} />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium text-foreground">{location.label}</span>
                    <span className="block truncate text-xs text-muted">{location.address}</span>
                  </span>
                </button>
              ))}
            </div>
          </div>

          <Button className="mt-5 w-full" size="lg" disabled={!pickup || !destination} isLoading={isEstimating} onClick={findRide}>
            <Search size={18} />
            Find Ride
          </Button>

          <div className="mt-6 border-t border-slate-100 pt-4 lg:hidden">
            <div className="h-56 overflow-hidden rounded-xl">
              <MapView center={center} onMapClick={pinTarget ? handleMapClick : undefined}>
                {pickup && <PickupMarker latitude={pickup.latitude} longitude={pickup.longitude} address={pickup.address} />}
                {destination && (
                  <DestinationMarker latitude={destination.latitude} longitude={destination.longitude} address={destination.address} />
                )}
                {pickup && destination && (
                  <RouteLine from={[pickup.latitude, pickup.longitude]} to={[destination.latitude, destination.longitude]} />
                )}
              </MapView>
            </div>
          </div>
        </motion.div>

        {/* Map (desktop) */}
        <div className="hidden h-[calc(100vh-13rem)] overflow-hidden rounded-2xl border border-slate-200 shadow-sm lg:block">
          <MapView center={center} onMapClick={pinTarget ? handleMapClick : undefined}>
            {pickup && <PickupMarker latitude={pickup.latitude} longitude={pickup.longitude} address={pickup.address} />}
            {destination && (
              <DestinationMarker latitude={destination.latitude} longitude={destination.longitude} address={destination.address} />
            )}
            {pickup && destination && (
              <RouteLine from={[pickup.latitude, pickup.longitude]} to={[destination.latitude, destination.longitude]} />
            )}
          </MapView>
        </div>
      </div>

      {/* Recent rides */}
      <div>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-dark">Recent rides</h2>
          <Link to="/rides" className="flex items-center gap-1 text-xs font-semibold text-dark hover:underline">
            View all <ChevronRight size={14} />
          </Link>
        </div>

        {recentRidesPending ? (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            {[...Array(3)].map((_, i) => (
              <Skeleton key={i} className="h-20 rounded-2xl" />
            ))}
          </div>
        ) : !recentRides || recentRides.content.length === 0 ? (
          <div className="flex items-center gap-3 rounded-2xl border border-dashed border-slate-300 bg-slate-50/60 px-5 py-6 text-center">
            <p className="mx-auto text-sm text-muted">No rides yet — book your first one above.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            {recentRides.content.map((ride) => (
              <Link
                key={ride.id}
                to={`/ride/${ride.id}`}
                className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white p-3.5 shadow-sm transition-shadow hover:shadow-md"
              >
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-dark">
                  <Car size={17} />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold text-foreground">{ride.pickupAddress}</p>
                  <p className="truncate text-xs text-muted">→ {ride.destinationAddress}</p>
                </div>
                <Badge variant={STATUS_VARIANT[ride.status]} className="shrink-0">
                  {STATUS_LABEL[ride.status]}
                </Badge>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
