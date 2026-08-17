import { Link } from "react-router-dom";
import { AlertOctagon, Car, KeyRound, ShieldAlert, ShieldCheck, UserCheck } from "lucide-react";
import { Skeleton } from "@/components/common/Skeleton";
import { useCurrentCustomerRide } from "@/hooks/useCurrentCustomerRide";

const SAFETY_CARDS = [
  {
    icon: UserCheck,
    title: "Verified drivers",
    description: "Every driver is document-checked and admin-approved before they can accept rides.",
  },
  {
    icon: KeyRound,
    title: "OTP-protected pickup",
    description: "Your ride only starts once you share the one-time code with your driver, so no one else can take your trip.",
  },
  {
    icon: ShieldAlert,
    title: "Live trip sharing",
    description: "Share a live tracking link with anyone you trust from the ride screen - no account needed to view it.",
  },
  {
    icon: AlertOctagon,
    title: "SOS alerts",
    description: "Send an SOS from an active ride and our safety team gets your live location immediately.",
  },
];

/**
 * A real customer-facing home for the safety features that already exist
 * (SOS, Share Ride) but only live on the in-ride tracking screen. Rather
 * than duplicate live controls with no ride to attach them to, this
 * explains the features and deep-links into the current ride when there is
 * one - honest about what's actually actionable right now.
 */
export default function SafetyCenterPage() {
  const { currentRide, isPending } = useCurrentCustomerRide();

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-4 sm:p-6">
      <div>
        <h1 className="text-xl font-extrabold text-dark">Safety Center</h1>
        <p className="mt-1 text-sm text-muted">How SafeRide keeps every trip safe, start to finish.</p>
      </div>

      {isPending ? (
        <Skeleton className="h-24 rounded-2xl" />
      ) : currentRide ? (
        <div className="flex flex-col items-start gap-3 rounded-2xl border border-primary/30 bg-primary/10 p-5 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground">
              <Car size={20} />
            </span>
            <div>
              <p className="text-sm font-semibold text-dark">You have an active ride</p>
              <p className="text-xs text-muted">Open live tracking for SOS and Share Ride controls.</p>
            </div>
          </div>
          <Link
            to={`/ride/${currentRide.id}`}
            className="inline-flex h-9 w-full items-center justify-center gap-2 whitespace-nowrap rounded-xl bg-primary px-3.5 text-sm font-semibold text-primary-foreground shadow-sm transition-colors hover:brightness-95 sm:w-auto"
          >
            Open live tracking
          </Link>
        </div>
      ) : (
        <div className="flex flex-col items-start gap-3 rounded-2xl border border-dashed border-slate-300 bg-slate-50/60 p-5 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-slate-200 text-dark">
              <ShieldCheck size={20} />
            </span>
            <p className="text-sm text-muted">Book a ride to access live safety tools like SOS alerts and trip sharing.</p>
          </div>
          <Link
            to="/home"
            className="inline-flex h-9 w-full items-center justify-center gap-2 whitespace-nowrap rounded-xl border border-slate-300 bg-white px-3.5 text-sm font-semibold text-foreground shadow-sm transition-colors hover:bg-slate-50 sm:w-auto"
          >
            Book a ride
          </Link>
        </div>
      )}

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {SAFETY_CARDS.map(({ icon: Icon, title, description }) => (
          <div key={title} className="flex gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary/15 text-dark">
              <Icon size={17} />
            </span>
            <div>
              <p className="text-sm font-semibold text-foreground">{title}</p>
              <p className="mt-0.5 text-xs text-muted">{description}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
