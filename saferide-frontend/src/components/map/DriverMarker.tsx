import { Marker, Popup } from "react-leaflet";
import { driverIcon } from "./markerIcons";

export function DriverMarker({ latitude, longitude, label }: { latitude: number; longitude: number; label?: string }) {
  return (
    <Marker position={[latitude, longitude]} icon={driverIcon}>
      {label && <Popup>{label}</Popup>}
    </Marker>
  );
}
