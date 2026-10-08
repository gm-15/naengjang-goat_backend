import { Platform } from "react-native";
import * as SecureStore from "expo-secure-store";

/**
 * 키-값 저장소. 네이티브는 SecureStore(암호화), 웹 미리보기는 localStorage.
 * 앱 시작 시 hydrate() 로 메모리에 올려 두고 이후에는 동기로 읽는다.
 */
const KEYS = [
  "auth_access_token",
  "auth_refresh_token",
  "auth_username",
  "api_base_url",
  "handled_notification_ids",
  "pending_notification",
  "registered_fcm_connection",
] as const;
export type StorageKey = (typeof KEYS)[number];

const cache = new Map<StorageKey, string>();

async function rawGet(key: StorageKey): Promise<string | null> {
  if (Platform.OS === "web") {
    try {
      return globalThis.localStorage?.getItem(key) ?? null;
    } catch {
      return null;
    }
  }
  return SecureStore.getItemAsync(key);
}

async function rawSet(key: StorageKey, value: string | null): Promise<void> {
  if (Platform.OS === "web") {
    try {
      if (value === null) globalThis.localStorage?.removeItem(key);
      else globalThis.localStorage?.setItem(key, value);
    } catch {
      /* ignore */
    }
    return;
  }
  if (value === null) await SecureStore.deleteItemAsync(key);
  else await SecureStore.setItemAsync(key, value);
}

export const storage = {
  async hydrate(): Promise<void> {
    await Promise.all(
      KEYS.map(async (k) => {
        const v = await rawGet(k);
        if (v !== null) cache.set(k, v);
      }),
    );
  },
  get(key: StorageKey): string | null {
    return cache.get(key) ?? null;
  },
  set(key: StorageKey, value: string | null): void {
    if (value === null) cache.delete(key);
    else cache.set(key, value);
    void rawSet(key, value);
  },
};
