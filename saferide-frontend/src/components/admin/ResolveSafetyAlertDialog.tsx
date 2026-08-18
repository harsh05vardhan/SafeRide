import { useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { ShieldCheck } from "lucide-react";
import { Button } from "@/components/common/Button";
import { Label } from "@/components/common/Label";

interface ResolveSafetyAlertDialogProps {
  open: boolean;
  isLoading?: boolean;
  onConfirm: (notes: string) => void;
  onCancel: () => void;
}

/**
 * Resolving an SOS alert requires a real account of what happened and what
 * was done - not a one-click status flip like ReasonConfirmDialog's
 * "reason (optional)" pattern elsewhere. The confirm button stays disabled
 * until something's actually written, on top of the backend's own
 * @NotBlank enforcement.
 */
export function ResolveSafetyAlertDialog({ open, isLoading, onConfirm, onCancel }: ResolveSafetyAlertDialogProps) {
  const [notes, setNotes] = useState("");

  function handleCancel() {
    setNotes("");
    onCancel();
  }

  function handleConfirm() {
    if (!notes.trim()) return;
    onConfirm(notes.trim());
    setNotes("");
  }

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="fixed inset-0 z-[90] flex items-center justify-center bg-dark/50 p-4"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={handleCancel}
        >
          <motion.div
            role="dialog"
            aria-modal="true"
            className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl"
            initial={{ opacity: 0, scale: 0.94, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-primary/15 text-dark">
              <ShieldCheck size={20} />
            </div>
            <h2 className="text-lg font-bold text-dark">Resolve this alert</h2>
            <p className="mt-1.5 text-sm text-muted">
              Contact the rider/driver first, or send the SafeRide team to their location - then record what happened.
            </p>

            <div className="mt-4">
              <Label htmlFor="resolution-notes">What happened?</Label>
              <textarea
                id="resolution-notes"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={4}
                maxLength={1000}
                placeholder="e.g. Called the rider - false alarm, pocket-dialed the button. Or: dispatched the safety team, resolved on-site."
                className="w-full resize-none rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-foreground placeholder:text-muted focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/50"
              />
            </div>

            <div className="mt-5 flex gap-3">
              <Button variant="outline" className="flex-1" onClick={handleCancel} disabled={isLoading}>
                Cancel
              </Button>
              <Button className="flex-1" onClick={handleConfirm} isLoading={isLoading} disabled={!notes.trim()}>
                Mark resolved
              </Button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
