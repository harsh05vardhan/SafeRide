import { LogOut, Info, Moon, Sun } from "lucide-react";
import { useAuth } from "@/contexts/AuthContext";
import { useTheme } from "@/contexts/ThemeContext";

/**
 * The one "Settings" block every role's account page shares: day/night
 * mode, the real build version (see vite.config.ts - read from
 * package.json, not typed by hand), a "Made in India" line, and logout.
 */
export function AppSettingsSection() {
  const { logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === "dark";

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-card-border dark:bg-card">
      <p className="mb-3 text-sm font-semibold text-dark">Settings</p>

      <div className="flex items-center justify-between py-2 text-sm">
        <span className="flex items-center gap-2 text-muted">
          {isDark ? <Moon size={15} /> : <Sun size={15} />} Day / Night mode
        </span>
        <button
          type="button"
          role="switch"
          aria-checked={isDark}
          aria-label="Toggle dark mode"
          onClick={toggleTheme}
          className={`relative h-6 w-11 shrink-0 rounded-full transition-colors ${isDark ? "bg-primary" : "bg-slate-300 dark:bg-slate-600"}`}
        >
          <span
            className={`absolute left-0.5 top-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform ${
              isDark ? "translate-x-5" : "translate-x-0"
            }`}
          />
        </button>
      </div>

      <div className="flex items-center justify-between border-t border-slate-100 py-2 text-sm dark:border-card-border">
        <span className="flex items-center gap-2 text-muted">
          <Info size={15} /> App version
        </span>
        <span className="font-medium text-foreground">v{__APP_VERSION__}</span>
      </div>

      <div className="border-t border-slate-100 py-2 text-center text-xs text-muted dark:border-card-border">Made in India 🇮🇳</div>

      <button
        onClick={() => void logout()}
        className="mt-2 flex w-full items-center justify-center gap-2 rounded-xl border border-danger/30 bg-danger/5 py-3 text-sm font-semibold text-danger"
      >
        <LogOut size={16} /> Log out
      </button>
    </div>
  );
}
