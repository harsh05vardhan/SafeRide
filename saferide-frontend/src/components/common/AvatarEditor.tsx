import { useState } from "react";
import { Image, Save, X } from "lucide-react";
import { Avatar } from "./Avatar";
import { Button } from "./Button";
import { Input } from "./Input";
import { meService } from "@/services/meService";
import { useAuth } from "@/contexts/AuthContext";
import { useToast } from "@/contexts/ToastContext";
import { extractErrorMessage } from "@/services/api";

interface AvatarEditorProps {
  name: string;
  avatarUrl?: string | null;
  /** Called after a successful save, with the new (possibly empty) URL - lets the page's own profile query stay in sync. */
  onSaved?: (avatarUrl: string | null) => void;
}

/**
 * Set or clear a profile photo - a hosted-image-URL field, the same
 * "paste a link" pattern DriverProfilePage's document uploads already use
 * (no cloud file storage is wired up for real uploads). Shared by every
 * role's profile screen so this isn't rebuilt three times.
 */
export function AvatarEditor({ name, avatarUrl, onSaved }: AvatarEditorProps) {
  const { updateUser } = useAuth();
  const { showToast } = useToast();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(avatarUrl ?? "");
  const [isSaving, setIsSaving] = useState(false);

  async function save() {
    setIsSaving(true);
    try {
      const updated = await meService.updateAvatar(value.trim());
      updateUser({ avatarUrl: updated.avatarUrl ?? null });
      onSaved?.(updated.avatarUrl ?? null);
      showToast(updated.avatarUrl ? "Photo updated" : "Photo removed", "success");
      setEditing(false);
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className="flex items-center gap-4">
      <Avatar name={name} avatarUrl={avatarUrl} size={64} className="text-xl" />
      <div className="min-w-0 flex-1">
        {editing ? (
          <div className="flex flex-wrap items-center gap-2">
            <Input
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder="https://... link to your photo"
              className="max-w-xs flex-1"
            />
            <Button size="sm" onClick={save} isLoading={isSaving}>
              <Save size={13} /> Save
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => {
                setValue(avatarUrl ?? "");
                setEditing(false);
              }}
              disabled={isSaving}
            >
              <X size={13} />
            </Button>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => setEditing(true)}
            className="flex items-center gap-1.5 text-xs font-semibold text-dark hover:underline"
          >
            <Image size={13} /> {avatarUrl ? "Change photo" : "Add a photo"}
          </button>
        )}
      </div>
    </div>
  );
}
