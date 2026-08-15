import { Marker, Popup } from "react-leaflet";
import { destinationIcon } from "./markerIcons";

export function DestinationMarker({ latitude, longitude, address }: { latitude: number; longitude: number; address?: string }) {
  return (
    <Marker position={[latitude, longitude]} icon={destinationIcon}>
      {address && <Popup>{address}</Popup>}
    </Marker>
  );
}
