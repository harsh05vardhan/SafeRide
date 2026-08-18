import { useEffect, useState } from "react";

export interface LiveLocationState {
  position: { latitude: number; longitude: number } | null;
  /** True once the browser has actually reported a denial - not true just because permission hasn't been asked yet. */
  permissionDenied: boolean;
}

/**
 * Watches the browser's real geolocation while `enabled` - calling
 * `watchPosition` for the first time on a page is exactly what makes the
 * browser show its own native "Allow [site] to access your location?"
 * prompt; there's no separate custom dialog to build or fake here, the
 * permission prompt the customer sees genuinely comes from the browser
 * itself. Denial is remembered so this doesn't keep re-triggering a
 * prompt the browser will just auto-deny anyway.
 */
export function useLiveLocation(enabled: boolean): LiveLocationState {
  const [position, setPosition] = useState<{ latitude: number; longitude: number } | null>(null);
  const [permissionDenied, setPermissionDenied] = useState(false);

  useEffect(() => {
    if (!enabled || !navigator.geolocation) return;

    const watchId = navigator.geolocation.watchPosition(
      (result) => {
        setPermissionDenied(false);
        setPosition({ latitude: result.coords.latitude, longitude: result.coords.longitude });
      },
      (error) => {
        if (error.code === error.PERMISSION_DENIED) {
          setPermissionDenied(true);
        }
      },
      { enableHighAccuracy: true, maximumAge: 10_000 },
    );

    return () => navigator.geolocation.clearWatch(watchId);
  }, [enabled]);

  return { position, permissionDenied };
}
