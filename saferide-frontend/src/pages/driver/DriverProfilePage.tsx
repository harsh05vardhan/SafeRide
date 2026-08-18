import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link } from "react-router-dom";
import { Car, FileText, Plus, CheckCircle2, Clock, XCircle, ChevronRight, MessageSquareWarning, Phone, User as UserIcon } from "lucide-react";
import { useAuth } from "@/contexts/AuthContext";
import { useToast } from "@/contexts/ToastContext";
import { useDriverProfile } from "@/hooks/useDriverProfile";
import { driverService } from "@/services/driverService";
import { vehicleService } from "@/services/vehicleService";
import { settingsService } from "@/services/settingsService";
import { Button } from "@/components/common/Button";
import { Input } from "@/components/common/Input";
import { Label, FieldError } from "@/components/common/Label";
import { Badge } from "@/components/common/Badge";
import { Skeleton } from "@/components/common/Skeleton";
import { Card, CardBody } from "@/components/common/Card";
import { AvatarEditor } from "@/components/common/AvatarEditor";
import { AppSettingsSection } from "@/components/common/AppSettingsSection";
import { extractErrorMessage, extractFieldErrors } from "@/services/api";
import { profileSchema, type ProfileFormValues } from "@/lib/validation";
import type { VehicleType } from "@/types/ride";
import type { DocumentType } from "@/types/driver";
import type { VerificationStatus } from "@/types/common";

const vehicleSchema = z.object({
  vehicleType: z.enum(["MINI", "SEDAN", "SUV"]),
  vehicleModel: z.string().min(2, "Enter the vehicle model").max(60),
  vehicleNumber: z.string().min(4, "Enter a valid registration number").max(20),
  vehicleColor: z.string().min(2, "Enter a color").max(30),
  seatCapacity: z.coerce.number().int().min(1).max(10),
});
type VehicleFormValues = z.infer<typeof vehicleSchema>;

const documentSchema = z.object({
  documentType: z.enum(["DRIVING_LICENSE", "VEHICLE_REGISTRATION", "INSURANCE", "IDENTITY_PROOF", "PROFILE_PHOTO"]),
  documentUrl: z.string().url("Enter a valid URL").max(500),
});
type DocumentFormValues = z.infer<typeof documentSchema>;

function verificationBadge(status: VerificationStatus) {
  switch (status) {
    case "VERIFIED":
      return (
        <Badge variant="success">
          <CheckCircle2 size={12} /> Verified
        </Badge>
      );
    case "REJECTED":
      return (
        <Badge variant="danger">
          <XCircle size={12} /> Rejected
        </Badge>
      );
    default:
      return (
        <Badge variant="primary">
          <Clock size={12} /> Pending
        </Badge>
      );
  }
}

export default function DriverProfilePage() {
  const { user, updateUser } = useAuth();
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const { data: profile, isPending: profilePending } = useDriverProfile();
  // Polled, not just fetched once - an admin verifying/rejecting a vehicle or document while
  // this page is already open should show up here without a manual reload (refetchOnWindowFocus
  // is off app-wide - see App.tsx).
  const { data: vehicles, isPending: vehiclesPending } = useQuery({ queryKey: ["vehicles"], queryFn: () => vehicleService.list(), refetchInterval: 15000 });
  const { data: documents, isPending: documentsPending } = useQuery({ queryKey: ["driver-documents"], queryFn: () => driverService.listDocuments(), refetchInterval: 15000 });
  const { data: settings } = useQuery({ queryKey: ["settings", "support-contact"], queryFn: () => settingsService.getSupportContact() });

  const [showVehicleForm, setShowVehicleForm] = useState(false);
  const [showDocForm, setShowDocForm] = useState(false);
  const [activatingId, setActivatingId] = useState<number | null>(null);

  const vehicleForm = useForm<VehicleFormValues>({
    resolver: zodResolver(vehicleSchema),
    defaultValues: { vehicleType: "MINI", seatCapacity: 4 },
  });

  const documentForm = useForm<DocumentFormValues>({
    resolver: zodResolver(documentSchema),
    defaultValues: { documentType: "DRIVING_LICENSE" },
  });

  const profileForm = useForm<ProfileFormValues>({ resolver: zodResolver(profileSchema) });
  useEffect(() => {
    if (profile) profileForm.reset({ name: profile.user.name, phone: profile.user.phone });
  }, [profile, profileForm]);

  async function onUpdateProfile(values: ProfileFormValues) {
    try {
      const updated = await driverService.updateProfile(values);
      queryClient.setQueryData(["driver-profile"], updated);
      updateUser({ name: updated.user.name, phone: updated.user.phone });
      profileForm.reset({ name: updated.user.name, phone: updated.user.phone });
      showToast("Profile updated", "success");
    } catch (error) {
      const fieldErrors = extractFieldErrors(error);
      if (fieldErrors) {
        Object.entries(fieldErrors).forEach(([field, message]) => profileForm.setError(field as keyof ProfileFormValues, { message }));
      }
      showToast(extractErrorMessage(error), "error");
    }
  }

  async function onRegisterVehicle(values: VehicleFormValues) {
    try {
      await vehicleService.register(values);
      showToast("Vehicle added — awaiting admin verification.", "success");
      vehicleForm.reset({ vehicleType: "MINI", seatCapacity: 4, vehicleModel: "", vehicleNumber: "", vehicleColor: "" });
      setShowVehicleForm(false);
      await queryClient.invalidateQueries({ queryKey: ["vehicles"] });
    } catch (error) {
      const fieldErrors = extractFieldErrors(error);
      if (fieldErrors) {
        Object.entries(fieldErrors).forEach(([field, message]) => vehicleForm.setError(field as keyof VehicleFormValues, { message }));
      }
      showToast(extractErrorMessage(error), "error");
    }
  }

  async function onActivateVehicle(vehicleId: number) {
    setActivatingId(vehicleId);
    try {
      await vehicleService.activate(vehicleId);
      showToast("Active vehicle updated", "success");
      await queryClient.invalidateQueries({ queryKey: ["vehicles"] });
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setActivatingId(null);
    }
  }

  async function onUploadDocument(values: DocumentFormValues) {
    try {
      await driverService.uploadDocument(values);
      showToast("Document submitted for review", "success");
      documentForm.reset({ documentType: "DRIVING_LICENSE", documentUrl: "" });
      setShowDocForm(false);
      await queryClient.invalidateQueries({ queryKey: ["driver-documents"] });
    } catch (error) {
      const fieldErrors = extractFieldErrors(error);
      if (fieldErrors) {
        Object.entries(fieldErrors).forEach(([field, message]) => documentForm.setError(field as keyof DocumentFormValues, { message }));
      }
      showToast(extractErrorMessage(error), "error");
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-5 px-4 py-6 sm:px-6">
      <h1 className="text-2xl font-extrabold text-dark">Profile</h1>

      {/* Account */}
      <Card>
        <CardBody className="flex items-center gap-4">
          <AvatarEditor name={user?.name ?? "D"} avatarUrl={user?.avatarUrl} />
          <div className="min-w-0 flex-1">
            <p className="truncate font-semibold text-foreground">{user?.name}</p>
            <p className="truncate text-sm text-muted">{user?.email}</p>
            <p className="text-sm text-muted">{user?.phone}</p>
          </div>
          {profile && verificationBadge(profile.verificationStatus)}
        </CardBody>
      </Card>

      {/* Edit name/phone - email stays read-only, same rule the customer-side profile follows. */}
      <Card>
        <CardBody>
          <form onSubmit={profileForm.handleSubmit(onUpdateProfile)} className="space-y-3" noValidate>
            <div>
              <Label htmlFor="driver-name">
                <UserIcon size={13} className="mr-1 inline" /> Full name
              </Label>
              <Input id="driver-name" error={profileForm.formState.errors.name?.message} {...profileForm.register("name")} />
              <FieldError message={profileForm.formState.errors.name?.message} />
            </div>
            <div>
              <Label htmlFor="driver-phone">
                <Phone size={13} className="mr-1 inline" /> Phone
              </Label>
              <Input id="driver-phone" error={profileForm.formState.errors.phone?.message} {...profileForm.register("phone")} />
              <FieldError message={profileForm.formState.errors.phone?.message} />
            </div>
            <Button type="submit" isLoading={profileForm.formState.isSubmitting} disabled={!profileForm.formState.isDirty}>
              Save changes
            </Button>
          </form>
        </CardBody>
      </Card>

      {profilePending ? (
        <Skeleton className="h-16 w-full rounded-2xl" />
      ) : (
        profile && (
          <Card>
            <CardBody className="flex items-center justify-between">
              <span className="text-sm text-muted">Driving license number</span>
              <span className="font-mono text-sm font-semibold text-foreground">{profile.licenseNumber}</span>
            </CardBody>
          </Card>
        )
      )}

      {/* Vehicles */}
      <div>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-dark">
            <Car size={16} /> Vehicles
          </h2>
          <Button variant="outline" size="sm" onClick={() => setShowVehicleForm((v) => !v)}>
            <Plus size={14} /> Add vehicle
          </Button>
        </div>

        {showVehicleForm && (
          <form onSubmit={vehicleForm.handleSubmit(onRegisterVehicle)} className="mb-3 space-y-3 rounded-2xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-4 shadow-sm" noValidate>
            <div>
              <Label htmlFor="vehicleType">Vehicle type</Label>
              <div className="grid grid-cols-3 gap-2">
                {(["MINI", "SEDAN", "SUV"] as VehicleType[]).map((type) => (
                  <label
                    key={type}
                    className="flex cursor-pointer items-center justify-center rounded-xl border border-slate-300 dark:border-slate-600 py-2 text-sm font-medium has-[:checked]:border-primary has-[:checked]:bg-primary/10"
                  >
                    <input type="radio" value={type} className="sr-only" {...vehicleForm.register("vehicleType")} />
                    {type}
                  </label>
                ))}
              </div>
            </div>
            <div>
              <Label htmlFor="vehicleModel">Model</Label>
              <Input id="vehicleModel" placeholder="Maruti Swift" error={vehicleForm.formState.errors.vehicleModel?.message} {...vehicleForm.register("vehicleModel")} />
              <FieldError message={vehicleForm.formState.errors.vehicleModel?.message} />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label htmlFor="vehicleNumber">Registration no.</Label>
                <Input id="vehicleNumber" placeholder="AS01AB1234" error={vehicleForm.formState.errors.vehicleNumber?.message} {...vehicleForm.register("vehicleNumber")} />
                <FieldError message={vehicleForm.formState.errors.vehicleNumber?.message} />
              </div>
              <div>
                <Label htmlFor="vehicleColor">Color</Label>
                <Input id="vehicleColor" placeholder="White" error={vehicleForm.formState.errors.vehicleColor?.message} {...vehicleForm.register("vehicleColor")} />
                <FieldError message={vehicleForm.formState.errors.vehicleColor?.message} />
              </div>
            </div>
            <div>
              <Label htmlFor="seatCapacity">Seat capacity</Label>
              <Input id="seatCapacity" type="number" min={1} max={10} error={vehicleForm.formState.errors.seatCapacity?.message} {...vehicleForm.register("seatCapacity")} />
              <FieldError message={vehicleForm.formState.errors.seatCapacity?.message} />
            </div>
            <Button type="submit" className="w-full" isLoading={vehicleForm.formState.isSubmitting}>
              Save vehicle
            </Button>
          </form>
        )}

        <div className="space-y-2">
          {vehiclesPending ? (
            <Skeleton className="h-20 w-full rounded-2xl" />
          ) : vehicles?.length === 0 ? (
            <p className="rounded-2xl border border-dashed border-slate-300 dark:border-slate-600 bg-slate-50/60 dark:bg-white/5 p-5 text-center text-sm text-muted">No vehicles added yet.</p>
          ) : (
            vehicles?.map((vehicle) => (
              <div key={vehicle.id} className="flex items-center justify-between rounded-2xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-4 shadow-sm">
                <div>
                  <p className="text-sm font-semibold text-foreground">
                    {vehicle.vehicleModel} · {vehicle.vehicleType}
                  </p>
                  <p className="text-xs text-muted">
                    {vehicle.vehicleNumber} · {vehicle.vehicleColor} · {vehicle.seatCapacity} seats
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {verificationBadge(vehicle.verificationStatus)}
                  {vehicle.active ? (
                    <Badge variant="dark">Active</Badge>
                  ) : (
                    <Button
                      variant="outline"
                      size="sm"
                      isLoading={activatingId === vehicle.id}
                      disabled={vehicle.verificationStatus !== "VERIFIED"}
                      onClick={() => onActivateVehicle(vehicle.id)}
                    >
                      Set active
                    </Button>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {/* Documents */}
      <div>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-dark">
            <FileText size={16} /> Documents
          </h2>
          <Button variant="outline" size="sm" onClick={() => setShowDocForm((v) => !v)}>
            <Plus size={14} /> Add document
          </Button>
        </div>

        {showDocForm && (
          <form onSubmit={documentForm.handleSubmit(onUploadDocument)} className="mb-3 space-y-3 rounded-2xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-4 shadow-sm" noValidate>
            <div>
              <Label htmlFor="documentType">Document type</Label>
              <select
                id="documentType"
                className="h-11 w-full rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-card px-3.5 text-sm text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/50"
                {...documentForm.register("documentType")}
              >
                {(["DRIVING_LICENSE", "VEHICLE_REGISTRATION", "INSURANCE", "IDENTITY_PROOF", "PROFILE_PHOTO"] as DocumentType[]).map((type) => (
                  <option key={type} value={type}>
                    {type.replaceAll("_", " ")}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label htmlFor="documentUrl">Document URL</Label>
              <Input id="documentUrl" placeholder="https://…" error={documentForm.formState.errors.documentUrl?.message} {...documentForm.register("documentUrl")} />
              <FieldError message={documentForm.formState.errors.documentUrl?.message} />
              <p className="mt-1.5 text-xs text-muted">Paste a link to a hosted scan/photo. Direct file uploads arrive in a later phase.</p>
            </div>
            <Button type="submit" className="w-full" isLoading={documentForm.formState.isSubmitting}>
              Submit document
            </Button>
          </form>
        )}

        <div className="space-y-2">
          {documentsPending ? (
            <Skeleton className="h-16 w-full rounded-2xl" />
          ) : documents?.length === 0 ? (
            <p className="rounded-2xl border border-dashed border-slate-300 dark:border-slate-600 bg-slate-50/60 dark:bg-white/5 p-5 text-center text-sm text-muted">No documents submitted yet.</p>
          ) : (
            documents?.map((doc) => (
              <div key={doc.id} className="flex items-center justify-between rounded-2xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-4 shadow-sm">
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-foreground">{doc.documentType.replaceAll("_", " ")}</p>
                  <a href={doc.documentUrl} target="_blank" rel="noreferrer" className="truncate text-xs text-dark underline">
                    View document
                  </a>
                </div>
                {verificationBadge(doc.verificationStatus)}
              </div>
            ))
          )}
        </div>
      </div>

      <Link
        to="/driver/complaints"
        className="flex items-center justify-between rounded-2xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-4 shadow-sm"
      >
        <div className="flex items-center gap-3">
          <MessageSquareWarning size={18} className="text-dark" />
          <span className="text-sm font-medium text-foreground">My reports</span>
        </div>
        <ChevronRight size={16} className="text-muted" />
      </Link>

      {/* Only shown once an admin has actually set a number - a fake/placeholder support line would be worse than none. */}
      {settings?.supportPhoneNumber && (
        <a
          href={`tel:${settings.supportPhoneNumber}`}
          className="flex items-center justify-between rounded-2xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-4 shadow-sm"
        >
          <div className="flex items-center gap-3">
            <span className="flex h-9 w-9 items-center justify-center rounded-full bg-primary/15 text-dark">
              <Phone size={16} />
            </span>
            <div>
              <p className="text-sm font-medium text-foreground">Need help? Call support</p>
              <p className="text-xs text-muted">{settings.supportPhoneNumber}</p>
            </div>
          </div>
          <ChevronRight size={16} className="text-muted" />
        </a>
      )}

      <AppSettingsSection />
    </div>
  );
}
