import { useQuery } from "@tanstack/react-query";
import { ShieldCheck, ServerCog, Loader2, XCircle } from "lucide-react";
import { api, type ApiResponse } from "@/services/api";

interface PingData {
  service: string;
  status: string;
  timestamp: string;
}

/**
 * Temporary Phase 1 landing page. Its only job is to prove the full chain
 * works end to end: Vite + Tailwind render -> Axios -> Spring Boot ->
 * ApiResponse envelope -> back to the UI. Replaced by the real home page
 * ("Where are you going?") in Phase 7.
 */
export default function ScaffoldStatus() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["ping"],
    queryFn: async () => {
      const res = await api.get<ApiResponse<PingData>>("/ping");
      return res.data.data;
    },
  });

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4">
      <div className="w-full max-w-lg rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <div className="mb-6 flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <ShieldCheck size={24} />
          </div>
          <div>
            <h1 className="text-xl font-bold text-dark">SafeRide</h1>
            <p className="text-sm text-muted">Phase 1 - project scaffold</p>
          </div>
        </div>

        <div className="flex items-center gap-3 rounded-xl bg-slate-50 p-4">
          <ServerCog size={20} className="text-muted" />
          <div className="flex-1">
            <p className="text-sm font-medium text-foreground">Backend connection</p>
            {isLoading && (
              <p className="flex items-center gap-1 text-sm text-muted">
                <Loader2 size={14} className="animate-spin" /> Contacting saferide-backend...
              </p>
            )}
            {isError && (
              <p className="flex items-center gap-1 text-sm text-danger">
                <XCircle size={14} />
                {error instanceof Error ? error.message : "Could not reach backend"}
              </p>
            )}
            {data && (
              <p className="text-sm text-success">
                {data.service} is {data.status} - {new Date(data.timestamp).toLocaleTimeString()}
              </p>
            )}
          </div>
        </div>

        <p className="mt-6 text-center text-xs text-muted">
          Run <code className="rounded bg-slate-100 px-1 py-0.5">mvn spring-boot:run</code> in
          saferide-backend if the connection above fails.
        </p>
      </div>
    </div>
  );
}
