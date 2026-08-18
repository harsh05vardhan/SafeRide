import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { ShieldCheck, User, Car } from "lucide-react";
import { useAuth } from "@/contexts/AuthContext";
import { useToast } from "@/contexts/ToastContext";
import { Button } from "@/components/common/Button";
import { Input } from "@/components/common/Input";
import { Label, FieldError } from "@/components/common/Label";
import { cn } from "@/lib/utils";
import { extractErrorMessage, extractFieldErrors } from "@/services/api";
import { registerSchema, type RegisterFormValues } from "@/lib/validation";

export default function RegisterPage() {
  const { register: registerAccount } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    setError,
    formState: { errors },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { role: "CUSTOMER" },
  });

  const role = watch("role");

  const onSubmit = async (values: RegisterFormValues) => {
    setIsSubmitting(true);
    try {
      await registerAccount({
        name: values.name,
        email: values.email,
        phone: values.phone,
        password: values.password,
        role: values.role,
        licenseNumber: values.role === "DRIVER" ? values.licenseNumber : undefined,
      });
      showToast("Account created! Welcome to SafeRide.", "success");
      navigate("/home", { replace: true });
    } catch (error) {
      const fieldErrors = extractFieldErrors(error);
      if (fieldErrors) {
        Object.entries(fieldErrors).forEach(([field, message]) => {
          setError(field as keyof RegisterFormValues, { message });
        });
      }
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-6 flex items-center justify-center gap-2">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <ShieldCheck size={20} />
          </div>
          <span className="text-lg font-extrabold text-dark">SafeRide</span>
        </div>

        <div className="rounded-2xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-6 shadow-sm sm:p-8">
          <h1 className="text-2xl font-bold text-dark">Create your account</h1>
          <p className="mt-1 text-sm text-muted">Book rides or start driving with SafeRide.</p>

          {/* Role picker */}
          <div className="mt-5 grid grid-cols-2 gap-3">
            {(["CUSTOMER", "DRIVER"] as const).map((option) => {
              const Icon = option === "CUSTOMER" ? User : Car;
              const isSelected = role === option;
              return (
                <button
                  key={option}
                  type="button"
                  onClick={() => setValue("role", option)}
                  className={cn(
                    "flex flex-col items-center gap-1.5 rounded-xl border-2 p-4 text-sm font-semibold transition-all",
                    isSelected ? "border-primary bg-primary/10 text-dark" : "border-slate-200 dark:border-card-border text-muted hover:border-slate-300 dark:border-slate-600",
                  )}
                >
                  <Icon size={22} />
                  {option === "CUSTOMER" ? "Customer" : "Driver"}
                </button>
              );
            })}
          </div>

          <form className="mt-5 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
            <div>
              <Label htmlFor="name">Full Name</Label>
              <Input id="name" placeholder="Jane Doe" error={errors.name?.message} {...register("name")} />
              <FieldError message={errors.name?.message} />
            </div>
            <div>
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" placeholder="you@example.com" error={errors.email?.message} {...register("email")} />
              <FieldError message={errors.email?.message} />
            </div>
            <div>
              <Label htmlFor="phone">Phone</Label>
              <Input id="phone" placeholder="9000000000" error={errors.phone?.message} {...register("phone")} />
              <FieldError message={errors.phone?.message} />
            </div>

            {role === "DRIVER" && (
              <div>
                <Label htmlFor="licenseNumber">Driving License Number</Label>
                <Input
                  id="licenseNumber"
                  placeholder="DL-1234567"
                  error={errors.licenseNumber?.message}
                  {...register("licenseNumber")}
                />
                <FieldError message={errors.licenseNumber?.message} />
                <p className="mt-1.5 text-xs text-muted">
                  You'll add your vehicle details and documents after signing up, then wait for admin verification.
                </p>
              </div>
            )}

            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label htmlFor="password">Password</Label>
                <Input id="password" type="password" placeholder="••••••••" error={errors.password?.message} {...register("password")} />
                <FieldError message={errors.password?.message} />
              </div>
              <div>
                <Label htmlFor="confirmPassword">Confirm</Label>
                <Input
                  id="confirmPassword"
                  type="password"
                  placeholder="••••••••"
                  error={errors.confirmPassword?.message}
                  {...register("confirmPassword")}
                />
                <FieldError message={errors.confirmPassword?.message} />
              </div>
            </div>

            <Button type="submit" className="w-full" size="lg" isLoading={isSubmitting}>
              Create account
            </Button>
          </form>

          <p className="mt-6 text-center text-sm text-muted">
            Already have an account?{" "}
            <Link to="/login" className="font-semibold text-dark hover:underline">
              Log in
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
