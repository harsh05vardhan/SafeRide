import type { UserSummary } from "@/types/auth";

/**
 * localStorage-backed session storage. Plain localStorage (not an
 * httpOnly cookie) is a deliberate, documented tradeoff for this
 * portfolio build - it's readable by any script on the page (XSS risk),
 * but keeps the backend a pure stateless JWT API with no cookie/session
 * infrastructure. A production deployment would move the refresh token
 * into an httpOnly cookie set by the backend.
 */
const ACCESS_TOKEN_KEY = "saferide.accessToken";
const REFRESH_TOKEN_KEY = "saferide.refreshToken";
const USER_KEY = "saferide.user";

export const tokenStorage = {
  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  },
  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  },
  getUser(): UserSummary | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as UserSummary;
    } catch {
      return null;
    }
  },
  setSession(accessToken: string, refreshToken: string, user: UserSummary) {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  },
  setAccessToken(accessToken: string) {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  },
  /** Persists an in-place edit to the cached user (e.g. a new avatar) - doesn't touch either token. */
  setUser(user: UserSummary) {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  },
  clear() {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },
};
