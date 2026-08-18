import { useEffect } from "react";
import { Link } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { User, Mail, Phone, Calendar, ShieldCheck, ChevronRight, MessageSquareWarning } from "lucide-react";
import { customerService } from "@/services/customerService";
import { useToast } from "@/contexts/ToastContext";
import { Button } from "@/components/common/Button";
import { Input } from "@/components/common/Input";
import { Label, FieldError } from "@/components/common/Label";
import { Skeleton } from "@/components/common/Skeleton";
import { Badge } from "@/components/common/Badge";
import { AvatarEditor } from "@/components/common/AvatarEditor";
import { AppSettingsSection } from "@/components/common/AppSettingsSection";
import { extractErrorMessage } from "@/services/api";
import { profileSchema, type ProfileFormValues } from "@/lib/validation";

export default function ProfilePage() {
  const { showToast } = useToast();
  const queryClient = useQueryClient();

  const { data: profile, isPending } = useQuery({
    queryKey: ["customer-profile"],
    queryFn: () => customerService.getProfile(),
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<ProfileFormValues>({ resolver: zodResolver(profileSchema) });

  useEffect(() => {
    if (profile) reset({ name: profile.name, phone: profile.phone });
  }, [profile, reset]);

  async function onSubmit(values: ProfileFormValues) {
    try {
      const updated = await customerService.updateProfile(values);
      queryClient.setQueryData(["customer-profile"], updated);
      reset({ name: updated.name, phone: updated.phone });
      showToast("Profile updated", "success");
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    }
  }

  return (
    <div className="mx-auto max-w-lg px-4 py-6 sm:px-6">
      <h1 className="text-2xl font-extrabold text-dark">Profile</h1>
      <p className="mt-1 text-sm text-muted">Manage your account details.</p>

      <div className="mt-5 rounded-2xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-5 shadow-sm sm:p-6">
        {isPending || !profile ? (
          <div className="space-y-4">
            <Skeleton className="h-16 w-16 rounded-full" />
            <Skeleton className="h-5 w-40" />
            <Skeleton className="h-5 w-56" />
          </div>
        ) : (
          <>
            <div className="flex items-start justify-between gap-3">
              <AvatarEditor
                name={profile.name}
                avatarUrl={profile.avatarUrl}
                onSaved={(avatarUrl) => queryClient.setQueryData(["customer-profile"], { ...profile, avatarUrl })}
              />
              <Badge variant="success" className="shrink-0">
                <ShieldCheck size={12} /> {profile.status}
              </Badge>
            </div>
            <div className="mt-3 flex items-center gap-1.5 text-xs text-muted">
              <Calendar size={12} />
              Joined {new Date(profile.createdAt).toLocaleDateString(undefined, { month: "long", year: "numeric" })}
            </div>

            <form className="mt-6 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
              <div>
                <Label htmlFor="name">
                  <User size={13} className="mr-1 inline" /> Full name
                </Label>
                <Input id="name" error={errors.name?.message} {...register("name")} />
                <FieldError message={errors.name?.message} />
              </div>
              <div>
                <Label htmlFor="phone">
                  <Phone size={13} className="mr-1 inline" /> Phone
                </Label>
                <Input id="phone" error={errors.phone?.message} {...register("phone")} />
                <FieldError message={errors.phone?.message} />
              </div>
              <div>
                <Label>
                  <Mail size={13} className="mr-1 inline" /> Email
                </Label>
                <Input value={profile.email} disabled />
                <p className="mt-1 text-xs text-muted">Email changes aren't supported yet.</p>
              </div>

              <Button type="submit" isLoading={isSubmitting} disabled={!isDirty}>
                Save changes
              </Button>
            </form>
          </>
        )}
      </div>

      <Link
        to="/complaints"
        className="mt-4 flex items-center justify-between rounded-2xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-4 shadow-sm"
      >
        <div className="flex items-center gap-3">
          <MessageSquareWarning size={18} className="text-dark" />
          <span className="text-sm font-medium text-foreground">My reports</span>
        </div>
        <ChevronRight size={16} className="text-muted" />
      </Link>

      <div className="mt-4">
        <AppSettingsSection />
      </div>
    </div>
  );
}
