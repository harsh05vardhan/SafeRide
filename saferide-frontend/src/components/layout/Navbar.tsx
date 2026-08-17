import { useEffect, useRef, useState } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import { ShieldCheck, Home, Clock, User, LogOut, MessageSquareWarning, ChevronDown } from "lucide-react";
import { useAuth } from "@/contexts/AuthContext";
import { NotificationBell } from "@/components/common/NotificationBell";
import { cn } from "@/lib/utils";

const NAV_LINKS = [
  { to: "/home", label: "Home", icon: Home },
  { to: "/rides", label: "My Rides", icon: Clock },
  { to: "/complaints", label: "Reports", icon: MessageSquareWarning },
  { to: "/profile", label: "Profile", icon: User },
];

/** Avatar-initial + name + role, opening a tiny menu (Profile/Logout) - no stock photo, just the user's own first initial like every other avatar circle in this app already does. */
function UserMenu() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (!open) return;
    function onClickOutside(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, [open]);

  if (!user) return null;

  return (
    <div ref={ref} className="relative">
      <button onClick={() => setOpen((o) => !o)} className="flex items-center gap-2 rounded-full py-1 pl-1 pr-2 hover:bg-slate-100">
        <span className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-sm font-bold text-primary-foreground">
          {user.name.charAt(0).toUpperCase()}
        </span>
        <span className="hidden text-left leading-tight sm:block">
          <span className="block text-sm font-semibold text-foreground">{user.name.split(" ")[0]}</span>
          <span className="block text-[11px] text-muted">Customer</span>
        </span>
        <ChevronDown size={14} className={cn("hidden text-muted transition-transform sm:block", open && "rotate-180")} />
      </button>

      {open && (
        <div className="absolute right-0 top-full z-50 mt-2 w-44 rounded-xl border border-slate-200 bg-white p-1.5 shadow-lg">
          <Link
            to="/profile"
            onClick={() => setOpen(false)}
            className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-foreground hover:bg-slate-50"
          >
            <User size={15} /> Profile
          </Link>
          <button
            onClick={() => {
              setOpen(false);
              void logout().then(() => navigate("/login"));
            }}
            className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-danger hover:bg-slate-50"
          >
            <LogOut size={15} /> Logout
          </button>
        </div>
      )}
    </div>
  );
}

export function Navbar() {
  return (
    <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/90 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
        <Link to="/home" className="flex items-center gap-2">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <ShieldCheck size={20} />
          </div>
          <span className="text-lg font-extrabold tracking-tight text-dark">SafeRide</span>
        </Link>

        <nav className="hidden items-center gap-1 md:flex">
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
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          <NotificationBell />
          <UserMenu />
        </div>
      </div>
    </header>
  );
}

export function MobileBottomNav() {
  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 flex border-t border-slate-200 bg-white/95 backdrop-blur md:hidden">
      {NAV_LINKS.map(({ to, label, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          className={({ isActive }) =>
            cn("flex flex-1 flex-col items-center gap-1 py-2.5 text-xs font-medium", isActive ? "text-dark" : "text-muted")
          }
        >
          {({ isActive }) => (
            <>
              <span className={cn("flex h-7 w-11 items-center justify-center rounded-full transition-colors", isActive && "bg-primary/20")}>
                <Icon size={18} />
              </span>
              {label}
            </>
          )}
        </NavLink>
      ))}
    </nav>
  );
}
