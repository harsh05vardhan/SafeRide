import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Check, Copy, ShieldAlert } from "lucide-react";
import { safetyService } from "@/services/safetyService";
import { useToast } from "@/contexts/ToastContext";
import { Button } from "@/components/common/Button";
import { Spinner } from "@/components/common/Spinner";
import { extractErrorMessage } from "@/services/api";

interface ShareRideDialogProps {
  rideId: number;
  open: boolean;
  onClose: () => void;
}

/** Generates (or re-fetches) this ride's public tracking link the moment the dialog opens - see SafetyService for why the same token comes back on repeat calls. */
export function ShareRideDialog({ rideId, open, onClose }: ShareRideDialogProps) {
  const { showToast } = useToast();
  const [url, setUrl] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!open) {
      setUrl(null);
      setCopied(false);
      return;
    }
    setIsLoading(true);
    safetyService
      .getOrCreateShareLink(rideId)
      .then((share) => setUrl(`${window.location.origin}/track/${share.shareToken}`))
      .catch((error) => showToast(extractErrorMessage(error), "error"))
      .finally(() => setIsLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps -- showToast is stable from context; re-running on it would refetch pointlessly
  }, [open, rideId]);

  async function copyLink() {
    if (!url) return;
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      showToast("Couldn't copy automatically - please copy the link manually", "error");
    }
  }

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="fixed inset-0 z-[90] flex items-end justify-center bg-dark/50 p-4 sm:items-center"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
        >
          <motion.div
            role="dialog"
            aria-modal="true"
            className="w-full max-w-sm rounded-2xl bg-white dark:bg-card p-6 shadow-xl"
            initial={{ opacity: 0, scale: 0.94, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-primary/15 text-dark">
              <ShieldAlert size={20} />
            </div>
            <h2 className="text-lg font-bold text-dark">Share this ride</h2>
            <p className="mt-1 text-sm text-muted">
              Anyone with this link can follow your trip live - no account needed. It stops working once the trip is over.
            </p>

            <div className="mt-4 flex items-center gap-2 rounded-xl border border-slate-200 dark:border-card-border bg-slate-50 dark:bg-white/5 p-3">
              {isLoading || !url ? (
                <Spinner size={16} />
              ) : (
                <span className="flex-1 truncate text-xs text-foreground">{url}</span>
              )}
            </div>

            <div className="mt-5 flex gap-3">
              <Button variant="outline" className="flex-1" onClick={onClose}>
                Close
              </Button>
              <Button className="flex-1" onClick={copyLink} disabled={!url}>
                {copied ? <Check size={16} /> : <Copy size={16} />}
                {copied ? "Copied" : "Copy link"}
              </Button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
