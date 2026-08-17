import { useQuery } from "@tanstack/react-query";
import { rideService } from "@/services/rideService";
import { ACTIVE_TRACKING_STATUSES } from "@/types/ride";

/**
 * A customer's current in-progress ride, if any - same "just scan recent
 * history for a non-terminal status" approach as useCurrentDriverRide, since
 * there's likewise no dedicated "my active ride" endpoint. Used by the
 * Safety Center page to link straight into live tracking (where SOS and
 * Share Ride actually live) instead of duplicating those controls.
 */
export function useCurrentCustomerRide() {
  const query = useQuery({
    queryKey: ["my-rides", "current"],
    queryFn: () => rideService.myRides(0, 5),
    refetchInterval: 15000,
  });

  const currentRide = query.data?.content.find((ride) => ACTIVE_TRACKING_STATUSES.includes(ride.status)) ?? null;

  return { ...query, currentRide };
}
