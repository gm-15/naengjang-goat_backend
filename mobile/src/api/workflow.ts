import { Platform } from "react-native";
import type { DocumentPickerAsset } from "expo-document-picker";
import { apiClient } from "./client";
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

export function uploadPosSales(asset: DocumentPickerAsset) {
  const form = new FormData();
  if (Platform.OS === "web") {
    if (!asset.file) throw new Error("선택한 파일을 읽을 수 없습니다. 파일을 다시 선택해주세요.");
    form.append("file", asset.file, asset.name);
  } else {
    // React Native는 URI 기반 파일 객체를 multipart 파일로 전송합니다.
    form.append("file", {
      uri: asset.uri,
      name: asset.name,
      type: asset.mimeType ?? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    } as unknown as Blob);
  }
  return apiClient.upload<PosUploadResult>("/pos/uploads", form, { timeoutMs: 60_000 });
}

export function isBusinessDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
}
