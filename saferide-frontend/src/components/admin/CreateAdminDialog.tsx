import { AnimatePresence, motion } from "framer-motion";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { X } from "lucide-react";
import { adminUserService } from "@/services/adminUserService";
import { useToast } from "@/contexts/ToastContext";
import { extractErrorMessage, extractFieldErrors } from "@/services/api";
import { Button } from "@/components/common/Button";
import { Input } from "@/components/common/Input";
import { Label, FieldError } from "@/components/common/Label";
import { createAdminSchema, type CreateAdminFormValues } from "@/lib/validation";

interface CreateAdminDialogProps {
  open: boolean;
  onClose: () => void;
  /** Called after the account is actually created - parent refetches the user list and closes the dialog. */
  onCreated: (name: string) => void;
}

/**
 * The only in-app way to create another admin account - self-registration
 * always refuses role: ADMIN (see AuthService#register), so one admin
 * being a single point of failure for the whole platform is otherwise
 * unavoidable. Any existing admin can create another; there's no separate
 * "super admin" approval step.
 */
export function CreateAdminDialog({ open, onClose, onCreated }: CreateAdminDialogProps) {
  const { showToast } = useToast();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateAdminFormValues>({ resolver: zodResolver(createAdminSchema) });

  function handleClose() {
    reset();
    onClose();
  }

  async function submit(values: CreateAdminFormValues) {
    try {
      const created = await adminUserService.createAdmin(values);
      reset();
      onCreated(created.user.name);
    } catch (error) {
      const fieldErrors = extractFieldErrors(error);
      if (fieldErrors) {
        Object.entries(fieldErrors).forEach(([field, message]) => setError(field as keyof CreateAdminFormValues, { message }));
      }
      showToast(extractErrorMessage(error), "error");
    }
  }

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="fixed inset-0 z-[90] flex items-center justify-center bg-dark/50 p-4"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={handleClose}
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
            <div className="flex items-start justify-between">
              <div>
                <h2 className="text-lg font-bold text-dark">Add another admin</h2>
                <p className="mt-1 text-sm text-muted">They can log in immediately with the password you set below.</p>
              </div>
              <button onClick={handleClose} aria-label="Close" className="rounded-lg p-1 text-muted hover:bg-slate-100">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSubmit(submit)} className="mt-4 space-y-3" noValidate>
              <div>
                <Label htmlFor="admin-name">Name</Label>
                <Input id="admin-name" placeholder="Full name" error={errors.name?.message} {...register("name")} />
                <FieldError message={errors.name?.message} />
              </div>
              <div>
                <Label htmlFor="admin-email">Email</Label>
                <Input id="admin-email" type="email" placeholder="name@example.com" error={errors.email?.message} {...register("email")} />
                <FieldError message={errors.email?.message} />
              </div>
              <div>
                <Label htmlFor="admin-phone">Phone</Label>
                <Input id="admin-phone" placeholder="9000000000" error={errors.phone?.message} {...register("phone")} />
                <FieldError message={errors.phone?.message} />
              </div>
              <div>
                <Label htmlFor="admin-password">Password</Label>
                <Input
                  id="admin-password"
                  type="password"
                  placeholder="At least 8 characters"
                  error={errors.password?.message}
                  {...register("password")}
                />
                <FieldError message={errors.password?.message} />
              </div>

              <div className="mt-5 flex gap-3">
                <Button type="button" variant="outline" className="flex-1" onClick={handleClose} disabled={isSubmitting}>
                  Cancel
                </Button>
                <Button type="submit" className="flex-1" isLoading={isSubmitting}>
                  Create admin
                </Button>
              </div>
            </form>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
