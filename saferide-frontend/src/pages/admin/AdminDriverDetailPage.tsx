import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Car, FileText, CheckCircle2, XCircle, Ban, ShieldCheck, Star, MessageSquare } from "lucide-react";
import { adminService } from "@/services/adminService";
import { useToast } from "@/contexts/ToastContext";
import { Button } from "@/components/common/Button";
import { Skeleton } from "@/components/common/Skeleton";
import { ErrorState } from "@/components/common/StateViews";
import { Card, CardBody } from "@/components/common/Card";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { ReasonConfirmDialog } from "@/components/admin/ReasonConfirmDialog";
import { VerificationBadge, AccountStatusBadge } from "@/components/admin/StatusBadges";
import { Avatar } from "@/components/common/Avatar";
import { extractErrorMessage } from "@/services/api";

type PendingAction =
  | { kind: "block" }
  | { kind: "unblock" }
  | { kind: "rejectVehicle"; id: number }
  | { kind: "rejectDocument"; id: number }
  | null;

export default function AdminDriverDetailPage() {
  const { driverId } = useParams<{ driverId: string }>();
  const id = Number(driverId);
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<PendingAction>(null);

  const { data, isPending, isError, refetch } = useQuery({
    queryKey: ["admin-driver", id],
    queryFn: () => adminService.getDriver(id),
    enabled: Number.isFinite(id),
  });

  async function invalidate() {
    await queryClient.invalidateQueries({ queryKey: ["admin-driver", id] });
    await queryClient.invalidateQueries({ queryKey: ["admin-drivers"] });
    await queryClient.invalidateQueries({ queryKey: ["admin-driver-count"] });
  }

  async function run(key: string, action: () => Promise<unknown>, successMessage: string) {
    setBusyAction(key);
    try {
      await action();
      showToast(successMessage, "success");
      await invalidate();
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setBusyAction(null);
      setPendingAction(null);
    }
  }

  if (isPending) {
    return (
      <div className="mx-auto max-w-3xl space-y-4 px-4 py-6 sm:px-6">
        <Skeleton className="h-8 w-40" />
        <Skeleton className="h-32 w-full rounded-2xl" />
        <Skeleton className="h-40 w-full rounded-2xl" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-6 sm:px-6">
        <ErrorState description="Couldn't load this driver." onAction={() => refetch()} />
      </div>
    );
  }

  const { driver, vehicles, documents, completedRideCount, cancelledRideCount, totalEarnings, recentRatings } = data;
  const isBlocked = driver.accountStatus === "BLOCKED";
  const canApprove = driver.verificationStatus !== "VERIFIED";

  return (
    <div className="mx-auto max-w-3xl space-y-5 px-4 py-6 sm:px-6">
      <Link to="/admin/drivers" className="flex items-center gap-1.5 text-sm font-medium text-muted hover:text-foreground">
        <ArrowLeft size={15} /> Back to drivers
      </Link>

      <Card>
        <CardBody>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="flex items-center gap-3">
              <Avatar name={driver.user.name} avatarUrl={driver.user.avatarUrl} size={48} />
              <div>
                <p className="text-lg font-bold text-dark">{driver.user.name}</p>
                <p className="text-sm text-muted">{driver.user.email} · {driver.user.phone}</p>
                <p className="mt-1 font-mono text-xs text-muted">License {driver.licenseNumber}</p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <VerificationBadge status={driver.verificationStatus} />
              <AccountStatusBadge status={driver.accountStatus} />
            </div>
          </div>

          <div className="my-4 border-t border-slate-100 dark:border-card-border" />

          <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3 lg:grid-cols-4">
            <div>
              <p className="text-xs text-muted">Total earnings</p>
              <p className="font-semibold text-foreground">₹{totalEarnings.toFixed(0)}</p>
            </div>
            <div>
              <p className="text-xs text-muted">Rating</p>
              <p className="flex items-center gap-1 font-semibold text-foreground">
                <Star size={13} className="fill-primary text-primary" /> {driver.rating.toFixed(1)}
              </p>
            </div>
            <div>
              <p className="text-xs text-muted">Rides completed</p>
              <p className="font-semibold text-success">{completedRideCount}</p>
            </div>
            <div>
              <p className="text-xs text-muted">Rides cancelled</p>
              <p className="font-semibold text-danger">{cancelledRideCount}</p>
            </div>
            <div>
              <p className="text-xs text-muted">Reliability</p>
              <p className="font-semibold text-foreground">{driver.reliabilityScore.toFixed(0)}</p>
            </div>
            <div>
              <p className="text-xs text-muted">Acceptance rate</p>
              <p className="font-semibold text-foreground">{driver.acceptanceRate.toFixed(0)}%</p>
            </div>
            <div>
              <p className="text-xs text-muted">Cancellation rate</p>
              <p className="font-semibold text-foreground">{driver.cancellationRate.toFixed(0)}%</p>
            </div>
            <div>
              <p className="text-xs text-muted">Complaints</p>
              <p className="font-semibold text-foreground">{driver.complaintCount}</p>
            </div>
            <div>
              <p className="text-xs text-muted">Online status</p>
              <p className="font-semibold text-foreground">{driver.onlineStatus}</p>
            </div>
            <div>
              <p className="text-xs text-muted">Joined</p>
              <p className="font-semibold text-foreground">{new Date(driver.createdAt).toLocaleDateString()}</p>
            </div>
          </div>

          <div className="mt-5 flex flex-wrap gap-3">
            {canApprove && (
              <Button
                size="sm"
                isLoading={busyAction === "approve"}
                onClick={() => run("approve", () => adminService.approveDriver(id), "Driver approved")}
              >
                <ShieldCheck size={14} /> Approve driver
              </Button>
            )}
            {isBlocked ? (
              <Button
                variant="outline"
                size="sm"
                isLoading={busyAction === "unblock"}
                onClick={() => setPendingAction({ kind: "unblock" })}
              >
                Unblock account
              </Button>
            ) : (
              <Button
                variant="danger"
                size="sm"
                isLoading={busyAction === "block"}
                onClick={() => setPendingAction({ kind: "block" })}
              >
                <Ban size={14} /> Block account
              </Button>
            )}
          </div>
        </CardBody>
      </Card>

      {/* Feedback */}
      <div>
        <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-dark">
          <MessageSquare size={16} /> Recent feedback
        </h2>
        {recentRatings.length === 0 ? (
          <p className="rounded-2xl border border-dashed border-slate-300 dark:border-slate-600 bg-slate-50/60 dark:bg-white/5 p-5 text-center text-sm text-muted">
            No ratings yet.
          </p>
        ) : (
          <div className="space-y-2">
            {recentRatings.map((rating) => (
              <div key={rating.rideId} className="rounded-2xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-4 shadow-sm">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-0.5">
                    {[1, 2, 3, 4, 5].map((star) => (
                      <Star
                        key={star}
                        size={14}
                        className={star <= rating.score ? "fill-primary text-primary" : "fill-transparent text-slate-300"}
                      />
                    ))}
                  </div>
                  <p className="text-xs text-muted">{new Date(rating.createdAt).toLocaleDateString()}</p>
                </div>
                {rating.comment && <p className="mt-2 text-sm text-foreground">"{rating.comment}"</p>}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Vehicles */}
      <div>
        <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-dark">
          <Car size={16} /> Vehicles
        </h2>
        {vehicles.length === 0 ? (
          <p className="rounded-2xl border border-dashed border-slate-300 dark:border-slate-600 bg-slate-50/60 dark:bg-white/5 p-5 text-center text-sm text-muted">
            No vehicles registered yet.
          </p>
        ) : (
          <div className="space-y-2">
            {vehicles.map((vehicle) => (
              <div key={vehicle.id} className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-4 shadow-sm">
                <div>
                  <p className="text-sm font-semibold text-foreground">
                    {vehicle.vehicleModel} · {vehicle.vehicleType} {vehicle.active && "· Active"}
                  </p>
                  <p className="text-xs text-muted">
                    {vehicle.vehicleNumber} · {vehicle.vehicleColor} · {vehicle.seatCapacity} seats
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <VerificationBadge status={vehicle.verificationStatus} />
                  {vehicle.verificationStatus === "PENDING" && (
                    <>
                      <Button
                        variant="outline"
                        size="sm"
                        isLoading={busyAction === `vehicle-approve-${vehicle.id}`}
                        onClick={() =>
                          run(`vehicle-approve-${vehicle.id}`, () => adminService.approveVehicle(vehicle.id), "Vehicle verified")
                        }
                      >
                        <CheckCircle2 size={14} /> Approve
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setPendingAction({ kind: "rejectVehicle", id: vehicle.id })}
                      >
                        <XCircle size={14} /> Reject
                      </Button>
                    </>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Documents */}
      <div>
        <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-dark">
          <FileText size={16} /> Documents
        </h2>
        {documents.length === 0 ? (
          <p className="rounded-2xl border border-dashed border-slate-300 dark:border-slate-600 bg-slate-50/60 dark:bg-white/5 p-5 text-center text-sm text-muted">
            No documents submitted yet.
          </p>
        ) : (
          <div className="space-y-2">
            {documents.map((doc) => (
              <div key={doc.id} className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-slate-200 dark:border-card-border bg-white dark:bg-card p-4 shadow-sm">
                <div>
                  <p className="text-sm font-semibold text-foreground">{doc.documentType.replaceAll("_", " ")}</p>
                  <a href={doc.documentUrl} target="_blank" rel="noreferrer" className="text-xs text-dark underline">
                    View document
                  </a>
                </div>
                <div className="flex items-center gap-2">
                  <VerificationBadge status={doc.verificationStatus} />
                  {doc.verificationStatus === "PENDING" && (
                    <>
                      <Button
                        variant="outline"
                        size="sm"
                        isLoading={busyAction === `doc-approve-${doc.id}`}
                        onClick={() => run(`doc-approve-${doc.id}`, () => adminService.approveDocument(doc.id), "Document verified")}
                      >
                        <CheckCircle2 size={14} /> Approve
                      </Button>
                      <Button variant="outline" size="sm" onClick={() => setPendingAction({ kind: "rejectDocument", id: doc.id })}>
                        <XCircle size={14} /> Reject
                      </Button>
                    </>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <ConfirmDialog
        open={pendingAction?.kind === "unblock"}
        title="Unblock this driver?"
        description="They'll be able to log in and go online again immediately."
        confirmLabel="Unblock"
        cancelLabel="Cancel"
        isLoading={busyAction === "unblock"}
        onConfirm={() => run("unblock", () => adminService.unblockDriver(id), "Driver unblocked")}
        onCancel={() => setPendingAction(null)}
      />

      <ReasonConfirmDialog
        open={pendingAction?.kind === "block"}
        title="Block this driver's account?"
        description="They won't be able to log in, and they'll be forced offline immediately."
        confirmLabel="Block account"
        isLoading={busyAction === "block"}
        onConfirm={(reason) => run("block", () => adminService.blockDriver(id, { reason: reason || undefined }), "Driver blocked")}
        onCancel={() => setPendingAction(null)}
      />

      <ReasonConfirmDialog
        open={pendingAction?.kind === "rejectVehicle"}
        title="Reject this vehicle?"
        description="The driver will need to fix and resubmit it before it can become active."
        confirmLabel="Reject vehicle"
        isLoading={pendingAction?.kind === "rejectVehicle" && busyAction === `vehicle-reject-${pendingAction.id}`}
        onConfirm={(reason) => {
          if (pendingAction?.kind !== "rejectVehicle") return;
          const vehicleId = pendingAction.id;
          void run(
            `vehicle-reject-${vehicleId}`,
            () => adminService.rejectVehicle(vehicleId, { reason: reason || undefined }),
            "Vehicle rejected",
          );
        }}
        onCancel={() => setPendingAction(null)}
      />

      <ReasonConfirmDialog
        open={pendingAction?.kind === "rejectDocument"}
        title="Reject this document?"
        description="The driver will need to submit a new one before it can be verified."
        confirmLabel="Reject document"
        isLoading={pendingAction?.kind === "rejectDocument" && busyAction === `doc-reject-${pendingAction.id}`}
        onConfirm={(reason) => {
          if (pendingAction?.kind !== "rejectDocument") return;
          const documentId = pendingAction.id;
          void run(
            `doc-reject-${documentId}`,
            () => adminService.rejectDocument(documentId, { reason: reason || undefined }),
            "Document rejected",
          );
        }}
        onCancel={() => setPendingAction(null)}
      />
    </div>
  );
}
