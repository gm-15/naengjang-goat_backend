import { apiClient } from "./client";
import type {
  DailyReport,
  Menu,
  MenuPayload,
  PosMenuMapping,
  Recommendations,
  UploadResult,
} from "../types/workflow";
export function uploadPos(file: File): Promise<UploadResult> {
  const form = new FormData();
  form.append("file", file);
  return apiClient.postForm<UploadResult>("/pos/uploads", form);
}
export const getDailyReport = (date: string) =>
  apiClient.get<DailyReport>(
    `/reports/daily?businessDate=${encodeURIComponent(date)}`,
  );
export const listMenus = () => apiClient.get<Menu[]>("/menus");
export const createMenu = (payload: MenuPayload) =>
  apiClient.post<Menu>("/menus", payload);
export const updateMenu = (id: number, payload: MenuPayload) =>
  apiClient.put<Menu>(`/menus/${id}`, payload);
export const listPosMappings = () =>
  apiClient.get<PosMenuMapping[]>("/pos/menu-mappings");
export const savePosMapping = (code: string, menuId: number) =>
  apiClient.put<PosMenuMapping>(
    `/pos/menu-mappings/${encodeURIComponent(code)}`,
    { menuId },
  );
export const getRecommendations = (date: string) =>
  apiClient.get<Recommendations>(
    `/closing/recommendations?businessDate=${encodeURIComponent(date)}`,
  );
export const getClosingNotification = (id: number) =>
  apiClient.get<import("../types/workflow").ClosingNotification>(
    `/closing/notifications/${id}`,
  );
