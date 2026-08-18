import type { LucideIcon } from "lucide-react";
import { AlertCircle, Inbox } from "lucide-react";
import { Button } from "./Button";

interface BaseStateViewProps {
  icon?: LucideIcon;
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
}

export function EmptyState({ icon: Icon = Inbox, title, description, actionLabel, onAction }: BaseStateViewProps & { title: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-2xl border border-dashed border-slate-300 dark:border-slate-600 bg-slate-50/60 dark:bg-white/5 px-6 py-14 text-center">
      <Icon size={32} className="mb-1 text-muted" />
      <p className="font-semibold text-foreground">{title}</p>
      {description && <p className="max-w-sm text-sm text-muted">{description}</p>}
      {actionLabel && onAction && (
        <Button variant="primary" size="sm" className="mt-3" onClick={onAction}>
          {actionLabel}
        </Button>
      )}
    </div>
  );
}

export function ErrorState({
  icon: Icon = AlertCircle,
  title = "Something went wrong",
  description,
  actionLabel = "Try again",
  onAction,
}: BaseStateViewProps & { title?: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-2xl border border-danger/20 bg-red-50/60 px-6 py-14 text-center">
      <Icon size={32} className="mb-1 text-danger" />
      <p className="font-semibold text-foreground">{title}</p>
      {description && <p className="max-w-sm text-sm text-muted">{description}</p>}
      {actionLabel && onAction && (
        <Button variant="outline" size="sm" className="mt-3" onClick={onAction}>
          {actionLabel}
        </Button>
      )}
    </div>
  );
}
