import { apiClient } from "./client";
import type { ClosingNotification } from "../types/notification";

/** GET /closing/notifications/{id} — 알림 상세 + 발송 당시 판단 스냅샷 (본인 알림만, 아니면 404) */
export async function getClosingNotification(id: number): Promise<ClosingNotification> {
  return apiClient.get<ClosingNotification>(`/closing/notifications/${id}`);
}
