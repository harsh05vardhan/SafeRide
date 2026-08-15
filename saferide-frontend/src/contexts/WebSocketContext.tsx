import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Client, type StompSubscription } from "@stomp/stompjs";
import { useAuth } from "./AuthContext";
import { tokenStorage } from "@/lib/tokenStorage";

const WS_URL = import.meta.env.VITE_WS_BASE_URL ?? "ws://localhost:8080/ws";

type MessageHandler = (payload: unknown) => void;

interface Subscription {
  destination: string;
  onMessage: MessageHandler;
  stompSubscription?: StompSubscription;
}

interface WebSocketContextValue {
  /** True once the STOMP CONNECT frame is acknowledged - false while offline, reconnecting, or logged out. */
  connected: boolean;
  /**
   * Registers interest in a destination and returns an unsubscribe function.
   * Safe to call before the connection is up (and across reconnects) - the
   * subscription is (re)issued automatically the moment {@code onConnect}
   * fires, exactly like `client.subscribe` calls made directly inside it.
   */
  subscribe: (destination: string, onMessage: MessageHandler) => () => void;
}

const WebSocketContext = createContext<WebSocketContextValue | undefined>(undefined);

/**
 * One STOMP connection per authenticated session, shared by every page that
 * wants live ride/driver updates - see {@code WebSocketConfig} on the
 * backend for the three destination shapes this carries
 * (`/topic/rides/{id}/status`, `/topic/rides/{id}/location`,
 * `/user/queue/assignments`).
 *
 * <p>Authentication happens on every (re)connect via {@code beforeConnect}
 * rather than once at construction time, because a browser's native
 * `WebSocket` can't set an `Authorization` header on the handshake - the
 * access token instead travels as a STOMP CONNECT header, re-read fresh
 * from storage each time in case it was refreshed since the last attempt
 * (see `StompAuthChannelInterceptor` on the backend for the other half of
 * this).
 */
export function WebSocketProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef<Map<string, Subscription>>(new Map());
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }

    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 4000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      beforeConnect: () => {
        const token = tokenStorage.getAccessToken();
        client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
      },
      onConnect: () => {
        setConnected(true);
        // Re-issue every subscription a component registered while we were offline/reconnecting.
        subscriptionsRef.current.forEach((sub) => {
          sub.stompSubscription = client.subscribe(sub.destination, (message) => {
            sub.onMessage(JSON.parse(message.body));
          });
        });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    client.activate();
    clientRef.current = client;

    return () => {
      void client.deactivate();
      clientRef.current = null;
      setConnected(false);
    };
  }, [isAuthenticated]);

  const value = useMemo<WebSocketContextValue>(
    () => ({
      connected,
      subscribe: (destination, onMessage) => {
        const id = `${destination}:${Math.random().toString(36).slice(2)}`;
        const sub: Subscription = { destination, onMessage };
        subscriptionsRef.current.set(id, sub);

        const client = clientRef.current;
        if (client?.connected) {
          sub.stompSubscription = client.subscribe(destination, (message) => {
            sub.onMessage(JSON.parse(message.body));
          });
        }

        return () => {
          subscriptionsRef.current.get(id)?.stompSubscription?.unsubscribe();
          subscriptionsRef.current.delete(id);
        };
      },
    }),
    [connected],
  );

  return <WebSocketContext.Provider value={value}>{children}</WebSocketContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components -- the useX hook belongs next to its Provider, not split into a second file
export function useWebSocket(): WebSocketContextValue {
  const context = useContext(WebSocketContext);
  if (!context) {
    throw new Error("useWebSocket must be used within a WebSocketProvider");
  }
  return context;
}
