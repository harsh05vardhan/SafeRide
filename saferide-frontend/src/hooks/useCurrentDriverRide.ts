import { useQuery, useQueryClient } from "@tanstack/react-query";
import { driverService } from "@/services/driverService";
import { useDriverAssignmentChannel } from "./useRideChannel";
import { ACTIVE_DRIVER_RIDE_STATUSES } from "@/types/ride";

/**
 * There's no dedicated "my current assignment" endpoint - a driver's
 * current ride (whether it's a fresh proactive assignment awaiting
 * accept/decline, or one already accepted and in progress) is just
 * whichever of their own rides is in one of the busy statuses, found via
 * the same GET /api/drivers/rides history endpoint used everywhere else.
 * Both the dashboard and the requests page share this so "what is my
 * current ride" never has two different answers.
 *
 * <p>A driver has no ride id to subscribe to before they're assigned one -
 * unlike a customer tracking their own already-created ride - so this
 * listens on the private per-driver `/user/queue/assignments` channel
 * instead and simply refetches when it fires. The 15s poll stays on as a
 * fallback for whenever the socket is reconnecting; real assignments now
 * arrive within a second or two via the push, not the next poll tick.
 */
export function useCurrentDriverRide() {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ["driver-rides", "current"],
    queryFn: () => driverService.myRides(0, 5),
    refetchInterval: 15000,
  });

  useDriverAssignmentChannel(true, () => {
    void queryClient.invalidateQueries({ queryKey: ["driver-rides"] });
  });

  const currentRide = query.data?.content.find((ride) => ACTIVE_DRIVER_RIDE_STATUSES.includes(ride.status)) ?? null;

  return { ...query, currentRide };
}
