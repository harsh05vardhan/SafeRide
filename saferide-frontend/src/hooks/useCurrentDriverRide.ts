import { useQuery } from "@tanstack/react-query";
import { driverService } from "@/services/driverService";
import { ACTIVE_DRIVER_RIDE_STATUSES } from "@/types/ride";

/**
 * There's no dedicated "my current assignment" endpoint - a driver's
 * current ride (whether it's a fresh proactive assignment awaiting
 * accept/decline, or one already accepted and in progress) is just
 * whichever of their own rides is in one of the busy statuses, found via
 * the same GET /api/drivers/rides history endpoint used everywhere else.
 * Both the dashboard and the requests page share this so "what is my
 * current ride" never has two different answers.
 */
export function useCurrentDriverRide() {
  const query = useQuery({
    queryKey: ["driver-rides", "current"],
    queryFn: () => driverService.myRides(0, 5),
    refetchInterval: 5000,
  });

  const currentRide = query.data?.content.find((ride) => ACTIVE_DRIVER_RIDE_STATUSES.includes(ride.status)) ?? null;

  return { ...query, currentRide };
}
