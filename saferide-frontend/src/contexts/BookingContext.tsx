import { createContext, useContext, useMemo, useState, type ReactNode } from "react";
import type { VehicleType } from "@/types/ride";

export interface LocationPoint {
  label: string;
  address: string;
  latitude: number;
  longitude: number;
}

interface BookingDraft {
  pickup: LocationPoint | null;
  destination: LocationPoint | null;
  distanceKm: number | null;
  estimatedDurationMinutes: number | null;
  vehicleType: VehicleType | null;
}

interface BookingContextValue extends BookingDraft {
  setPickup: (point: LocationPoint | null) => void;
  setDestination: (point: LocationPoint | null) => void;
  setEstimate: (distanceKm: number, estimatedDurationMinutes: number) => void;
  setVehicleType: (vehicleType: VehicleType) => void;
  reset: () => void;
  isReadyForOptions: boolean;
  isReadyForFareBreakdown: boolean;
}

const emptyDraft: BookingDraft = {
  pickup: null,
  destination: null,
  distanceKm: null,
  estimatedDurationMinutes: null,
  vehicleType: null,
};

const BookingContext = createContext<BookingContextValue | undefined>(undefined);

/**
 * Holds the in-progress booking (pickup/destination/estimate/selected
 * vehicle) before a {@code Ride} exists server-side. Nothing here is
 * persisted - if the user refreshes mid-flow they start over, which is an
 * acceptable simplification for a draft that only lives across three
 * pages of one continuous booking action.
 */
export function BookingProvider({ children }: { children: ReactNode }) {
  const [draft, setDraft] = useState<BookingDraft>(emptyDraft);

  const value = useMemo<BookingContextValue>(
    () => ({
      ...draft,
      setPickup: (point) => setDraft((d) => ({ ...d, pickup: point })),
      setDestination: (point) => setDraft((d) => ({ ...d, destination: point })),
      setEstimate: (distanceKm, estimatedDurationMinutes) =>
        setDraft((d) => ({ ...d, distanceKm, estimatedDurationMinutes })),
      setVehicleType: (vehicleType) => setDraft((d) => ({ ...d, vehicleType })),
      reset: () => setDraft(emptyDraft),
      isReadyForOptions: draft.pickup !== null && draft.destination !== null && draft.distanceKm !== null,
      isReadyForFareBreakdown:
        draft.pickup !== null && draft.destination !== null && draft.distanceKm !== null && draft.vehicleType !== null,
    }),
    [draft],
  );

  return <BookingContext.Provider value={value}>{children}</BookingContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components -- the useX hook belongs next to its Provider, not split into a second file
export function useBooking(): BookingContextValue {
  const context = useContext(BookingContext);
  if (!context) {
    throw new Error("useBooking must be used within a BookingProvider");
  }
  return context;
}
