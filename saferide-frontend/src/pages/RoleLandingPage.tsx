import { Navigate, Link } from "react-router-dom";
import { ShieldCheck, User, Car, LayoutDashboard } from "lucide-react";
import { useAuth } from "@/contexts/AuthContext";

const ROLE_CARDS = [
  { role: "isCustomer" as const, to: "/home", label: "Book a ride", icon: User },
  { role: "isDriver" as const, to: "/driver/dashboard", label: "Drive", icon: Car },
  { role: "isAdmin" as const, to: "/admin/dashboard", label: "Admin console", icon: LayoutDashboard },
];

/**
 * "/" itself. Most accounts have exactly one role and are redirected
 * straight through; an account holding more than one (the backend
 * explicitly supports a customer later becoming a driver too, keeping
 * both roles) sees a lightweight chooser instead of guessing which app
 * they meant to open.
 */
export default function RoleLandingPage() {
  const auth = useAuth();
  const { isAuthenticated, user } = auth;

  if (!isAuthenticated) return <Navigate to="/login" replace />;

  const available = ROLE_CARDS.filter((card) => auth[card.role]);
  if (available.length === 0) return <Navigate to="/login" replace />;
  if (available.length === 1) return <Navigate to={available[0].to} replace />;

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4">
      <div className="mb-8 flex items-center gap-2">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-primary-foreground">
          <ShieldCheck size={22} />
        </div>
        <span className="text-xl font-extrabold text-dark">SafeRide</span>
      </div>
      <h1 className="text-xl font-bold text-dark">Welcome back, {user?.name.split(" ")[0]}</h1>
      <p className="mt-1 text-sm text-muted">Your account has more than one role - what would you like to do?</p>

      <div className="mt-8 grid w-full max-w-sm gap-4" style={{ gridTemplateColumns: `repeat(${available.length}, minmax(0, 1fr))` }}>
        {available.map(({ to, label, icon: Icon }) => (
          <Link
            key={to}
            to={to}
            className="flex flex-col items-center gap-2 rounded-2xl border border-slate-200 bg-white p-6 text-center shadow-sm transition-shadow hover:shadow-md"
          >
            <Icon size={28} className="text-dark" />
            <span className="font-semibold text-foreground">{label}</span>
          </Link>
        ))}
      </div>
    </div>
  );
}
