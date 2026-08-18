import { LogOut, Info } from "lucide-react";
import { useAuth } from "@/contexts/AuthContext";

/**
 * The one "Settings" block every role's account page shares: the real
 * build version (see vite.config.ts - read from package.json, not typed
 * by hand), a "Made in India" line, and logout. Kept intentionally small -
 * nothing here is decorative, and there's nothing else genuinely
 * platform-wide to put in it yet.
 */
export function AppSettingsSection() {
  const { logout } = useAuth();

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <p className="mb-3 text-sm font-semibold text-dark">Settings</p>

      <div className="flex items-center justify-between py-2 text-sm">
        <span className="flex items-center gap-2 text-muted">
          <Info size={15} /> App version
        </span>
        <span className="font-medium text-foreground">v{__APP_VERSION__}</span>
      </div>

      <div className="border-t border-slate-100 py-2 text-center text-xs text-muted">Made in India 🇮🇳</div>

      <button
        onClick={() => void logout()}
        className="mt-2 flex w-full items-center justify-center gap-2 rounded-xl border border-danger/30 bg-danger/5 py-3 text-sm font-semibold text-danger"
      >
        <LogOut size={16} /> Log out
      </button>
    </div>
  );
}
