import L from "leaflet";

/**
 * Leaflet's default marker images resolve relative to the bundler's asset
 * pipeline and break under Vite without extra config - so every marker in
 * SafeRide uses a hand-built divIcon (a small colored pin/dot rendered as
 * inline SVG) instead of the default image-based icon. Also lets each
 * marker type carry the SafeRide brand color instead of Leaflet's generic
 * blue teardrop.
 */
function pinIcon(color: string, glyph: string): L.DivIcon {
  return L.divIcon({
    className: "",
    html: `
      <div style="
        width: 34px; height: 34px; display:flex; align-items:center; justify-content:center;
        transform: translate(-50%, -100%);
      ">
        <svg width="34" height="34" viewBox="0 0 34 34" xmlns="http://www.w3.org/2000/svg">
          <path d="M17 0C9 0 3 6.2 3 13.6 3 23.8 17 34 17 34s14-10.2 14-20.4C31 6.2 25 0 17 0z" fill="${color}"/>
          <circle cx="17" cy="13.5" r="7" fill="white"/>
          <text x="17" y="18" font-size="11" text-anchor="middle" fill="${color}" font-family="system-ui" font-weight="700">${glyph}</text>
        </svg>
      </div>`,
    iconSize: [34, 34],
    iconAnchor: [17, 34],
  });
}

export const pickupIcon = pinIcon("#0F172A", "P");
export const destinationIcon = pinIcon("#EF4444", "D");

export const driverIcon = L.divIcon({
  className: "",
  html: `
    <div style="
      width: 30px; height: 30px; border-radius: 9999px; background:#FFC107; border: 3px solid white;
      box-shadow: 0 2px 6px rgba(15,23,42,0.35); display:flex; align-items:center; justify-content:center;
      transform: translate(-50%, -50%);
    ">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#0F172A" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M14 16H9m10 0h3v-3.15a1 1 0 0 0-.84-.99L16 11l-2.7-3.6a1 1 0 0 0-.8-.4H5.24a2 2 0 0 0-1.8 1.1l-.8 1.63A6 6 0 0 0 2 12.42V16h2"/>
        <circle cx="6.5" cy="16.5" r="2.5"/>
        <circle cx="16.5" cy="16.5" r="2.5"/>
      </svg>
    </div>`,
  iconSize: [30, 30],
  iconAnchor: [15, 15],
});
