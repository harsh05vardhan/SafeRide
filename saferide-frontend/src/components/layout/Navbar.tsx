import { useEffect, useRef, useState } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import {
  ShieldCheck,
  Home,
  Clock,
  User,
  LogOut,
  MessageSquareWarning,
  ChevronDown,
  Menu,
  X,
  ShieldAlert,
  Plus,
} from "lucide-react";
import { useAuth } from "@/contexts/AuthContext";
import { NotificationBell } from "@/components/common/NotificationBell";
import { cn } from "@/lib/utils";

const NAV_LINKS = [
  { to: "/home", label: "Home", icon: Home },
  { to: "/rides", label: "My Rides", icon: Clock },
  { to: "/safety", label: "Safety", icon: ShieldAlert },
  { to: "/complaints", label: "Reports", icon: MessageSquareWarning },
  { to: "/profile", label: "Profile", icon: User },
];

/** The four items that fit either side of the raised center "Book Ride" button on the mobile tab bar. */
const BOTTOM_NAV_LEFT = NAV_LINKS.slice(0, 2);
const BOTTOM_NAV_RIGHT = [NAV_LINKS[2], NAV_LINKS[4]];

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

/** Slide-out drawer holding the full nav list on mobile, opened via the hamburger button - the mobile tab bar only has room for four of the five links plus the raised booking action, so Reports lives here instead of being dropped. */
function NavDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  if (!user) return null;

  return (
    <div className={cn("fixed inset-0 z-50 md:hidden", !open && "pointer-events-none")}>
      <div
        onClick={onClose}
        className={cn("absolute inset-0 bg-dark/40 transition-opacity", open ? "opacity-100" : "opacity-0")}
      />
      <div
        className={cn(
          "absolute inset-y-0 left-0 flex w-72 max-w-[80vw] flex-col bg-white shadow-xl transition-transform duration-200",
          open ? "translate-x-0" : "-translate-x-full",
        )}
      >
        <div className="flex items-center justify-between border-b border-slate-100 p-4">
          <div className="flex items-center gap-2">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary text-primary-foreground">
              <ShieldCheck size={20} />
            </div>
            <span className="text-lg font-extrabold tracking-tight text-dark">SafeRide</span>
          </div>
          <button onClick={onClose} aria-label="Close menu" className="rounded-lg p-1.5 text-muted hover:bg-slate-100">
            <X size={18} />
          </button>
        </div>

        <div className="flex items-center gap-3 border-b border-slate-100 p-4">
          <span className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-sm font-bold text-primary-foreground">
            {user.name.charAt(0).toUpperCase()}
          </span>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-foreground">{user.name}</p>
            <p className="text-xs text-muted">Customer</p>
          </div>
        </div>

        <nav className="flex-1 space-y-1 p-3">
          {NAV_LINKS.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              onClick={onClose}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium",
                  isActive ? "bg-primary/15 text-dark" : "text-muted hover:bg-slate-50 hover:text-foreground",
                )
              }
            >
              <Icon size={18} />
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-slate-100 p-3">
          <button
            onClick={() => {
              onClose();
              void logout().then(() => navigate("/login"));
            }}
            className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-danger hover:bg-slate-50"
          >
            <LogOut size={18} /> Logout
          </button>
        </div>
      </div>
    </div>
  );
}

export function Navbar() {
  const [drawerOpen, setDrawerOpen] = useState(false);

  return (
    <>
      <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
          <div className="flex items-center gap-1">
            <button
              onClick={() => setDrawerOpen(true)}
              aria-label="Open menu"
              className="-ml-1.5 rounded-lg p-1.5 text-dark hover:bg-slate-100 md:hidden"
            >
              <Menu size={22} />
            </button>
            <Link to="/home" className="flex items-center gap-2">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary text-primary-foreground">
                <ShieldCheck size={20} />
              </div>
              <span className="text-lg font-extrabold tracking-tight text-dark">SafeRide</span>
            </Link>
          </div>

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

      <NavDrawer open={drawerOpen} onClose={() => setDrawerOpen(false)} />
    </>
  );
}

function BottomNavLink({ to, label, icon: Icon }: (typeof NAV_LINKS)[number]) {
  return (
    <NavLink
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
  );
}

export function MobileBottomNav() {
  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 flex items-center border-t border-slate-200 bg-white/95 backdrop-blur md:hidden">
      {BOTTOM_NAV_LEFT.map((link) => (
        <BottomNavLink key={link.to} {...link} />
      ))}

      {/* Raised center action - the same real booking flow as the Home tab, just surfaced the way the tab bar's centerpiece is meant to be. */}
      <div className="flex flex-1 flex-col items-center">
        <Link
          to="/home"
          aria-label="Book a ride"
          className="-mt-5 flex h-14 w-14 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg ring-4 ring-white transition-transform active:scale-95"
        >
          <Plus size={24} />
        </Link>
        <span className="mt-1 text-[10px] font-medium text-muted">Book Ride</span>
      </div>

      {BOTTOM_NAV_RIGHT.map((link) => (
        <BottomNavLink key={link.to} {...link} />
      ))}
    </nav>
  );
}
