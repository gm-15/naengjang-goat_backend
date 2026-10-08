import type { PushData } from "../types/notification";
import { authStorage } from "./auth-storage";
import { getApiBaseUrl } from "./config";
import { storage } from "./storage";

type Seen = { context: string; received: string[] };
type Pending = { server: string; data: PushData };

export function notificationPath(data: PushData): `/notification/${string}` | "/order" {
  const id = data.notificationId;
  if (id && /^[1-9]\d*$/.test(id) && Number.isSafeInteger(Number(id)) &&
      (data.route === "closing" || data.type === "UPLOAD" || data.type === "OPENING")) {
    return `/notification/${id}`;
  }
  return "/order";
}

export function notificationContext(): string {
  return `${getApiBaseUrl()}|${authStorage.getUsername() ?? "anonymous"}`;
}

export function shouldShowNotification(data: PushData): boolean {
  const id = data.notificationId;
  if (!id) return true;
  const context = notificationContext();
  let seen: Seen = { context, received: [] };
  try {
    const previous = JSON.parse(storage.get("handled_notification_ids") ?? "null") as Seen | null;
    if (previous?.context === context && Array.isArray(previous.received)) seen = previous;
  } catch { /* 이전 형식이나 손상된 기록은 새로 만든다. */ }
  if (seen.received.includes(id)) return false;
  seen.received = [...seen.received, id].slice(-100);
  storage.set("handled_notification_ids", JSON.stringify(seen));
  return true;
}

export function rememberPendingNotification(data: PushData): void {
  storage.set("pending_notification", JSON.stringify({ server: getApiBaseUrl(), data } satisfies Pending));
}

export function takePendingNotificationPath(): ReturnType<typeof notificationPath> | null {
  if (!authStorage.isAuthenticated()) return null;
  const raw = storage.get("pending_notification");
  storage.set("pending_notification", null);
  if (!raw) return null;
  try {
    const pending = JSON.parse(raw) as Pending;
    return pending.server === getApiBaseUrl() ? notificationPath(pending.data) : null;
  } catch { return null; }
}

export function clearNotificationState(): void {
  storage.set("handled_notification_ids", null);
  storage.set("pending_notification", null);
}
