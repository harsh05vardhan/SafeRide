import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { motion } from "framer-motion";
import { ShieldCheck, User, KeyRound, Eye, EyeOff, ArrowRight, ShieldHalf } from "lucide-react";
import { useAuth } from "@/contexts/AuthContext";
import { useToast } from "@/contexts/ToastContext";
import { Button } from "@/components/common/Button";
import { Input } from "@/components/common/Input";
import { Label, FieldError } from "@/components/common/Label";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { LoginHeroIllustration } from "@/components/auth/LoginHeroIllustration";
import { extractErrorCode, extractErrorMessage } from "@/services/api";
import { settingsService } from "@/services/settingsService";
import { loginSchema, type LoginFormValues } from "@/lib/validation";

/** Small inline brand marks - not worth a whole icon-library dependency for three static logos. */
function GoogleMark() {
  return (
    <svg viewBox="0 0 24 24" width={16} height={16} aria-hidden="true">
      <path fill="#4285F4" d="M23.52 12.27c0-.85-.08-1.67-.22-2.45H12v4.63h6.47a5.53 5.53 0 0 1-2.4 3.63v3h3.88c2.27-2.09 3.57-5.17 3.57-8.81Z" />
      <path fill="#34A853" d="M12 24c3.24 0 5.95-1.07 7.94-2.92l-3.88-3c-1.08.72-2.46 1.15-4.06 1.15-3.12 0-5.77-2.11-6.71-4.94H1.29v3.1A12 12 0 0 0 12 24Z" />
      <path fill="#FBBC05" d="M5.29 14.29a7.2 7.2 0 0 1 0-4.58v-3.1H1.29a12 12 0 0 0 0 10.78l4-3.1Z" />
      <path fill="#EA4335" d="M12 4.75c1.76 0 3.34.61 4.58 1.8l3.44-3.44C17.95 1.19 15.24 0 12 0A12 12 0 0 0 1.29 6.61l4 3.1C6.23 6.86 8.88 4.75 12 4.75Z" />
    </svg>
  );
}
function FacebookMark() {
  return (
    <svg viewBox="0 0 24 24" width={16} height={16} aria-hidden="true">
      <circle cx={12} cy={12} r={12} fill="#1877F2" />
      <path fill="#fff" d="M15.12 15.5 15.6 12.4h-2.97v-2c0-.85.42-1.68 1.75-1.68h1.36V5.9s-1.23-.21-2.4-.21c-2.45 0-4.05 1.48-4.05 4.17v2.36H6.6v3.1h2.7v7.5c.54.08 1.1.13 1.67.13.57 0 1.13-.05 1.67-.13v-7.5h2.48Z" />
    </svg>
  );
}
function AppleMark() {
  return (
    <svg viewBox="0 0 24 24" width={16} height={16} aria-hidden="true" fill="#000">
      <path d="M16.36 1c.12 1.02-.29 2.02-.9 2.75-.63.75-1.68 1.33-2.7 1.25-.14-1 .35-2.05.94-2.72C14.35 1.5 15.4.98 16.36 1Zm3.3 16.32c-.42.98-.62 1.42-1.16 2.3-.76 1.22-1.83 2.74-3.15 2.75-1.17.02-1.47-.76-3.06-.75-1.58.01-1.92.77-3.09.75-1.32-.02-2.33-1.38-3.09-2.6-2.12-3.36-2.34-7.3-1.03-9.4.93-1.48 2.4-2.35 3.78-2.35 1.4 0 2.29.79 3.45.79 1.13 0 1.82-.79 3.45-.79 1.24 0 2.55.68 3.48 1.85-3.06 1.68-2.56 6.06.42 7.45Z" />
    </svg>
  );
}

export default function LoginPage() {
  const { login } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [blockedInfo, setBlockedInfo] = useState<{ message: string; supportPhone: string } | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) });

  const onSubmit = async (values: LoginFormValues) => {
    setIsSubmitting(true);
    try {
      await login(values);
      showToast("Welcome back!", "success");
      const redirectTo = (location.state as { from?: Location })?.from?.pathname ?? "/home";
      navigate(redirectTo, { replace: true });
    } catch (error) {
      const message = extractErrorMessage(error);
      // A blocked account gets a proper dialog with a tappable "Call support" action - only when
      // a number is actually configured, never a dialog offering to call a number that isn't
      // there. Anything else (wrong password, rate-limited, ...) is still just a toast.
      if (extractErrorCode(error) === "ACCOUNT_BLOCKED") {
        try {
          const settings = await settingsService.getSupportContact();
          if (settings.supportPhoneNumber) {
            setBlockedInfo({ message, supportPhone: settings.supportPhoneNumber });
          } else {
            showToast(message, "error");
          }
        } catch {
          showToast(message, "error");
        }
      } else {
        showToast(message, "error");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-background">
      <div className="mx-auto max-w-md px-5 pb-0 pt-6 sm:max-w-lg sm:pt-10">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-dark text-primary">
              <ShieldCheck size={24} />
            </div>
            <div>
              <span className="text-xl font-extrabold tracking-tight text-dark">
                Safe<span className="text-primary">Ride</span>
              </span>
              <p className="text-xs text-muted">Your ride. Your safety.</p>
            </div>
          </div>
        </div>

        {/* Hero illustration - see LoginHeroIllustration's own doc for why this is a vector
            interpretation, not a literal copy of a photographic reference. */}
        <motion.div
          className="mt-2"
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
        >
          <LoginHeroIllustration />
        </motion.div>

        {/* Headline */}
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4, delay: 0.05 }}>
          <h1 className="text-3xl font-extrabold leading-tight text-dark">
            Welcome
            <br />
            <span className="text-primary">back!</span> 👋
          </h1>
          <p className="mt-2 text-sm text-muted">
            Login to continue your safe journey with <span className="font-semibold text-dark">SafeRide</span>.
          </p>
        </motion.div>
      </div>

      {/* Form card - a bottom-sheet look on mobile, a centered card on larger screens */}
      <div className="mt-6 flex justify-center px-0 sm:mt-8 sm:px-4">
        <motion.div
          className="w-full max-w-md rounded-t-3xl bg-white p-6 shadow-xl shadow-slate-900/10 sm:max-w-lg sm:rounded-3xl sm:p-8"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.1 }}
        >
          <h2 className="text-xl font-extrabold text-dark">Login to your account</h2>
          <p className="mt-1 text-sm text-muted">Please enter your details to continue</p>

          <form className="mt-6 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
            <div>
              <Label htmlFor="identifier">Phone number or Email</Label>
              <div className="relative">
                <User size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-muted" />
                <Input
                  id="identifier"
                  placeholder="Enter phone number or email"
                  className="pl-10"
                  error={errors.identifier?.message}
                  {...register("identifier")}
                />
              </div>
              <FieldError message={errors.identifier?.message} />
            </div>
            <div>
              <div className="flex items-center justify-between">
                <Label htmlFor="password">Password</Label>
                <button type="button" className="mb-1.5 text-xs font-medium text-dark hover:underline">
                  Forgot password?
                </button>
              </div>
              <div className="relative">
                <KeyRound size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-muted" />
                <Input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  placeholder="Enter your password"
                  className="pl-10 pr-10"
                  error={errors.password?.message}
                  {...register("password")}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((s) => !s)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-muted hover:text-foreground"
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              <FieldError message={errors.password?.message} />
            </div>

            <label className="flex items-center gap-2 text-sm text-muted">
              <input type="checkbox" defaultChecked className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary/50" />
              Remember me
            </label>

            <Button type="submit" className="group w-full" size="lg" isLoading={isSubmitting}>
              Login
              <ArrowRight size={16} className="transition-transform group-hover:translate-x-0.5" />
            </Button>
          </form>

          <div className="my-6 flex items-center gap-3 text-xs text-muted">
            <div className="h-px flex-1 bg-slate-200" />
            or continue with
            <div className="h-px flex-1 bg-slate-200" />
          </div>

          {/* Not wired to a real provider yet - Google/Facebook/Apple OAuth each need their own
              developer-console app registered (Apple's requires a paid developer account), so
              this stays a styled preview rather than a working button until that's built. */}
          <div className="grid grid-cols-3 gap-3">
            {[
              { name: "Google", mark: <GoogleMark /> },
              { name: "Facebook", mark: <FacebookMark /> },
              { name: "Apple", mark: <AppleMark /> },
            ].map(({ name, mark }) => (
              <button
                key={name}
                type="button"
                disabled
                title="Coming soon"
                className="flex h-11 items-center justify-center gap-2 rounded-xl border border-slate-200 text-xs font-medium text-muted opacity-70"
              >
                {mark}
                <span className="hidden sm:inline">{name}</span>
              </button>
            ))}
          </div>

          <p className="mt-6 flex items-center justify-center gap-1.5 text-xs text-muted">
            <ShieldHalf size={13} />
            Your data is protected with industry-standard encryption
          </p>

          <p className="mt-4 text-center text-sm text-muted">
            New here?{" "}
            <Link to="/register" className="inline-flex items-center gap-1 font-semibold text-dark hover:underline">
              Sign up <ArrowRight size={13} />
            </Link>
          </p>
        </motion.div>
      </div>

      <ConfirmDialog
        open={!!blockedInfo}
        title="Account blocked"
        description={blockedInfo?.message ?? ""}
        confirmLabel={`Call ${blockedInfo?.supportPhone ?? ""}`}
        cancelLabel="Close"
        destructive
        onConfirm={() => {
          if (blockedInfo) window.location.href = `tel:${blockedInfo.supportPhone}`;
          setBlockedInfo(null);
        }}
        onCancel={() => setBlockedInfo(null)}
      />
    </div>
  );
}
