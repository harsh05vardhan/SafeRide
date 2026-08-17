import { Outlet } from "react-router-dom";
import { Navbar, MobileBottomNav } from "./Navbar";

/** Shared chrome for every authenticated customer page - top navbar on desktop, bottom tab bar on mobile. */
export function CustomerLayout() {
  return (
    <div className="flex min-h-screen flex-col bg-background">
      <Navbar />
      <main className="relative flex-1 pb-20 md:pb-0">
        <Outlet />
      </main>
      <MobileBottomNav />
    </div>
  );
}
