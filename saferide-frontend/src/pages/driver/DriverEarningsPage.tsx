import { useMemo } from "react";
import { useQuery, useQueries } from "@tanstack/react-query";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Wallet, Route, CheckCircle2, TrendingUp } from "lucide-react";
import { driverService } from "@/services/driverService";
import { fareService } from "@/services/fareService";
import { Skeleton } from "@/components/common/Skeleton";
import { EmptyState, ErrorState } from "@/components/common/StateViews";
import { StatCard } from "@/components/driver/StatCard";
import type { Ride } from "@/types/ride";

const COMPLETED_STATUSES: Ride["status"][] = ["RIDE_COMPLETED", "PAYMENT_PENDING", "PAYMENT_COMPLETED", "RATED"];

/** Earnings are derived from each completed ride's locked fare - never recalculated client-side, matching the Fare Lock guarantee shown to customers. */
export default function DriverEarningsPage() {
  const {
    data: rides,
    isPending,
    isError,
    refetch,
  } = useQuery({
    queryKey: ["driver-rides", "history"],
    queryFn: () => driverService.myRides(0, 100),
  });

  const completedRides = useMemo(() => rides?.content.filter((r) => COMPLETED_STATUSES.includes(r.status)) ?? [], [rides]);

  const fareQueries = useQueries({
    queries: completedRides.map((ride) => ({
      queryKey: ["ride-fare", ride.id],
      queryFn: () => fareService.getRideFare(ride.id),
    })),
  });

  const fareLoading = fareQueries.some((q) => q.isPending);

  const rows = useMemo(
    () =>
      completedRides
        .map((ride, i) => ({ ride, fare: fareQueries[i]?.data }))
        .filter((r): r is { ride: Ride; fare: NonNullable<(typeof fareQueries)[number]["data"]> } => !!r.fare)
        .sort((a, b) => new Date(b.ride.completedAt ?? b.ride.createdAt).getTime() - new Date(a.ride.completedAt ?? a.ride.createdAt).getTime()),
    [completedRides, fareQueries],
  );

  const totalEarnings = rows.reduce((sum, r) => sum + r.fare.totalFare, 0);
  const totalTrips = rows.length;
  const avgFare = totalTrips > 0 ? totalEarnings / totalTrips : 0;
  const totalDistance = rows.reduce((sum, r) => sum + r.ride.distanceKm, 0);

  const chartData = useMemo(() => {
    const byDay = new Map<string, number>();
    for (const { ride, fare } of rows) {
      const day = new Date(ride.completedAt ?? ride.createdAt).toLocaleDateString(undefined, { month: "short", day: "numeric" });
      byDay.set(day, (byDay.get(day) ?? 0) + fare.totalFare);
    }
    return [...byDay.entries()].map(([day, total]) => ({ day, total })).slice(-14);
  }, [rows]);

  if (isPending) {
    return (
      <div className="mx-auto max-w-3xl space-y-4 px-4 py-6 sm:px-6">
        <Skeleton className="h-8 w-40" />
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {[...Array(4)].map((_, i) => (
            <Skeleton key={i} className="h-24 rounded-2xl" />
          ))}
        </div>
        <Skeleton className="h-64 w-full rounded-2xl" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-6 sm:px-6">
        <ErrorState description="Couldn't load your earnings." onAction={() => refetch()} />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-5 px-4 py-6 sm:px-6">
      <h1 className="text-2xl font-extrabold text-dark">Earnings</h1>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <StatCard icon={Wallet} label="Total earnings" value={`₹${totalEarnings.toFixed(0)}`} />
        <StatCard icon={CheckCircle2} label="Completed trips" value={String(totalTrips)} />
        <StatCard icon={TrendingUp} label="Avg. per trip" value={`₹${avgFare.toFixed(0)}`} />
        <StatCard icon={Route} label="Distance driven" value={`${totalDistance.toFixed(0)} km`} />
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <p className="mb-4 text-sm font-semibold text-dark">Earnings by day</p>
        {fareLoading ? (
          <Skeleton className="h-56 w-full" />
        ) : chartData.length === 0 ? (
          <p className="py-10 text-center text-sm text-muted">No completed trips yet.</p>
        ) : (
          <div className="h-56 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" vertical={false} />
                <XAxis dataKey="day" tick={{ fontSize: 11, fill: "#64748B" }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 11, fill: "#64748B" }} axisLine={false} tickLine={false} width={36} />
                <Tooltip
                  cursor={{ fill: "#F8FAFC" }}
                  formatter={(value) => [`₹${Number(value).toFixed(0)}`, "Earnings"]}
                  contentStyle={{ borderRadius: 12, border: "1px solid #E2E8F0", fontSize: 12 }}
                />
                <Bar dataKey="total" fill="#F5B800" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>

      <div>
        <p className="mb-3 text-sm font-semibold text-dark">Recent trips</p>
        {rows.length === 0 ? (
          <EmptyState icon={Wallet} title="No trips completed yet" description="Your completed rides will show up here with what you earned." />
        ) : (
          <div className="space-y-2">
            {rows.slice(0, 20).map(({ ride, fare }) => (
              <div key={ride.id} className="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-foreground">{ride.pickupAddress}</p>
                  <p className="truncate text-xs text-muted">→ {ride.destinationAddress}</p>
                  <p className="mt-1 text-xs text-muted">
                    {new Date(ride.completedAt ?? ride.createdAt).toLocaleDateString(undefined, { month: "short", day: "numeric" })} ·{" "}
                    {ride.distanceKm.toFixed(1)} km
                  </p>
                </div>
                <span className="shrink-0 pl-3 text-base font-bold text-dark">₹{fare.totalFare.toFixed(0)}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
