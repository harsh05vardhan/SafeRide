import { useQuery } from "@tanstack/react-query";
import { driverService } from "@/services/driverService";

export function useDriverProfile() {
  return useQuery({
    queryKey: ["driver-profile"],
    queryFn: () => driverService.getProfile(),
  });
}
