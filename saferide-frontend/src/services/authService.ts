import { api, type ApiResponse } from "./api";
import { tokenStorage } from "@/lib/tokenStorage";
import type { AuthResponse, LoginRequest, RegisterRequest } from "@/types/auth";

export const authService = {
  async register(request: RegisterRequest): Promise<AuthResponse> {
    const response = await api.post<ApiResponse<AuthResponse>>("/auth/register", request);
    tokenStorage.setSession(response.data.data.accessToken, response.data.data.refreshToken, response.data.data.user);
    return response.data.data;
  },

  async login(request: LoginRequest): Promise<AuthResponse> {
    const response = await api.post<ApiResponse<AuthResponse>>("/auth/login", request);
    tokenStorage.setSession(response.data.data.accessToken, response.data.data.refreshToken, response.data.data.user);
    return response.data.data;
  },

  async logout(): Promise<void> {
    const refreshToken = tokenStorage.getRefreshToken();
    tokenStorage.clear();
    if (refreshToken) {
      // Best-effort - the local session is already cleared either way.
      await api.post("/auth/logout", { refreshToken }).catch(() => undefined);
    }
  },
};
