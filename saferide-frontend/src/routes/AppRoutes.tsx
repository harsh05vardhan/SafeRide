import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { ProtectedRoute } from "@/components/common/ProtectedRoute";
import { CustomerLayout } from "@/components/layout/CustomerLayout";
import { DriverLayout } from "@/components/layout/DriverLayout";
import { AdminLayout } from "@/components/layout/AdminLayout";
import { FullPageSpinner } from "@/components/common/Spinner";

const LoginPage = lazy(() => import("@/pages/auth/LoginPage"));
const RegisterPage = lazy(() => import("@/pages/auth/RegisterPage"));
const RoleLandingPage = lazy(() => import("@/pages/RoleLandingPage"));

const HomePage = lazy(() => import("@/pages/customer/HomePage"));
const RideOptionsPage = lazy(() => import("@/pages/customer/RideOptionsPage"));
const FareBreakdownPage = lazy(() => import("@/pages/customer/FareBreakdownPage"));
const FindingDriverPage = lazy(() => import("@/pages/customer/FindingDriverPage"));
const RideTrackingPage = lazy(() => import("@/pages/customer/RideTrackingPage"));
const MyRidesPage = lazy(() => import("@/pages/customer/MyRidesPage"));
const ProfilePage = lazy(() => import("@/pages/customer/ProfilePage"));
const MyComplaintsPage = lazy(() => import("@/pages/customer/MyComplaintsPage"));

const DriverDashboardPage = lazy(() => import("@/pages/driver/DriverDashboardPage"));
const DriverRidesPage = lazy(() => import("@/pages/driver/DriverRidesPage"));
const DriverRideDetailPage = lazy(() => import("@/pages/driver/DriverRideDetailPage"));
const DriverEarningsPage = lazy(() => import("@/pages/driver/DriverEarningsPage"));
const DriverProfilePage = lazy(() => import("@/pages/driver/DriverProfilePage"));

const AdminDashboardPage = lazy(() => import("@/pages/admin/AdminDashboardPage"));
const AdminDriversPage = lazy(() => import("@/pages/admin/AdminDriversPage"));
const AdminDriverDetailPage = lazy(() => import("@/pages/admin/AdminDriverDetailPage"));
const AdminComplaintsPage = lazy(() => import("@/pages/admin/AdminComplaintsPage"));
const AdminComplaintDetailPage = lazy(() => import("@/pages/admin/AdminComplaintDetailPage"));
const AdminSafetyPage = lazy(() => import("@/pages/admin/AdminSafetyPage"));
const AdminAnalyticsPage = lazy(() => import("@/pages/admin/AdminAnalyticsPage"));

const PublicTrackingPage = lazy(() => import("@/pages/PublicTrackingPage"));

/**
 * Central route table. Grows one page per phase (driver pages in Phase 8,
 * admin pages in Phase 9, ...). Kept as a single file so the whole route
 * map is visible at a glance rather than scattered.
 *
 * <p>Every page is lazy-loaded - the map stack (Leaflet + its plugins) is
 * the single heaviest dependency in the app and has no business being in
 * the bundle a first-time visitor downloads just to see the login page.
 *
 * <p>"/" is handled by RoleLandingPage rather than a hardcoded redirect,
 * since an authenticated account can now hold CUSTOMER, DRIVER, or both
 * roles - see ProtectedRoute and RoleLandingPage for how each is gated.
 */
export default function AppRoutes() {
  return (
    <Suspense fallback={<FullPageSpinner />}>
      <Routes>
        <Route path="/" element={<RoleLandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        {/* Deliberately outside every ProtectedRoute - guarded only by possessing the token itself. */}
        <Route path="/track/:token" element={<PublicTrackingPage />} />

        {/* Customer: full-bleed pages (their own map-filling chrome, no navbar) */}
        <Route element={<ProtectedRoute requiredRole="CUSTOMER" />}>
          <Route path="/ride-options" element={<RideOptionsPage />} />
          <Route path="/fare-breakdown" element={<FareBreakdownPage />} />
          <Route path="/finding-driver/:rideId" element={<FindingDriverPage />} />
        </Route>

        {/* Customer: pages with the standard navbar/bottom-tab chrome */}
        <Route element={<ProtectedRoute requiredRole="CUSTOMER" />}>
          <Route element={<CustomerLayout />}>
            <Route path="/home" element={<HomePage />} />
            <Route path="/ride/:rideId" element={<RideTrackingPage />} />
            <Route path="/rides" element={<MyRidesPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/complaints" element={<MyComplaintsPage />} />
          </Route>
        </Route>

        {/* Driver: full-bleed active-ride page (map-filling chrome, no navbar) */}
        <Route element={<ProtectedRoute requiredRole="DRIVER" />}>
          <Route path="/driver/ride/:rideId" element={<DriverRideDetailPage />} />
        </Route>

        {/* Driver: pages with the standard navbar/bottom-tab chrome */}
        <Route element={<ProtectedRoute requiredRole="DRIVER" />}>
          <Route element={<DriverLayout />}>
            <Route path="/driver/dashboard" element={<DriverDashboardPage />} />
            <Route path="/driver/rides" element={<DriverRidesPage />} />
            <Route path="/driver/earnings" element={<DriverEarningsPage />} />
            <Route path="/driver/profile" element={<DriverProfilePage />} />
            <Route path="/driver/complaints" element={<MyComplaintsPage />} />
          </Route>
        </Route>

        {/* Admin: verification console */}
        <Route element={<ProtectedRoute requiredRole="ADMIN" />}>
          <Route element={<AdminLayout />}>
            <Route path="/admin/dashboard" element={<AdminDashboardPage />} />
            <Route path="/admin/drivers" element={<AdminDriversPage />} />
            <Route path="/admin/drivers/:driverId" element={<AdminDriverDetailPage />} />
            <Route path="/admin/complaints" element={<AdminComplaintsPage />} />
            <Route path="/admin/complaints/:complaintId" element={<AdminComplaintDetailPage />} />
            <Route path="/admin/safety" element={<AdminSafetyPage />} />
            <Route path="/admin/analytics" element={<AdminAnalyticsPage />} />
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
