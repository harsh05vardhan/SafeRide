import { Marker } from "react-leaflet";
import { myLocationIcon } from "./markerIcons";

export function MyLocationMarker({ latitude, longitude }: { latitude: number; longitude: number }) {
  return <Marker position={[latitude, longitude]} icon={myLocationIcon} interactive={false} />;
}
