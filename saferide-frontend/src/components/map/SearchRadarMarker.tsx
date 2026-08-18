import { Marker } from "react-leaflet";
import L from "leaflet";

const RING_SIZES = [80, 140, 200];

/**
 * The "actively searching" animation on FindingDriverPage's map - three
 * amber rings expanding outward from the pickup point, staggered so a new
 * one starts as the last one fades (same pulse-keyframe technique as
 * MyLocationMarker's single dot, just three of them layered). A real,
 * honest loading indicator - it doesn't imply any actual driver position,
 * only "we're looking here".
 */
function radarIcon(): L.DivIcon {
  const rings = RING_SIZES.map(
    (size, i) => `
      <div class="saferide-radar-ring" style="
        position:absolute; top:50%; left:50%; width:${size}px; height:${size}px;
        margin:${-size / 2}px 0 0 ${-size / 2}px; border-radius:9999px;
        background: radial-gradient(circle, rgba(245,184,0,0.35) 0%, rgba(245,184,0,0) 70%);
        animation-delay: ${i * 0.8}s;
      "></div>`,
  ).join("");

  return L.divIcon({
    className: "",
    html: `<div style="width:1px; height:1px; position:relative; transform: translate(-50%, -50%);">${rings}</div>`,
    iconSize: [1, 1],
    iconAnchor: [0, 0],
  });
}

const ICON = radarIcon();

export function SearchRadarMarker({ latitude, longitude }: { latitude: number; longitude: number }) {
  return <Marker position={[latitude, longitude]} icon={ICON} interactive={false} />;
}
