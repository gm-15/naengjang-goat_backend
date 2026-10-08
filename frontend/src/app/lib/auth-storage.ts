const ACCESS_KEY = "auth_access_token";
const REFRESH_KEY = "auth_refresh_token";
const USERNAME_KEY = "auth_username";

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

function safeWindow(): Storage | null {
  if (typeof window === "undefined") return null;
  return window.localStorage;
}

export const authStorage = {
  getAccessToken(): string | null {
    return safeWindow()?.getItem(ACCESS_KEY) ?? null;
  },

  getRefreshToken(): string | null {
    return safeWindow()?.getItem(REFRESH_KEY) ?? null;
  },

  getUsername(): string | null {
    return safeWindow()?.getItem(USERNAME_KEY) ?? null;
  },

  setTokens(tokens: AuthTokens): void {
    const ls = safeWindow();
    if (!ls) return;
    ls.setItem(ACCESS_KEY, tokens.accessToken);
    ls.setItem(REFRESH_KEY, tokens.refreshToken);
  },

  setUsername(username: string): void {
    safeWindow()?.setItem(USERNAME_KEY, username);
  },

  isAuthenticated(): boolean {
    return Boolean(this.getAccessToken());
  },

  clear(): void {
    const ls = safeWindow();
    if (!ls) return;
    ls.removeItem(ACCESS_KEY);
    ls.removeItem(REFRESH_KEY);
    ls.removeItem(USERNAME_KEY);
  },
};
