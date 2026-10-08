import { apiClient } from "./client";
import type {
  StoreSettings,
  UpdateStoreSettingsPayload,
} from "../types/settings";

/**
 * GET /settings — 현재 매장 운영 설정 조회 (인증 필요).
 * 미설정 시 백엔드가 기본값 또는 404 응답할 수 있음.
 */
export async function getStoreSettings(): Promise<StoreSettings | null> {
  try {
    const data = await apiClient.get<StoreSettings>("/settings");
    // configured=false 면 미설정 — null 반환
    if (!data.configured) return null;
    return data;
  } catch (err) {
    if (err instanceof Error && err.message.includes("404")) return null;
    throw err;
  }
}

/**
 * PUT /settings — 매장 운영 설정 저장 (인증 필요).
 */
export async function updateStoreSettings(
  payload: UpdateStoreSettingsPayload,
): Promise<StoreSettings> {
  return apiClient.put<StoreSettings>("/settings", payload);
}
