import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Phone, Save } from "lucide-react";
import { settingsService } from "@/services/settingsService";
import { adminSettingsService } from "@/services/adminSettingsService";
import { Button } from "@/components/common/Button";
import { Input } from "@/components/common/Input";
import { Skeleton } from "@/components/common/Skeleton";
import { useToast } from "@/contexts/ToastContext";
import { extractErrorMessage } from "@/services/api";

/**
 * One setting for now: the support contact number shown to drivers (Driver
 * Profile) and to a blocked account failing at the login screen. Kept as
 * its own small page rather than folded into an existing one - it's a
 * platform-wide setting, not specific to drivers, users, or any other
 * existing admin screen.
 */
export default function AdminSettingsPage() {
  const { data, isPending } = useQuery({
    queryKey: ["settings", "support-contact"],
    queryFn: () => settingsService.getSupportContact(),
  });
  const [phone, setPhone] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const { showToast } = useToast();

  useEffect(() => {
    if (data) setPhone(data.supportPhoneNumber ?? "");
  }, [data]);

  async function save() {
    setIsSaving(true);
    try {
      await adminSettingsService.updateSupportContact(phone.trim());
      showToast(phone.trim() ? "Support number updated" : "Support number cleared", "success");
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className="mx-auto max-w-lg px-4 py-6 sm:px-6">
      <h1 className="text-2xl font-extrabold text-dark">Settings</h1>
      <p className="mt-1 text-sm text-muted">Platform-wide settings shown to customers and drivers across the app.</p>

      <div className="mt-5 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center gap-2">
          <span className="flex h-9 w-9 items-center justify-center rounded-full bg-primary/15 text-dark">
            <Phone size={16} />
          </span>
          <div>
            <p className="text-sm font-semibold text-foreground">Support contact number</p>
            <p className="text-xs text-muted">Shown to drivers who need help, and to blocked accounts at login.</p>
          </div>
        </div>

        {isPending ? (
          <Skeleton className="mt-4 h-11 w-full rounded-xl" />
        ) : (
          <div className="mt-4 flex gap-2">
            <Input
              type="tel"
              placeholder="e.g. +911234567890"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              className="flex-1"
            />
            <Button onClick={save} isLoading={isSaving}>
              <Save size={15} /> Save
            </Button>
          </div>
        )}
        <p className="mt-2 text-xs text-muted">Leave blank and save to remove the number - it just won't be shown anywhere.</p>
      </div>
    </div>
  );
}
