import type { LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";

interface StatCardProps {
  icon: LucideIcon;
  label: string;
  value: string;
  tone?: "neutral" | "warning";
}

export function StatCard({ icon: Icon, label, value, tone = "neutral" }: StatCardProps) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div
        className={cn(
          "mb-2 flex h-9 w-9 items-center justify-center rounded-lg",
          tone === "warning" ? "bg-danger/10 text-danger" : "bg-primary/15 text-dark",
        )}
      >
        <Icon size={17} />
      </div>
      <p className="text-xl font-extrabold text-dark">{value}</p>
      <p className="text-xs text-muted">{label}</p>
    </div>
  );
}
