import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { motion } from "framer-motion";
import { ShieldCheck, Lock, Users, Zap, LifeBuoy } from "lucide-react";
import { useAuth } from "@/contexts/AuthContext";
import { useToast } from "@/contexts/ToastContext";
import { Button } from "@/components/common/Button";
import { Input } from "@/components/common/Input";
import { Label, FieldError } from "@/components/common/Label";
import { extractErrorMessage } from "@/services/api";
import { loginSchema, type LoginFormValues } from "@/lib/validation";

const FEATURES = [
  { icon: Lock, title: "Fare Lock Guarantee", description: "No extra charges." },
  { icon: Users, title: "Trusted Drivers", description: "Verified and highly rated." },
  { icon: Zap, title: "Instant Matching", description: "Smart algorithm for faster rides." },
  { icon: LifeBuoy, title: "Safety First", description: "SOS and trip sharing." },
];

export default function LoginPage() {
  const { login } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [isSubmitting, setIsSubmitting] = useState(false);

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
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="grid min-h-screen grid-cols-1 lg:grid-cols-2">
      {/* Left: brand panel */}
      <div className="relative hidden flex-col justify-between overflow-hidden bg-dark p-10 text-white lg:flex">
        <div className="absolute inset-0 bg-gradient-to-br from-slate-800 via-dark to-black" />
        <div
          className="absolute inset-0 opacity-40"
          style={{ backgroundImage: "radial-gradient(circle at 20% 20%, rgba(255,193,7,0.12), transparent 45%)" }}
        />
        <div className="relative z-10 flex items-center gap-2">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <ShieldCheck size={22} />
          </div>
          <span className="text-xl font-extrabold tracking-tight">SafeRide</span>
        </div>

        <motion.div
          className="relative z-10"
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
        >
          <h1 className="text-4xl font-extrabold leading-tight">
            Your ride.
            <br />
            Your safety.
            <br />
            <span className="text-primary">Our priority.</span>
          </h1>
          <p className="mt-4 max-w-md text-slate-300">
            Transparent fares, reliable rides and 24/7 safety for every journey.
          </p>
        </motion.div>

        <div className="relative z-10 grid grid-cols-2 gap-4">
          {FEATURES.map(({ icon: Icon, title, description }, i) => (
            <motion.div
              key={title}
              className="rounded-xl border border-white/10 bg-white/5 p-4 backdrop-blur"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.4, delay: 0.1 * i }}
            >
              <Icon size={20} className="text-primary" />
              <p className="mt-2 text-sm font-semibold">{title}</p>
              <p className="text-xs text-slate-400">{description}</p>
            </motion.div>
          ))}
        </div>
      </div>

      {/* Right: form */}
      <div className="flex items-center justify-center bg-background p-6 sm:p-10">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex items-center gap-2 lg:hidden">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary text-primary-foreground">
              <ShieldCheck size={20} />
            </div>
            <span className="text-lg font-extrabold text-dark">SafeRide</span>
          </div>

          <h2 className="text-2xl font-bold text-dark">Welcome back!</h2>
          <p className="mt-1 text-sm text-muted">Log in to book your next ride.</p>

          <form className="mt-6 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
            <div>
              <Label htmlFor="identifier">Phone number or Email</Label>
              <Input id="identifier" placeholder="you@example.com" error={errors.identifier?.message} {...register("identifier")} />
              <FieldError message={errors.identifier?.message} />
            </div>
            <div>
              <div className="flex items-center justify-between">
                <Label htmlFor="password">Password</Label>
                <button type="button" className="mb-1.5 text-xs font-medium text-dark hover:underline">
                  Forgot password?
                </button>
              </div>
              <Input id="password" type="password" placeholder="••••••••" error={errors.password?.message} {...register("password")} />
              <FieldError message={errors.password?.message} />
            </div>

            <label className="flex items-center gap-2 text-sm text-muted">
              <input type="checkbox" className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary/50" />
              Remember me
            </label>

            <Button type="submit" className="w-full" size="lg" isLoading={isSubmitting}>
              Login
            </Button>
          </form>

          <div className="my-6 flex items-center gap-3 text-xs text-muted">
            <div className="h-px flex-1 bg-slate-200" />
            or continue with
            <div className="h-px flex-1 bg-slate-200" />
          </div>

          <div className="grid grid-cols-3 gap-3">
            {["Google", "Facebook", "Apple"].map((provider) => (
              <button
                key={provider}
                type="button"
                disabled
                title="Coming soon"
                className="flex h-11 items-center justify-center rounded-xl border border-slate-200 text-xs font-medium text-muted opacity-60"
              >
                {provider}
              </button>
            ))}
          </div>

          <p className="mt-8 text-center text-sm text-muted">
            New here?{" "}
            <Link to="/register" className="font-semibold text-dark hover:underline">
              Sign up
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
