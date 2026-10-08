import { storage } from "./storage";

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

export const authStorage = {
  getAccessToken: () => storage.get("auth_access_token"),
  getRefreshToken: () => storage.get("auth_refresh_token"),
  getUsername: () => storage.get("auth_username"),
  setTokens(tokens: AuthTokens) {
    storage.set("auth_access_token", tokens.accessToken);
    storage.set("auth_refresh_token", tokens.refreshToken);
  },
  setUsername: (username: string) => storage.set("auth_username", username),
  isAuthenticated: () => Boolean(storage.get("auth_access_token")),
  clear() {
    storage.set("auth_access_token", null);
    storage.set("auth_refresh_token", null);
    storage.set("auth_username", null);
  },
};
