import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { AnimatePresence, motion } from "framer-motion";
import { Bell, CheckCheck, Inbox } from "lucide-react";
import { notificationService } from "@/services/notificationService";
import { Skeleton } from "./Skeleton";
import { cn } from "@/lib/utils";
import type { NotificationType } from "@/types/notification";

/**
 * Shared bell dropped into every navbar (customer, driver, admin) - the
 * notifications behind it are the same {@code GET /api/notifications}
 * endpoint regardless of who's logged in, only the events that produced
 * them differ per role. No WebSocket push for this yet (Phase 10's
 * channels are ride-scoped, not a generic per-user feed) - unread count
 * polls instead, same "polling is an acceptable fallback where no
 * dedicated channel exists" reasoning used elsewhere.
 */
const TYPE_ICON: Record<NotificationType, string> = {
  RIDE_REQUESTED: "🔍",
  DRIVER_ASSIGNED: "🚗",
  RIDE_COMPLETED: "🏁",
  PAYMENT_COMPLETED: "💸",
  COMPLAINT_FILED: "📝",
  COMPLAINT_RESOLVED: "✅",
};

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();

  const { data: unreadCount = 0 } = useQuery({
    queryKey: ["notifications-unread-count"],
    queryFn: () => notificationService.unreadCount(),
    refetchInterval: 20_000,
  });

  const { data: page, isPending } = useQuery({
    queryKey: ["notifications-list"],
    queryFn: () => notificationService.listMine(false, 0, 10),
    enabled: open,
  });

  useEffect(() => {
    if (!open) return;
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  async function handleMarkAsRead(id: number) {
    await notificationService.markAsRead(id);
    void queryClient.invalidateQueries({ queryKey: ["notifications-list"] });
    void queryClient.invalidateQueries({ queryKey: ["notifications-unread-count"] });
  }

  async function handleMarkAllAsRead() {
    await notificationService.markAllAsRead();
    void queryClient.invalidateQueries({ queryKey: ["notifications-list"] });
    void queryClient.invalidateQueries({ queryKey: ["notifications-unread-count"] });
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        aria-label="Notifications"
        className="relative flex h-9 w-9 items-center justify-center rounded-lg text-muted hover:bg-slate-100 hover:text-foreground"
      >
        <Bell size={18} />
        {unreadCount > 0 && (
          <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-bold leading-none text-white">
            {unreadCount > 9 ? "9+" : unreadCount}
          </span>
        )}
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -6, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -6, scale: 0.98 }}
            transition={{ duration: 0.12 }}
            className="absolute right-0 top-full z-50 mt-2 w-80 max-w-[90vw] rounded-2xl border border-slate-200 bg-white shadow-xl"
          >
            <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
              <p className="text-sm font-semibold text-dark">Notifications</p>
              {unreadCount > 0 && (
                <button
                  onClick={() => void handleMarkAllAsRead()}
                  className="flex items-center gap-1 text-xs font-medium text-dark hover:underline"
                >
                  <CheckCheck size={13} /> Mark all read
                </button>
              )}
            </div>

            <div className="max-h-96 overflow-y-auto">
              {isPending ? (
                <div className="space-y-2 p-3">
                  {[...Array(3)].map((_, i) => (
                    <Skeleton key={i} className="h-14 w-full rounded-xl" />
                  ))}
                </div>
              ) : !page || page.content.length === 0 ? (
                <div className="flex flex-col items-center gap-2 px-6 py-10 text-center">
                  <Inbox size={26} className="text-muted" />
                  <p className="text-sm text-muted">You're all caught up.</p>
                </div>
              ) : (
                page.content.map((notification) => (
                  <button
                    key={notification.id}
                    onClick={() => !notification.read && void handleMarkAsRead(notification.id)}
                    className={cn(
                      "flex w-full items-start gap-2.5 border-b border-slate-50 px-4 py-3 text-left transition-colors last:border-0 hover:bg-slate-50",
                      !notification.read && "bg-primary/5",
                    )}
                  >
                    <span className="mt-0.5 text-base leading-none">{TYPE_ICON[notification.type] ?? "🔔"}</span>
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-semibold text-foreground">{notification.title}</p>
                      <p className="mt-0.5 line-clamp-2 text-xs text-muted">{notification.message}</p>
                      <p className="mt-1 text-[11px] text-muted">
                        {new Date(notification.createdAt).toLocaleString(undefined, { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" })}
                      </p>
                    </div>
                    {!notification.read && <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-primary" />}
                  </button>
                ))
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
