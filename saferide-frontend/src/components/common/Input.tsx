import { forwardRef, type InputHTMLAttributes } from "react";
import { cn } from "@/lib/utils";

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  error?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(({ className, error, ...props }, ref) => {
  return (
    <input
      ref={ref}
      className={cn(
        "h-11 w-full rounded-xl border bg-white px-3.5 text-sm text-foreground placeholder:text-muted",
        "transition-colors focus:outline-none focus:ring-2 focus:ring-primary/50",
        error ? "border-danger focus:ring-danger/40" : "border-slate-300 focus:border-primary",
        "disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-muted",
        className,
      )}
      {...props}
    />
  );
});
Input.displayName = "Input";
