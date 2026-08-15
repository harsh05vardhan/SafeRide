import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { ArrowLeftRight, Search } from "lucide-react";
import { LocationPicker } from "@/components/ride/LocationPicker";
import { MapView } from "@/components/map/MapView";
import { PickupMarker } from "@/components/map/PickupMarker";
import { DestinationMarker } from "@/components/map/DestinationMarker";
import { RouteLine } from "@/components/map/RouteLine";
import { Button } from "@/components/common/Button";
import { useBooking, type LocationPoint } from "@/contexts/BookingContext";
import { useToast } from "@/contexts/ToastContext";
import { rideService } from "@/services/rideService";
import { extractErrorMessage } from "@/services/api";
import { MAP_DEFAULT_CENTER } from "@/lib/suggestedLocations";

export default function HomePage() {
  const { pickup, destination, setPickup, setDestination, setEstimate } = useBooking();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [isEstimating, setIsEstimating] = useState(false);
  const [pinTarget, setPinTarget] = useState<"pickup" | "destination" | null>(null);

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

  return (
    <div className="mx-auto grid max-w-7xl grid-cols-1 gap-6 p-4 sm:p-6 lg:grid-cols-[420px_1fr] lg:items-start">
      {/* Booking card */}
      <motion.div
        initial={{ opacity: 0, x: -16 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ duration: 0.35 }}
        className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6"
      >
        <h1 className="text-2xl font-extrabold text-dark">Where are you going?</h1>
        <p className="mt-1 text-sm text-muted">Pick your locations to see ride options and fares.</p>

        <div className="relative mt-5 space-y-2">
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
      <div className="hidden h-[calc(100vh-7rem)] overflow-hidden rounded-2xl border border-slate-200 shadow-sm lg:block">
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
  );
}
