import { useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { AlertTriangle } from "lucide-react";
import { complaintService } from "@/services/complaintService";
import { useToast } from "@/contexts/ToastContext";
import { Button } from "@/components/common/Button";
import { Label } from "@/components/common/Label";
import { extractErrorMessage } from "@/services/api";
import type { ComplaintType } from "@/types/complaint";

const TYPE_LABELS: Record<ComplaintType, string> = {
  EXTRA_FARE_DEMANDED: "Driver demanded extra fare",
  UNSAFE_DRIVING: "Unsafe driving",
  RUDE_BEHAVIOR: "Rude behavior",
  VEHICLE_MISMATCH: "Vehicle didn't match the app",
  OTHER: "Something else",
};

interface ReportIssueDialogProps {
  rideId: number;
  open: boolean;
  onClose: () => void;
}

/** Shared by both the customer and driver ride screens - "who this is about" is inferred server-side from which side of the ride the caller is on (see ComplaintService). */
export function ReportIssueDialog({ rideId, open, onClose }: ReportIssueDialogProps) {
  const { showToast } = useToast();
  const [type, setType] = useState<ComplaintType>("EXTRA_FARE_DEMANDED");
  const [description, setDescription] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  function handleClose() {
    if (isSubmitting) return;
    setType("EXTRA_FARE_DEMANDED");
    setDescription("");
    onClose();
  }

  async function handleSubmit() {
    if (description.trim().length === 0) {
      showToast("Please describe what happened", "error");
      return;
    }
    setIsSubmitting(true);
    try {
      await complaintService.file(rideId, { type, description: description.trim() });
      showToast("Thanks - our team will review this", "success");
      handleClose();
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsSubmitting(false);
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
          onClick={handleClose}
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
            <h2 className="text-lg font-bold text-dark">Report an issue</h2>
            <p className="mt-1 text-sm text-muted">Our team reviews every report - see it later under your complaint history.</p>

            <div className="mt-4">
              <Label htmlFor="complaint-type">What happened?</Label>
              <select
                id="complaint-type"
                value={type}
                onChange={(e) => setType(e.target.value as ComplaintType)}
                className="h-11 w-full rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-card px-3.5 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/50"
              >
                {(Object.entries(TYPE_LABELS) as [ComplaintType, string][]).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </div>

            <div className="mt-4">
              <Label htmlFor="complaint-description">Details</Label>
              <textarea
                id="complaint-description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={4}
                maxLength={1000}
                placeholder="Tell us what happened…"
                className="w-full resize-none rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-card px-3.5 py-2.5 text-sm text-foreground placeholder:text-muted focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/50"
              />
            </div>

            <div className="mt-5 flex gap-3">
              <Button variant="outline" className="flex-1" onClick={handleClose} disabled={isSubmitting}>
                Cancel
              </Button>
              <Button variant="danger" className="flex-1" onClick={handleSubmit} isLoading={isSubmitting}>
                Submit report
              </Button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
