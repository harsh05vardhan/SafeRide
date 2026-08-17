import { useEffect, useState } from "react";

/**
 * Seconds remaining until `totalSeconds` have elapsed since `from` (an ISO
 * timestamp) - or `null` if there's nothing to count down (`from` is
 * missing). Recomputed from the wall clock on every tick rather than
 * decremented by hand, so it's correct immediately even right after a page
 * reload or a tab that was backgrounded for a while, and always resyncs
 * automatically if `from` changes (e.g. a fresh server value after a poll).
 */
export function useCountdown(from: string | null | undefined, totalSeconds: number): number | null {
  const [, tick] = useState(0);

  useEffect(() => {
    if (!from) return;
    const id = setInterval(() => tick((t) => t + 1), 1000);
    return () => clearInterval(id);
  }, [from]);

  if (!from) return null;
  const elapsedSeconds = (Date.now() - new Date(from).getTime()) / 1000;
  return Math.max(0, Math.ceil(totalSeconds - elapsedSeconds));
}
