/** Must match the backend's app.matching.search-timeout-seconds (default 60s, see application.yml) - purely cosmetic if the two ever drift (the backend's own timer is authoritative and cancels the ride regardless), but keeping them equal is what makes the customer's countdown hit zero at the same moment the ride actually gets cancelled. */
export const RIDE_SEARCH_TIMEOUT_SECONDS = 60;

/** "0:45" - always at least one digit of seconds, zero-padded. */
export function formatCountdown(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${s.toString().padStart(2, "0")}`;
}
