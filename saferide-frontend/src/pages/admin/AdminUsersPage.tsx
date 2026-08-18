import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Trash2, Users, UserPlus } from "lucide-react";
import { adminUserService } from "@/services/adminUserService";
import { Badge } from "@/components/common/Badge";
import { Button } from "@/components/common/Button";
import { Skeleton } from "@/components/common/Skeleton";
import { EmptyState, ErrorState } from "@/components/common/StateViews";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { AccountStatusBadge } from "@/components/admin/StatusBadges";
import { CreateAdminDialog } from "@/components/admin/CreateAdminDialog";
import { Avatar } from "@/components/common/Avatar";
import { useToast } from "@/contexts/ToastContext";
import { extractErrorMessage } from "@/services/api";
import { cn } from "@/lib/utils";
import type { RoleName } from "@/types/auth";
import type { AdminUserSummary } from "@/types/admin";

const TABS: { label: string; value: RoleName | "ALL" }[] = [
  { label: "All", value: "ALL" },
  { label: "Customers", value: "CUSTOMER" },
  { label: "Drivers", value: "DRIVER" },
  { label: "Admins", value: "ADMIN" },
];

/**
 * Platform-wide account management - every user regardless of role, distinct from
 * AdminDriversPage's driver-specific verification queue. Deletion is a soft delete (see the
 * backend's UserStatus.DELETED doc) - the row and everything it's ever been part of (rides,
 * payments, ratings, complaints) stays intact, the account just can never log in again.
 * Admin accounts can't be deleted from here at all (matches a hard backend guard - see
 * AdminUserService), so no delete action is even rendered for them.
 */
export default function AdminUsersPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = (searchParams.get("role") as RoleName | "ALL" | null) ?? "ALL";
  const [page, setPage] = useState(0);
  const [target, setTarget] = useState<AdminUserSummary | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [showCreateAdmin, setShowCreateAdmin] = useState(false);
  const { showToast } = useToast();
  const queryClient = useQueryClient();

  const role = activeTab === "ALL" ? undefined : activeTab;
  const { data, isPending, isError, refetch } = useQuery({
    queryKey: ["admin-users", role, page],
    queryFn: () => adminUserService.list(role, page, 15),
  });

  function selectTab(value: RoleName | "ALL") {
    setPage(0);
    if (value === "ALL") {
      setSearchParams({});
    } else {
      setSearchParams({ role: value });
    }
  }

  async function confirmDelete() {
    if (!target) return;
    setIsDeleting(true);
    try {
      await adminUserService.delete(target.user.id);
      showToast(`${target.user.name}'s account has been deleted`, "success");
      await queryClient.invalidateQueries({ queryKey: ["admin-users"] });
      setTarget(null);
    } catch (error) {
      showToast(extractErrorMessage(error), "error");
    } finally {
      setIsDeleting(false);
    }
  }

  async function onAdminCreated(name: string) {
    setShowCreateAdmin(false);
    showToast(`${name} can now log in as an admin`, "success");
    await queryClient.invalidateQueries({ queryKey: ["admin-users"] });
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 sm:px-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold text-dark">Users</h1>
          <p className="mt-1 text-sm text-muted">Every SafeRide account - customers, drivers and admins.</p>
        </div>
        <Button size="sm" onClick={() => setShowCreateAdmin(true)}>
          <UserPlus size={14} /> Add admin
        </Button>
      </div>

      <div className="mt-5 flex gap-1 border-b border-slate-200">
        {TABS.map((tab) => (
          <button
            key={tab.value}
            onClick={() => selectTab(tab.value)}
            className={cn(
              "border-b-2 px-4 py-2.5 text-sm font-medium transition-colors",
              activeTab === tab.value ? "border-dark text-dark" : "border-transparent text-muted hover:text-foreground",
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="mt-4 overflow-x-auto rounded-2xl border border-slate-200 bg-white shadow-sm">
        {isPending ? (
          <div className="space-y-2 p-4">
            {[...Array(5)].map((_, i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : isError ? (
          <div className="p-4">
            <ErrorState description="Couldn't load users." onAction={() => refetch()} />
          </div>
        ) : data!.content.length === 0 ? (
          <div className="p-4">
            <EmptyState icon={Users} title="No users found" description="No accounts match this filter." />
          </div>
        ) : (
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead>
              <tr className="border-b border-slate-100 text-xs font-semibold uppercase tracking-wide text-muted">
                <th className="px-4 py-3">User</th>
                <th className="px-4 py-3">Phone</th>
                <th className="px-4 py-3">Roles</th>
                <th className="px-4 py-3">Account</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {data!.content.map((row) => (
                <tr key={row.user.id} className="border-b border-slate-50 last:border-0 hover:bg-slate-50/60">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2.5">
                      <Avatar name={row.user.name} avatarUrl={row.user.avatarUrl} size={30} />
                      <div>
                        <p className="font-medium text-foreground">{row.user.name}</p>
                        <p className="text-xs text-muted">{row.user.email}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-foreground">{row.user.phone}</td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1">
                      {row.user.roles.map((r) => (
                        <Badge key={r} variant="neutral">
                          {r}
                        </Badge>
                      ))}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <AccountStatusBadge status={row.status} />
                  </td>
                  <td className="px-4 py-3 text-right">
                    {!row.user.roles.includes("ADMIN") && row.status !== "DELETED" && (
                      <Button variant="outline" size="sm" onClick={() => setTarget(row)}>
                        <Trash2 size={13} /> Delete
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {data && data.page.totalPages > 1 && (
        <div className="mt-5 flex items-center justify-center gap-3">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            <ChevronLeft size={14} /> Prev
          </Button>
          <span className="text-xs text-muted">
            Page {page + 1} of {data.page.totalPages}
          </span>
          <Button variant="outline" size="sm" disabled={page >= data.page.totalPages - 1} onClick={() => setPage((p) => p + 1)}>
            Next <ChevronRight size={14} />
          </Button>
        </div>
      )}

      <ConfirmDialog
        open={!!target}
        title="Delete this account?"
        description={
          target
            ? `${target.user.name} will never be able to log in again. Their ride history, payments and ratings are kept, not deleted - this only revokes access.`
            : ""
        }
        confirmLabel="Delete Account"
        cancelLabel="Keep Account"
        destructive
        isLoading={isDeleting}
        onConfirm={confirmDelete}
        onCancel={() => setTarget(null)}
      />

      <CreateAdminDialog open={showCreateAdmin} onClose={() => setShowCreateAdmin(false)} onCreated={onAdminCreated} />
    </div>
  );
}
