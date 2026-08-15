import { useEffect, useRef } from "react";
import { useWebSocket } from "@/contexts/WebSocketContext";
import type { DriverLocationEvent, RideAssignmentEvent, RideStatusEvent } from "@/types/ws";

/**
 * Three thin wrappers around {@code useWebSocket().subscribe} - one per
 * destination shape the backend's {@code WebSocketConfig} defines. Each
 * keeps the latest `onEvent` callback in a ref rather than the effect's
 * dependency array, so callers don't have to memoize an inline arrow
 * function just to avoid resubscribing every render.
 */

export function useRideStatusChannel(rideId: number | undefined, onEvent: (event: RideStatusEvent) => void) {
  const { subscribe } = useWebSocket();
  const handlerRef = useRef(onEvent);
  handlerRef.current = onEvent;

  useEffect(() => {
    if (!rideId) return;
    return subscribe(`/topic/rides/${rideId}/status`, (payload) => handlerRef.current(payload as RideStatusEvent));
  }, [rideId, subscribe]);
}

export function useRideLocationChannel(rideId: number | undefined, onEvent: (event: DriverLocationEvent) => void) {
  const { subscribe } = useWebSocket();
  const handlerRef = useRef(onEvent);
  handlerRef.current = onEvent;

  useEffect(() => {
    if (!rideId) return;
    return subscribe(`/topic/rides/${rideId}/location`, (payload) => handlerRef.current(payload as DriverLocationEvent));
  }, [rideId, subscribe]);
}

/** A driver's private channel - no rideId needed to subscribe, unlike the two above. */
export function useDriverAssignmentChannel(enabled: boolean, onEvent: (event: RideAssignmentEvent) => void) {
  const { subscribe } = useWebSocket();
  const handlerRef = useRef(onEvent);
  handlerRef.current = onEvent;

  useEffect(() => {
    if (!enabled) return;
    return subscribe("/user/queue/assignments", (payload) => handlerRef.current(payload as RideAssignmentEvent));
  }, [enabled, subscribe]);
}
