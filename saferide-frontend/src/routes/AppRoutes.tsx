import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { ProtectedRoute } from "@/components/common/ProtectedRoute";
import { CustomerLayout } from "@/components/layout/CustomerLayout";
import { FullPageSpinner } from "@/components/common/Spinner";
import { useAuth } from "@/contexts/AuthContext";

const LoginPage = lazy(() => import("@/pages/auth/LoginPage"));
const RegisterPage = lazy(() => import("@/pages/auth/RegisterPage"));
const HomePage = lazy(() => import("@/pages/customer/HomePage"));
const RideOptionsPage = lazy(() => import("@/pages/customer/RideOptionsPage"));
const FareBreakdownPage = lazy(() => import("@/pages/customer/FareBreakdownPage"));
const FindingDriverPage = lazy(() => import("@/pages/customer/FindingDriverPage"));
const RideTrackingPage = lazy(() => import("@/pages/customer/RideTrackingPage"));
const MyRidesPage = lazy(() => import("@/pages/customer/MyRidesPage"));
const ProfilePage = lazy(() => import("@/pages/customer/ProfilePage"));

/**
 * Central route table. Grows one page per phase (driver pages in Phase 8,
 * admin pages in Phase 9, ...). Kept as a single file so the whole route
 * map is visible at a glance rather than scattered.
 *
 * <p>Every page is lazy-loaded - the map stack (Leaflet + its plugins) is
 * the single heaviest dependency in the app and has no business being in
 * the bundle a first-time visitor downloads just to see the login page.
 */
export default function AppRoutes() {
  const { isAuthenticated } = useAuth();

  return (
    <Suspense fallback={<FullPageSpinner />}>
      <Routes>
        <Route path="/" element={<Navigate to={isAuthenticated ? "/home" : "/login"} replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        {/* Full-bleed pages (their own map-filling chrome, no navbar) */}
        <Route element={<ProtectedRoute />}>
          <Route path="/ride-options" element={<RideOptionsPage />} />
          <Route path="/fare-breakdown" element={<FareBreakdownPage />} />
          <Route path="/finding-driver/:rideId" element={<FindingDriverPage />} />
        </Route>

        {/* Pages with the standard navbar/bottom-tab chrome */}
        <Route element={<ProtectedRoute />}>
          <Route element={<CustomerLayout />}>
            <Route path="/home" element={<HomePage />} />
            <Route path="/ride/:rideId" element={<RideTrackingPage />} />
            <Route path="/rides" element={<MyRidesPage />} />
            <Route path="/profile" element={<ProfilePage />} />
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
