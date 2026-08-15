import { Marker, Popup } from "react-leaflet";
import { pickupIcon } from "./markerIcons";

export function PickupMarker({ latitude, longitude, address }: { latitude: number; longitude: number; address?: string }) {
  return (
    <Marker position={[latitude, longitude]} icon={pickupIcon}>
      {address && <Popup>{address}</Popup>}
    </Marker>
  );
}
