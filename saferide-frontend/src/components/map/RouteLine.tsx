import { Polyline } from "react-leaflet";

/** Straight line between two points - a stand-in for a real routed polyline, which needs a real routing provider (Phase 11+/MapService swap). */
export function RouteLine({ from, to }: { from: [number, number]; to: [number, number] }) {
  return <Polyline positions={[from, to]} pathOptions={{ color: "#0F172A", weight: 3, dashArray: "6 8", opacity: 0.6 }} />;
}
