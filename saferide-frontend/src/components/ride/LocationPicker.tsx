import { useRef, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { MapPin, Navigation, ChevronDown } from "lucide-react";
import { SUGGESTED_LOCATIONS } from "@/lib/suggestedLocations";
import type { LocationPoint } from "@/contexts/BookingContext";
import { cn } from "@/lib/utils";

interface LocationPickerProps {
  label: string;
  placeholder: string;
  value: LocationPoint | null;
  onChange: (point: LocationPoint) => void;
  accent?: "pickup" | "destination";
}

/**
 * Stands in for a real address-autocomplete field: SafeRide has no
 * geocoding provider wired up yet (MapService is backend-only, and only
 * does distance/duration - see the README's Map section), so location
 * selection is a curated list of known-coordinate landmarks. Dropping a
 * pin directly on the map (see HomePage) is the other way to set a point.
 */
export function LocationPicker({ label, placeholder, value, onChange, accent = "pickup" }: LocationPickerProps) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  return (
    <div className="relative" ref={containerRef}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center gap-3 rounded-xl border border-slate-200 dark:border-card-border bg-white dark:bg-card px-3.5 py-3 text-left transition-colors hover:border-slate-300 dark:border-slate-600"
      >
        <span
          className={cn(
            "flex h-8 w-8 shrink-0 items-center justify-center rounded-lg",
            accent === "pickup" ? "bg-dark/10 text-dark" : "bg-danger/10 text-danger",
          )}
        >
          {accent === "pickup" ? <Navigation size={15} /> : <MapPin size={15} />}
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-xs font-medium text-muted">{label}</span>
          <span className={cn("block truncate text-sm font-semibold", value ? "text-foreground" : "text-muted")}>
            {value ? value.label : placeholder}
          </span>
        </span>
        <ChevronDown size={16} className={cn("shrink-0 text-muted transition-transform", open && "rotate-180")} />
      </button>

      <AnimatePresence>
        {open && (
          <>
            <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
            <motion.div
              initial={{ opacity: 0, y: -6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -6 }}
              transition={{ duration: 0.15 }}
              className="absolute left-0 right-0 top-full z-20 mt-2 max-h-72 overflow-auto rounded-xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-1.5 shadow-lg"
            >
              <p className="px-2.5 pb-1 pt-1.5 text-xs font-semibold uppercase tracking-wide text-muted">
                Suggested locations
              </p>
              {SUGGESTED_LOCATIONS.map((location) => (
                <button
                  key={location.label}
                  type="button"
                  onClick={() => {
                    onChange(location);
                    setOpen(false);
                  }}
                  className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2.5 text-left text-sm hover:bg-slate-50 dark:bg-white/5"
                >
                  <MapPin size={15} className="shrink-0 text-muted" />
                  <span>
                    <span className="block font-medium text-foreground">{location.label}</span>
                    <span className="block text-xs text-muted">{location.address}</span>
                  </span>
                </button>
              ))}
              <p className="px-2.5 pb-1.5 pt-2 text-xs text-muted">Or tap a point directly on the map.</p>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
}
