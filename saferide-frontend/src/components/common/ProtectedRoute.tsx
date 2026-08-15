import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "@/contexts/AuthContext";
import type { RoleName } from "@/types/auth";

/**
 * Gates every role-specific route. The backend is the real authority on
 * role (every API call is independently checked server-side) - this only
 * controls what the SPA renders, so a customer never even sees a driver-
 * or admin-shaped screen to begin with.
 */
export function ProtectedRoute({ requiredRole }: { requiredRole: Extract<RoleName, "CUSTOMER" | "DRIVER"> }) {
  const { isAuthenticated, isCustomer, isDriver } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  const hasRequiredRole = requiredRole === "CUSTOMER" ? isCustomer : isDriver;
  if (!hasRequiredRole) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
