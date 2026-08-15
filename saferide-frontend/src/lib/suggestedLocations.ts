import type { LocationPoint } from "@/contexts/BookingContext";

/**
 * Fixed, known-coordinate landmarks standing in for a real geocoding
 * provider (MapService is a backend-only abstraction - the frontend has
 * no address-to-coordinates lookup of its own yet). A user picks a
 * suggestion or drops a pin on the map; free-text address search isn't
 * possible until a real map provider (Google Maps/Mapbox) is wired in.
 */
export const SUGGESTED_LOCATIONS: LocationPoint[] = [
  { label: "Guwahati Railway Station", address: "Guwahati Railway Station, Paltan Bazaar", latitude: 26.1845, longitude: 91.7362 },
  { label: "Fancy Bazar", address: "Fancy Bazar, Guwahati", latitude: 26.1867, longitude: 91.7458 },
  { label: "Dispur", address: "Dispur, Guwahati", latitude: 26.1433, longitude: 91.7898 },
  { label: "Panbazar", address: "Panbazar, Guwahati", latitude: 26.1858, longitude: 91.7536 },
  { label: "Guwahati Airport", address: "Lokpriya Gopinath Bordoloi International Airport", latitude: 26.1061, longitude: 91.5859 },
];

export const MAP_DEFAULT_CENTER: [number, number] = [26.1845, 91.7362];
