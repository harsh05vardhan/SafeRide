import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { ArrowLeftRight, Car, ChevronRight, LocateFixed, MapPin, Plane, Search, ShoppingBag, Store, TrainFront } from "lucide-react";
import { LocationPicker } from "@/components/ride/LocationPicker";
import { MapView } from "@/components/map/MapView";
import { PickupMarker } from "@/components/map/PickupMarker";
import { DestinationMarker } from "@/components/map/DestinationMarker";
import { RouteLine } from "@/components/map/RouteLine";
import { MyLocationMarker } from "@/components/map/MyLocationMarker";
import { Button } from "@/components/common/Button";
import { Badge } from "@/components/common/Badge";
import { Skeleton } from "@/components/common/Skeleton";
import { useAuth } from "@/contexts/AuthContext";
import { useBooking, type LocationPoint } from "@/contexts/BookingContext";
import { useToast } from "@/contexts/ToastContext";
import { useLiveLocation } from "@/hooks/useLiveLocation";
import { rideService } from "@/services/rideService";
import { extractErrorMessage } from "@/services/api";
import { SUGGESTED_LOCATIONS, MAP_DEFAULT_CENTER } from "@/lib/suggestedLocations";
import { SAFERIDE_FEATURES } from "@/lib/features";
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

/** One icon per curated landmark - purely cosmetic, keyed by label since SUGGESTED_LOCATIONS has no category field of its own. */
const PLACE_ICON: Record<string, typeof MapPin> = {
  "Guwahati Railway Station": TrainFront,
  "Fancy Bazar": ShoppingBag,
  Dispur: Store,
  Panbazar: ShoppingBag,
  "Guwahati Airport": Plane,
};

export default function HomePage() {
  const { user } = useAuth();
  const { pickup, destination, setPickup, setDestination, setEstimate } = useBooking();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [isEstimating, setIsEstimating] = useState(false);
  const [pinTarget, setPinTarget] = useState<"pickup" | "destination" | null>(null);
  // Requesting this is what triggers the browser's own native location-permission prompt the
  // moment the customer opens the app - there's no separate custom dialog to build here, that
  // prompt genuinely comes from the browser itself.
  const { position: myLocation } = useLiveLocation(true);

  const { data: recentRides, isPending: recentRidesPending } = useQuery({
    queryKey: ["my-rides", "recent"],
    queryFn: () => rideService.myRides(0, 3),
  });

  const center: [number, number] = pickup
    ? [pickup.latitude, pickup.longitude]
    : destination
      ? [destination.latitude, destination.longitude]
      : myLocation
        ? [myLocation.latitude, myLocation.longitude]
        : MAP_DEFAULT_CENTER;

  function useMyLocation() {
    if (!myLocation) return;
    setPickup({ label: "Current location", address: "Your current location", latitude: myLocation.latitude, longitude: myLocation.longitude });
  }

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
      {/* Header band */}
      <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-primary/20 via-primary/5 to-white p-6 sm:p-8">
        <div className="pointer-events-none absolute -right-6 -top-6 flex h-32 w-32 items-center justify-center rounded-full bg-primary/25 sm:right-4 sm:top-4 sm:h-40 sm:w-40">
          <Car size={64} className="text-dark/60" />
        </div>
        <div className="relative max-w-xs">
          <p className="text-sm text-muted">Hello, {firstName} 👋</p>
          <h1 className="mt-1 text-2xl font-extrabold leading-tight text-dark sm:text-3xl">Where are you going?</h1>
          <p className="mt-2 text-sm text-muted">Book a safe, reliable and affordable ride in seconds.</p>
          <p className="mt-3 font-serif text-base italic text-dark/70">Rides for a better Guwahati</p>
        </div>
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
            {myLocation && (
              <button
                type="button"
                onClick={useMyLocation}
                className="flex items-center gap-1.5 text-xs font-medium text-dark underline-offset-2 hover:underline"
              >
                <LocateFixed size={12} /> Use my current location
              </button>
            )}
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

          {/* Suggested places - horizontal, tap to fill whichever field is still empty. */}
          <div className="mt-5">
            <p className="text-xs font-semibold uppercase tracking-wide text-muted">Suggested places</p>
            <div className="mt-2 flex gap-2.5 overflow-x-auto pb-1">
              {SUGGESTED_LOCATIONS.map((location) => {
                const Icon = PLACE_ICON[location.label] ?? MapPin;
                return (
                  <button
                    key={location.label}
                    type="button"
                    onClick={() => pickSuggestion(location)}
                    className="flex w-24 shrink-0 flex-col items-center gap-2 rounded-2xl border border-slate-200 bg-white p-3 text-center transition-colors hover:border-slate-300 hover:bg-slate-50"
                  >
                    <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary/15 text-dark">
                      <Icon size={16} />
                    </span>
                    <span className="line-clamp-2 text-[11px] font-medium leading-tight text-foreground">{location.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <Button className="mt-5 w-full" size="lg" disabled={!pickup || !destination} isLoading={isEstimating} onClick={findRide}>
            <Search size={18} />
            Find Ride
          </Button>

          <div className="mt-6 border-t border-slate-100 pt-4 lg:hidden">
            <div className="h-56 overflow-hidden rounded-xl">
              <MapView center={center} onMapClick={pinTarget ? handleMapClick : undefined}>
                {myLocation && <MyLocationMarker latitude={myLocation.latitude} longitude={myLocation.longitude} />}
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
        <div className="hidden h-[calc(100vh-16rem)] overflow-hidden rounded-2xl border border-slate-200 shadow-sm lg:block">
          <MapView center={center} onMapClick={pinTarget ? handleMapClick : undefined}>
            {myLocation && <MyLocationMarker latitude={myLocation.latitude} longitude={myLocation.longitude} />}
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

      {/* Same pitch as the login page, reused rather than reinvented - see lib/features.ts. */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {SAFERIDE_FEATURES.map(({ icon: Icon, title, description }) => (
          <div key={title} className="flex items-center gap-2.5 rounded-2xl border border-slate-200 bg-white p-3.5 shadow-sm">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary/15 text-dark">
              <Icon size={16} />
            </span>
            <div className="min-w-0">
              <p className="truncate text-xs font-semibold text-foreground">{title}</p>
              <p className="truncate text-[11px] text-muted">{description}</p>
            </div>
          </div>
        ))}
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
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-slate-100 text-sm font-bold text-dark">
                  {ride.driver ? ride.driver.name.charAt(0) : <Car size={17} />}
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
