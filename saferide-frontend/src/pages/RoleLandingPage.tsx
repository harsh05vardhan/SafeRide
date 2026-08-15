import { Navigate, Link } from "react-router-dom";
import { ShieldCheck, User, Car } from "lucide-react";
import { useAuth } from "@/contexts/AuthContext";

/**
 * "/" itself. Most accounts have exactly one of CUSTOMER/DRIVER and are
 * redirected straight through; an account with both (the backend
 * explicitly supports a customer later becoming a driver too, keeping
 * both roles) sees a lightweight chooser instead of guessing which app
 * they meant to open.
 */
export default function RoleLandingPage() {
  const { isAuthenticated, isCustomer, isDriver, user } = useAuth();

  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (isCustomer && !isDriver) return <Navigate to="/home" replace />;
  if (isDriver && !isCustomer) return <Navigate to="/driver/dashboard" replace />;
  if (!isCustomer && !isDriver) return <Navigate to="/login" replace />;

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4">
      <div className="mb-8 flex items-center gap-2">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-primary-foreground">
          <ShieldCheck size={22} />
        </div>
        <span className="text-xl font-extrabold text-dark">SafeRide</span>
      </div>
      <h1 className="text-xl font-bold text-dark">Welcome back, {user?.name.split(" ")[0]}</h1>
      <p className="mt-1 text-sm text-muted">Your account can book rides and drive - what would you like to do?</p>

      <div className="mt-8 grid w-full max-w-sm grid-cols-2 gap-4">
        <Link
          to="/home"
          className="flex flex-col items-center gap-2 rounded-2xl border border-slate-200 bg-white p-6 text-center shadow-sm transition-shadow hover:shadow-md"
        >
          <User size={28} className="text-dark" />
          <span className="font-semibold text-foreground">Book a ride</span>
        </Link>
        <Link
          to="/driver/dashboard"
          className="flex flex-col items-center gap-2 rounded-2xl border border-slate-200 bg-white p-6 text-center shadow-sm transition-shadow hover:shadow-md"
        >
          <Car size={28} className="text-dark" />
          <span className="font-semibold text-foreground">Drive</span>
        </Link>
      </div>
    </div>
  );
}
