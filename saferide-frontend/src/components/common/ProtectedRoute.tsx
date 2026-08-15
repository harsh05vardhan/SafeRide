import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "@/contexts/AuthContext";

/**
 * Gates every customer-only route. The backend is the real authority on
 * role (every API call is independently checked server-side) - this only
 * controls what the SPA renders, so a customer never even sees a driver-
 * or admin-shaped screen to begin with.
 */
export function ProtectedRoute() {
  const { isAuthenticated, isCustomer } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  if (!isCustomer) {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}
