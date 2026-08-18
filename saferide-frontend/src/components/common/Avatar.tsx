import { useState } from "react";
import { cn } from "@/lib/utils";

interface AvatarProps {
  name: string;
  avatarUrl?: string | null;
  size?: number;
  className?: string;
}

/**
 * A real photo when one is set, the same initial-letter circle every
 * avatar in this app already fell back to otherwise - one component so
 * "what does an avatar look like" can't drift between the dozen places
 * that show one (navbars, driver/customer cards, safety alerts, the
 * admin dashboard's driver leaderboard, ...). Falls back to the initial
 * automatically if the URL turns out to be dead rather than showing a
 * broken-image icon.
 */
export function Avatar({ name, avatarUrl, size = 36, className }: AvatarProps) {
  const [failed, setFailed] = useState(false);
  const style = { width: size, height: size, fontSize: Math.max(11, size * 0.4) };

  if (avatarUrl && !failed) {
    return (
      <img
        src={avatarUrl}
        alt={name}
        style={style}
        className={cn("shrink-0 rounded-full object-cover", className)}
        onError={() => setFailed(true)}
      />
    );
  }

  return (
    <span
      style={style}
      className={cn("flex shrink-0 items-center justify-center rounded-full bg-primary font-bold text-primary-foreground", className)}
    >
      {name.charAt(0).toUpperCase()}
    </span>
  );
}
