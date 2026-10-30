import { Platform } from "react-native";
import type { DocumentPickerAsset } from "expo-document-picker";
import * as FileSystemLegacy from "expo-file-system/legacy";
import { apiClient, ApiError } from "./client";
import { authStorage } from "../lib/auth-storage";
import { getApiBaseUrl } from "../lib/config";
import type { Recommendations } from "../types/notification";
import type { DailyReport, MenuRecipePayload, MenuSummary, PosMenuMapping, PosUploadResult } from "../types/workflow";

export function getDailyReport(businessDate: string) {
  return apiClient.get<DailyReport>(`/reports/daily?businessDate=${encodeURIComponent(businessDate)}`);
}

export function getClosingRecommendations(businessDate: string) {
  return apiClient.get<Recommendations>(`/closing/recommendations?businessDate=${encodeURIComponent(businessDate)}`);
}

export function getMenus() {
  return apiClient.get<MenuSummary[]>("/menus");
}

export function saveMenuRecipe(menuId: number | null, payload: MenuRecipePayload) {
  return menuId === null
    ? apiClient.post<MenuSummary>("/menus", payload)
    : apiClient.put<MenuSummary>(`/menus/${menuId}`, payload);
}

export function getPosMenuMappings() {
  return apiClient.get<PosMenuMapping[]>("/pos/menu-mappings");
}

export function savePosMenuMapping(posCode: string, menuId: number) {
  return apiClient.put<PosMenuMapping>(`/pos/menu-mappings/${encodeURIComponent(posCode)}`, { menuId });
}

export async function uploadPosSales(asset: DocumentPickerAsset): Promise<PosUploadResult> {
  if (Platform.OS === "web") {
    const form = new FormData();
    if (!asset.file) throw new Error("선택한 파일을 읽을 수 없습니다. 파일을 다시 선택해주세요.");
    form.append("file", asset.file, asset.name);
    return apiClient.upload<PosUploadResult>("/pos/uploads", form, { timeoutMs: 60_000 });
  }
  // RN: 네이티브 멀티파트 업로드 (fetch+FormData 는 Android 캐시 URI 에서 자주 throw)
  const token = authStorage.getAccessToken();
  const mime = asset.mimeType ?? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  const result = await FileSystemLegacy.uploadAsync(`${getApiBaseUrl()}/pos/uploads`, asset.uri, {
    httpMethod: "POST",
    uploadType: FileSystemLegacy.FileSystemUploadType.MULTIPART,
    fieldName: "file",
    mimeType: mime,
    headers: {
      Accept: "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });
  if (result.status >= 400) {
    let message = result.body || `업로드 실패 (${result.status})`;
    try {
      const detail = JSON.parse(result.body) as { message?: string; detail?: string };
      message = detail.message ?? detail.detail ?? message;
    } catch { /* 서버 일반 텍스트 */ }
    throw new ApiError(result.status, message);
  }
  return JSON.parse(result.body) as PosUploadResult;
}

export function isBusinessDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
}
