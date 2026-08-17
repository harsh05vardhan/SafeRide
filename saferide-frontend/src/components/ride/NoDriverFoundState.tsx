import { useNavigate } from "react-router-dom";
import { SearchX } from "lucide-react";
import { Button } from "@/components/common/Button";

/**
 * Shown when a ride auto-cancels because nobody was available in time
 * (backend: RideService#autoExpireStaleSearches, RideCancellationReason.NO_DRIVER_FOUND) -
 * used from both FindingDriverPage (the very first search) and
 * RideTrackingPage (a re-search after a driver declined can time out while
 * the customer is already on that page, without ever bouncing back here).
 * Deliberately its own distinct message rather than the generic "ride
 * cancelled" screen, since a customer that did nothing wrong needs to know
 * the fix is just to try again, not that they cancelled something.
 */
export function NoDriverFoundState() {
  const navigate = useNavigate();

  return (
    <div className="mx-auto flex max-w-md flex-col items-center px-4 py-16 text-center">
      <SearchX size={48} className="mb-3 text-muted" />
      <h1 className="text-xl font-bold text-dark">Oops! No one could take your ride</h1>
      <p className="mt-1 text-sm text-muted">
        We looked for a driver nearby but couldn't find one in time. Please try again in a moment.
      </p>
      <Button className="mt-6" onClick={() => navigate("/home")}>
        Book another ride
      </Button>
    </div>
  );
}
