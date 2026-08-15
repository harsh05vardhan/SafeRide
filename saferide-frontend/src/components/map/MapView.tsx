import { MapContainer, TileLayer, useMap, useMapEvents } from "react-leaflet";
import { useEffect, type ReactNode } from "react";
import { cn } from "@/lib/utils";

interface MapViewProps {
  center: [number, number];
  zoom?: number;
  className?: string;
  children?: ReactNode;
  onMapClick?: (latitude: number, longitude: number) => void;
}

/**
 * Reusable Leaflet + OpenStreetMap wrapper. Every ride-related page renders
 * markers/routes through this one component so swapping the tile provider
 * (or the whole map engine, for Google Maps/Mapbox) later touches this
 * file and nothing else - see MapService's backend-side equivalent
 * abstraction for the same reasoning.
 */
export function MapView({ center, zoom = 14, className, children, onMapClick }: MapViewProps) {
  return (
    <MapContainer
      center={center}
      zoom={zoom}
      scrollWheelZoom
      className={cn("z-0 h-full w-full", className)}
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <RecenterOnChange center={center} />
      {onMapClick && <ClickHandler onMapClick={onMapClick} />}
      {children}
    </MapContainer>
  );
}

function RecenterOnChange({ center }: { center: [number, number] }) {
  const map = useMap();
  useEffect(() => {
    map.setView(center);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [center[0], center[1]]);
  return null;
}

function ClickHandler({ onMapClick }: { onMapClick: (lat: number, lng: number) => void }) {
  useMapEvents({
    click: (e) => onMapClick(e.latlng.lat, e.latlng.lng),
  });
  return null;
}
