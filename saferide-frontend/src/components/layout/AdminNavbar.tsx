import { Link, NavLink } from "react-router-dom";
import { ShieldCheck, LayoutDashboard, Car, MessageSquareWarning, Siren, LogOut } from "lucide-react";
import { useAuth } from "@/contexts/AuthContext";
import { cn } from "@/lib/utils";

const NAV_LINKS = [
  { to: "/admin/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { to: "/admin/drivers", label: "Drivers", icon: Car },
  { to: "/admin/complaints", label: "Complaints", icon: MessageSquareWarning },
  { to: "/admin/safety", label: "Safety", icon: Siren },
];

export function AdminNavbar() {
  const { user, logout } = useAuth();

  return (
    <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/90 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
        <Link to="/admin/dashboard" className="flex items-center gap-2">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-dark text-primary">
            <ShieldCheck size={20} />
          </div>
          <span className="text-lg font-extrabold tracking-tight text-dark">
            SafeRide <span className="font-medium text-muted">Admin</span>
          </span>
        </Link>

        <nav className="flex items-center gap-1">
          {NAV_LINKS.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-1.5 rounded-lg px-3.5 py-2 text-sm font-medium transition-colors",
                  isActive ? "bg-primary/15 text-dark" : "text-muted hover:bg-slate-100 hover:text-foreground",
                )
              }
            >
              <Icon size={16} />
              <span className="hidden sm:inline">{label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="flex items-center gap-3">
          <span className="hidden text-sm font-medium text-foreground sm:inline">{user?.name}</span>
          <button
            onClick={() => void logout()}
            className="flex h-9 items-center gap-1.5 rounded-lg px-3 text-sm font-medium text-muted hover:bg-slate-100 hover:text-danger"
          >
            <LogOut size={16} />
            <span className="hidden sm:inline">Logout</span>
          </button>
        </div>
      </div>
    </header>
  );
}
