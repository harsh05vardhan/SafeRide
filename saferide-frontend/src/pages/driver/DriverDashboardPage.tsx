import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Star, ThumbsUp, XCircle, ShieldCheck, Car, ChevronRight, AlertTriangle } from "lucide-react";
import { driverService } from "@/services/driverService";
import { vehicleService } from "@/services/vehicleService";
import { rideService } from "@/services/rideService";
import { useDriverProfile } from "@/hooks/useDriverProfile";
import { useCurrentDriverRide } from "@/hooks/useCurrentDriverRide";
import { useToast } from "@/contexts/ToastContext";
import { Badge } from "@/components/common/Badge";
import { Skeleton } from "@/components/common/Skeleton";
import { StatCard } from "@/components/driver/StatCard";
import { IncomingRequestCard } from "@/components/driver/IncomingRequestCard";
import { extractErrorMessage } from "@/services/api";
import { cn } from "@/lib/utils";

export default function DriverDashboardPage() {
  const { data: profile, isPending: profilePending } = useDriverProfile();
  // Same staleness reasoning as useDriverProfile - a vehicle admins verify while this page is
  // already open needs to show up here without a manual reload.
  const { data: vehicles } = useQuery({ queryKey: ["vehicles"], queryFn: () => vehicleService.list(), refetchInterval: 15000 });
  const { currentRide } = useCurrentDriverRide();
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [isTogglingStatus, setIsTogglingStatus] = useState(false);

  const activeVehicle = vehicles?.find((v) => v.active) ?? null;
  const isVerified = profile?.verificationStatus === "VERIFIED";
  const canGoOnline = isVerified && activeVehicle?.verificationStatus === "VERIFIED";

  async function toggleOnline() {
    if (!profile) return;
    setIsTogglingStatus(true);
    try {
      const next = profile.onlineStatus === "ONLINE" ? "OFFLINE" : "ONLINE";
      await driverService.updateStatus({ onlineStatus: next });
      // Report a location the moment a driver goes online for the first time in a session, so
      // matching actually has something to score them against - see PUT /api/drivers/location.
      if (next === "ONLINE" && navigator.geolocation) {
        navigator.geolocation.getCurrentPosition((position) => {
          void driverService.updateLocation({
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
          });
        });
      }
      await queryClient.invalidateQueries({ queryKey: ["driver-profile"] });
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsTogglingStatus(false);
    }
  }

  async function acceptCurrentRide() {
    if (!currentRide) return;
    try {
      await rideService.accept(currentRide.id);
      showToast("Ride accepted", "success");
      navigate(`/driver/ride/${currentRide.id}`);
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    }
  }

  async function declineCurrentRide() {
    if (!currentRide) return;
    try {
      await rideService.decline(currentRide.id);
      showToast("Ride declined", "info");
      await queryClient.invalidateQueries({ queryKey: ["driver-rides"] });
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-5 px-4 py-6 sm:px-6">
      {/* Verification / vehicle setup banners */}
      {!profilePending && profile && !isVerified && (
        <div className="flex items-center gap-3 rounded-2xl border border-primary/30 bg-primary/10 p-4">
          <AlertTriangle size={20} className="shrink-0 text-amber-700" />
          <div>
            <p className="text-sm font-semibold text-dark">Your account is pending verification</p>
            <p className="text-xs text-muted">
              An admin needs to review your license before you can go online. Status: {profile.verificationStatus}.
            </p>
          </div>
        </div>
      )}
      {!profilePending && isVerified && !activeVehicle && (
        <Link
          to="/driver/profile"
          className="flex items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"
        >
          <div className="flex items-center gap-3">
            <Car size={20} className="text-dark" />
            <div>
              <p className="text-sm font-semibold text-dark">Add a vehicle to start driving</p>
              <p className="text-xs text-muted">You need a verified vehicle before you can go online.</p>
            </div>
          </div>
          <ChevronRight size={18} className="text-muted" />
        </Link>
      )}

      {/* Online toggle */}
      <div className="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div>
          <p className="text-lg font-bold text-dark">{profile?.onlineStatus === "ONLINE" ? "You're online" : "You're offline"}</p>
          <p className="text-sm text-muted">
            {profile?.onlineStatus === "ONLINE" ? "Looking for ride requests nearby" : "Go online to start receiving requests"}
          </p>
        </div>
        <button
          role="switch"
          aria-checked={profile?.onlineStatus === "ONLINE"}
          disabled={!canGoOnline || isTogglingStatus || profilePending}
          onClick={() => void toggleOnline()}
          className={cn(
            "relative h-8 w-14 shrink-0 rounded-full transition-colors disabled:opacity-40",
            profile?.onlineStatus === "ONLINE" ? "bg-success" : "bg-slate-300",
          )}
        >
          <span
            className={cn(
              "absolute top-1 h-6 w-6 rounded-full bg-white shadow transition-transform",
              profile?.onlineStatus === "ONLINE" ? "translate-x-7" : "translate-x-1",
            )}
          />
        </button>
      </div>

      {/* Incoming request / current ride */}
      {currentRide?.status === "DRIVER_ASSIGNED" && (
        <IncomingRequestCard ride={currentRide} onAccept={acceptCurrentRide} onDecline={declineCurrentRide} />
      )}
      {currentRide && currentRide.status !== "DRIVER_ASSIGNED" && (
        <Link
          to={`/driver/ride/${currentRide.id}`}
          className="flex items-center justify-between rounded-2xl border-2 border-dark bg-dark p-4 text-white shadow-md"
        >
          <div>
            <p className="text-sm font-bold">Ride in progress</p>
            <p className="text-xs text-slate-300">{currentRide.pickupAddress} → {currentRide.destinationAddress}</p>
          </div>
          <ChevronRight size={20} />
        </Link>
      )}

      {/* Stats */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {profilePending || !profile ? (
          [...Array(4)].map((_, i) => <Skeleton key={i} className="h-24 rounded-2xl" />)
        ) : (
          <>
            <StatCard icon={Star} label="Rating" value={profile.rating.toFixed(1)} />
            <StatCard icon={ThumbsUp} label="Acceptance" value={`${profile.acceptanceRate.toFixed(0)}%`} />
            <StatCard
              icon={XCircle}
              label="Cancellation"
              value={`${profile.cancellationRate.toFixed(0)}%`}
              tone={profile.cancellationRate > 20 ? "warning" : "neutral"}
            />
            <StatCard icon={ShieldCheck} label="Reliability" value={profile.reliabilityScore.toFixed(0)} />
          </>
        )}
      </div>

      <div className="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        <span className="text-sm text-muted">Verification status</span>
        <Badge variant={isVerified ? "success" : "primary"}>{profile?.verificationStatus ?? "—"}</Badge>
      </div>

      <div className="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        <span className="text-sm text-muted">Active vehicle</span>
        {activeVehicle ? (
          <span className="text-sm font-semibold text-foreground">
            {activeVehicle.vehicleModel} · {activeVehicle.vehicleNumber}
          </span>
        ) : (
          <span className="text-sm text-muted">None</span>
        )}
      </div>
    </div>
  );
}
