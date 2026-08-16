import { useQuery } from "@tanstack/react-query";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Car, CheckCircle2, XCircle, Wallet, Star, Users, ShieldCheck, Radio, MessageSquareWarning, Siren } from "lucide-react";
import { adminAnalyticsService } from "@/services/adminAnalyticsService";
import { Skeleton } from "@/components/common/Skeleton";
import { ErrorState } from "@/components/common/StateViews";
import { StatCard } from "@/components/driver/StatCard";
import { Card, CardBody, CardHeader } from "@/components/common/Card";

/**
 * Read-only snapshot of {@code GET /api/admin/analytics/summary} - every
 * number here is a real aggregate query on the backend (COUNT/SUM/AVG),
 * not something computed client-side from a list of rows. No date range
 * or filters - it's a single point-in-time summary, matching the scope of
 * what the backend actually returns.
 */
export default function AdminAnalyticsPage() {
  const { data, isPending, isError, refetch } = useQuery({
    queryKey: ["admin-analytics-summary"],
    queryFn: () => adminAnalyticsService.getSummary(),
    refetchInterval: 30_000,
  });

  if (isPending) {
    return (
      <div className="mx-auto max-w-5xl space-y-6 px-4 py-6 sm:px-6">
        <Skeleton className="h-8 w-48" />
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {[...Array(8)].map((_, i) => (
            <Skeleton key={i} className="h-24 rounded-2xl" />
          ))}
        </div>
        <Skeleton className="h-64 w-full rounded-2xl" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-6 sm:px-6">
        <ErrorState description="Couldn't load analytics." onAction={() => refetch()} />
      </div>
    );
  }

  const rideChartData = [
    { name: "Active", value: data.activeRides },
    { name: "Completed", value: data.completedRides },
    { name: "Cancelled", value: data.cancelledRides },
  ];

  const complaintChartData = [
    { name: "Open", value: data.openComplaints },
    { name: "Resolved", value: data.resolvedComplaints },
    { name: "Dismissed", value: data.dismissedComplaints },
  ];

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-6 sm:px-6">
      <div>
        <h1 className="text-2xl font-extrabold text-dark">Analytics</h1>
        <p className="mt-1 text-sm text-muted">Platform-wide numbers, updated every 30 seconds.</p>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <StatCard icon={Car} label="Total rides" value={String(data.totalRides)} />
        <StatCard icon={CheckCircle2} label="Completed rides" value={String(data.completedRides)} />
        <StatCard icon={XCircle} label="Cancelled rides" value={String(data.cancelledRides)} tone={data.cancelledRides > 0 ? "warning" : "neutral"} />
        <StatCard icon={Wallet} label="Total revenue" value={`₹${data.totalRevenue.toFixed(0)}`} />
        <StatCard icon={Star} label="Average rating" value={data.averageRating.toFixed(1)} />
        <StatCard icon={Users} label="Total users" value={String(data.totalUsers)} />
        <StatCard icon={ShieldCheck} label="Verified drivers" value={`${data.verifiedDrivers} / ${data.totalDrivers}`} />
        <StatCard icon={Radio} label="Online drivers now" value={String(data.onlineDriversNow)} />
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <Card>
          <CardHeader className="flex items-center justify-between">
            <p className="text-sm font-semibold text-dark">Rides by status</p>
            <Car size={16} className="text-muted" />
          </CardHeader>
          <CardBody className="h-56">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={rideChartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" vertical={false} />
                <XAxis dataKey="name" tick={{ fontSize: 11, fill: "#64748B" }} axisLine={false} tickLine={false} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: "#64748B" }} axisLine={false} tickLine={false} width={30} />
                <Tooltip cursor={{ fill: "#F8FAFC" }} contentStyle={{ borderRadius: 12, border: "1px solid #E2E8F0", fontSize: 12 }} />
                <Bar dataKey="value" fill="#F5B800" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </CardBody>
        </Card>

        <Card>
          <CardHeader className="flex items-center justify-between">
            <p className="text-sm font-semibold text-dark">Complaints by status</p>
            <MessageSquareWarning size={16} className="text-muted" />
          </CardHeader>
          <CardBody className="h-56">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={complaintChartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" vertical={false} />
                <XAxis dataKey="name" tick={{ fontSize: 11, fill: "#64748B" }} axisLine={false} tickLine={false} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: "#64748B" }} axisLine={false} tickLine={false} width={30} />
                <Tooltip cursor={{ fill: "#F8FAFC" }} contentStyle={{ borderRadius: 12, border: "1px solid #E2E8F0", fontSize: 12 }} />
                <Bar dataKey="value" fill="#EF4444" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </CardBody>
        </Card>
      </div>

      <StatCard
        icon={Siren}
        label="Active safety alerts"
        value={String(data.activeSafetyAlerts)}
        tone={data.activeSafetyAlerts > 0 ? "warning" : "neutral"}
      />
    </div>
  );
}
