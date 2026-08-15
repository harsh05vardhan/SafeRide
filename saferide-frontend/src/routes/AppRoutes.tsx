import { Routes, Route } from "react-router-dom";
import ScaffoldStatus from "@/pages/ScaffoldStatus";

/**
 * Central route table. Grows one page per phase (auth in Phase 7, driver
 * pages in Phase 8, admin pages in Phase 9, ...). Kept as a single file so
 * the whole route map is visible at a glance rather than scattered.
 */
export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<ScaffoldStatus />} />
    </Routes>
  );
}
