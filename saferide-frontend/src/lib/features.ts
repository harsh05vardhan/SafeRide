import { Lock, Users, Zap, LifeBuoy, type LucideIcon } from "lucide-react";

export interface SafeRideFeature {
  icon: LucideIcon;
  title: string;
  description: string;
}

/** The four things SafeRide actually does differently - shared by LoginPage and HomePage so the pitch stays identical everywhere it's made, rather than each page inventing its own claims. */
export const SAFERIDE_FEATURES: SafeRideFeature[] = [
  { icon: Lock, title: "Fare Lock Guarantee", description: "No extra charges. Ever." },
  { icon: Users, title: "Trusted Drivers", description: "Verified and highly rated." },
  { icon: Zap, title: "Instant Matching", description: "Smart algorithm for faster rides." },
  { icon: LifeBuoy, title: "Safety First", description: "SOS and trip sharing." },
];
