import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { authService } from "@/services/authService";
import { SESSION_EXPIRED_EVENT } from "@/services/api";
import { tokenStorage } from "@/lib/tokenStorage";
import type { LoginRequest, RegisterRequest, UserSummary } from "@/types/auth";

interface AuthContextValue {
  user: UserSummary | null;
  isAuthenticated: boolean;
  isCustomer: boolean;
  isDriver: boolean;
  login: (request: LoginRequest) => Promise<void>;
  register: (request: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(() => tokenStorage.getUser());
  const navigate = useNavigate();

  const login = useCallback(async (request: LoginRequest) => {
    const auth = await authService.login(request);
    setUser(auth.user);
  }, []);

  const register = useCallback(async (request: RegisterRequest) => {
    const auth = await authService.register(request);
    setUser(auth.user);
  }, []);

  const logout = useCallback(async () => {
    await authService.logout();
    setUser(null);
    navigate("/login", { replace: true });
  }, [navigate]);

  useEffect(() => {
    function handleSessionExpired() {
      tokenStorage.clear();
      setUser(null);
      navigate("/login", { replace: true });
    }
    window.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
  }, [navigate]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      isCustomer: user?.roles.includes("CUSTOMER") ?? false,
      isDriver: user?.roles.includes("DRIVER") ?? false,
      login,
      register,
      logout,
    }),
    [user, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components -- the useX hook belongs next to its Provider, not split into a second file
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
