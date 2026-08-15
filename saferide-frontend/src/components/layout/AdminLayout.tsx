import { Outlet } from "react-router-dom";
import { AdminNavbar } from "./AdminNavbar";

/**
 * Shared chrome for every authenticated admin page. Unlike Customer/DriverLayout,
 * there's no mobile bottom-tab nav - the admin console is a desktop-first
 * review tool (tables, multi-field detail screens), not something admins are
 * expected to operate one-handed from a phone.
 */
export function AdminLayout() {
  return (
    <div className="flex min-h-screen flex-col bg-background">
      <AdminNavbar />
      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  );
}
