import { Outlet } from "react-router-dom";
import { DriverNavbar, DriverMobileBottomNav } from "./DriverNavbar";

/** Shared chrome for every authenticated driver page - mirrors CustomerLayout with driver-scoped nav links. */
export function DriverLayout() {
  return (
    <div className="flex min-h-screen flex-col bg-background">
      <DriverNavbar />
      <main className="flex-1 pb-16 md:pb-0">
        <Outlet />
      </main>
      <DriverMobileBottomNav />
    </div>
  );
}
