import { storage } from "./storage";

/**
 * 백엔드 주소.
 * 우선순위: 앱에서 직접 입력한 값 > EXPO_PUBLIC_API_BASE_URL(.env) > 기본값
 *
 * 폰에서는 localhost 로 PC 에 접속할 수 없으므로
 * 같은 와이파이의 PC IP (예: http://192.168.0.12:8080) 를 넣는다.
 * Android 에뮬레이터라면 http://10.0.2.2:8080
 */
export const DEFAULT_API_BASE_URL =
  process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://192.168.0.10:8080";

export function getApiBaseUrl(): string {
  return storage.get("api_base_url") ?? DEFAULT_API_BASE_URL;
}

export function setApiBaseUrl(url: string): void {
  const trimmed = url.trim().replace(/\/+$/, "");
  storage.set("api_base_url", trimmed || null);
}

export function normalizeBaseUrl(input: string): string {
  let u = input.trim().replace(/\/+$/, "");
  if (u && !/^https?:\/\//i.test(u)) u = `http://${u}`;
  return u;
}
