import { updateFcmToken } from "../api/users";
import { authStorage } from "./auth-storage";
import { getApiBaseUrl } from "./config";
import { storage } from "./storage";

type Connection = { server: string; username: string | null; token: string };

const registrations = new Set<Promise<unknown>>();
let registrationQueue: Promise<unknown> = Promise.resolve();
let disconnection: Promise<void> | null = null;

/** 로그아웃·서버 변경이 진행 중인 등록의 완료를 기다릴 수 있도록 추적한다. */
export async function trackFcmRegistration<T>(operation: () => Promise<T>): Promise<T | undefined> {
  if (disconnection) return undefined;
  // 서버 반영 순서와 로컬에 기억하는 토큰 순서를 같게 유지한다.
  // 이전 등록 실패가 이후 토큰 등록을 막지 않도록 한다.
  const pending = registrationQueue.catch(() => undefined).then(operation);
  registrationQueue = pending;
  registrations.add(pending);
  try { return await pending; }
  finally { registrations.delete(pending); }
}

export function rememberFcmConnection(token: string): void {
  storage.set("registered_fcm_connection", JSON.stringify({ server: getApiBaseUrl(), username: authStorage.getUsername(), token } satisfies Connection));
}

/** 현재 앱이 등록한 기기만 해제한다. 웹 미리보기나 다른 기기 연결은 건드리지 않는다. */
export function unregisterFcmConnection(): Promise<void> {
  if (disconnection) return disconnection;
  disconnection = (async () => {
    try {
      // 등록 응답보다 먼저 연결을 해제하면 늦게 완료된 등록이 계정에 남을 수 있다.
      // 해제 중 새 토큰 이벤트는 trackFcmRegistration에서 건너뛴다.
      await Promise.allSettled([...registrations]);
      const raw = storage.get("registered_fcm_connection");
      if (!raw) return;
      let connection: Connection;
      try { connection = JSON.parse(raw) as Connection; }
      catch { storage.set("registered_fcm_connection", null); return; }
      if (authStorage.isAuthenticated() && connection.server === getApiBaseUrl() && connection.username === authStorage.getUsername()) {
        await updateFcmToken(null, connection.token);
      }
      storage.set("registered_fcm_connection", null);
    } finally { disconnection = null; }
  })();
  return disconnection;
}
