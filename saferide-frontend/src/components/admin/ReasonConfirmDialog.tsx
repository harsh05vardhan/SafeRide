import { useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/common/Button";
import { Label } from "@/components/common/Label";

interface ReasonConfirmDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  isLoading?: boolean;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

/**
 * Same shape as ConfirmDialog, plus an optional free-text reason - used for
 * block/reject admin actions, whose reason is stored in the audit log
 * (see AdminActionRequest) rather than a general-purpose destructive prompt.
 */
export function ReasonConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  isLoading,
  onConfirm,
  onCancel,
}: ReasonConfirmDialogProps) {
  const [reason, setReason] = useState("");

  function handleCancel() {
    setReason("");
    onCancel();
  }

  function handleConfirm() {
    onConfirm(reason.trim());
    setReason("");
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
            className="w-full max-w-sm rounded-2xl bg-white dark:bg-card p-6 shadow-xl"
            initial={{ opacity: 0, scale: 0.94, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-danger/10 text-danger">
              <AlertTriangle size={20} />
            </div>
            <h2 className="text-lg font-bold text-dark">{title}</h2>
            <p className="mt-1.5 text-sm text-muted">{description}</p>

            <div className="mt-4">
              <Label htmlFor="reason">Reason (optional)</Label>
              <textarea
                id="reason"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                rows={3}
                maxLength={500}
                placeholder="Explain why, for the audit trail…"
                className="w-full resize-none rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-card px-3.5 py-2.5 text-sm text-foreground placeholder:text-muted focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/50"
              />
            </div>

            <div className="mt-5 flex gap-3">
              <Button variant="outline" className="flex-1" onClick={handleCancel} disabled={isLoading}>
                Cancel
              </Button>
              <Button variant="danger" className="flex-1" onClick={handleConfirm} isLoading={isLoading}>
                {confirmLabel}
              </Button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
