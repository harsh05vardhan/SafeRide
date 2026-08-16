import { useQuery } from "@tanstack/react-query";
import { driverService } from "@/services/driverService";

export function useDriverProfile() {
  return useQuery({
    queryKey: ["driver-profile"],
    queryFn: () => driverService.getProfile(),
    // refetchOnWindowFocus is off app-wide (App.tsx), so without this a driver's already-open
    // dashboard would never notice an admin approving them (or any other profile change) in a
    // separate session - verificationStatus/reliability numbers would look frozen until a full
    // page reload. Same polling cadence useCurrentDriverRide already uses for the same reason.
    refetchInterval: 15000,
  });
}
