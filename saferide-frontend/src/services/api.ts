import axios from "axios";

/**
 * Shared Axios instance for every SafeRide API call. Base URL comes from
 * VITE_API_BASE_URL so local dev, Docker, and prod builds never hardcode a
 * host. Auth headers (Bearer token attach + refresh-on-401 interceptor)
 * are added in Phase 2 once /api/auth exists - kept out for now so this
 * file only does what Phase 1 needs.
 */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api",
  headers: {
    "Content-Type": "application/json",
  },
});

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface ApiErrorResponse {
  success: false;
  message: string;
  code: string;
  timestamp: string;
  fieldErrors?: Record<string, string>;
}
