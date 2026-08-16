import type { LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";

interface StatCardProps {
  icon: LucideIcon;
  label: string;
  value: string;
  tone?: "neutral" | "warning" | "success";
}

const TONE_CLASSES: Record<NonNullable<StatCardProps["tone"]>, string> = {
  neutral: "bg-primary/15 text-dark",
  warning: "bg-danger/10 text-danger",
  success: "bg-success/10 text-success",
};

export function StatCard({ icon: Icon, label, value, tone = "neutral" }: StatCardProps) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md">
      <div className={cn("mb-2 flex h-9 w-9 items-center justify-center rounded-lg", TONE_CLASSES[tone])}>
        <Icon size={17} />
      </div>
      <p className="text-xl font-extrabold text-dark">{value}</p>
      <p className="text-xs text-muted">{label}</p>
    </div>
  );
}
