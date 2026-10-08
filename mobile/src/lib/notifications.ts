import { Platform } from "react-native";
import * as Device from "expo-device";
import * as Notifications from "expo-notifications";
import { router } from "expo-router";
import { updateFcmToken } from "../api/users";
import type { PushData } from "../types/notification";
import { authStorage } from "./auth-storage";
import { rememberFcmConnection, trackFcmRegistration } from "./fcm-connection";
import { notificationContext, notificationPath, rememberPendingNotification, shouldShowNotification } from "./notification-state";

function pushData(n: Notifications.Notification): PushData {
  return (n.request.content.data ?? {}) as PushData;
}

async function saveToken(token: string, accessToken: string | null): Promise<void> {
  await trackFcmRegistration(async () => {
    for (let attempt = 0; attempt < 2; attempt += 1) {
      if (authStorage.getAccessToken() !== accessToken) return;
      try {
        await updateFcmToken(token);
        if (authStorage.getAccessToken() === accessToken) rememberFcmConnection(token);
        return;
      }
      catch (error) {
        if (attempt === 1 || (error as { status?: number }).status !== 409) throw error;
      }
    }
  });
}

Notifications.setNotificationHandler({
  handleNotification: async (n) => {
    const show = shouldShowNotification(pushData(n));
    if (__DEV__) console.log("[FCM] received", n.request.content.title);
    return { shouldShowBanner: show, shouldShowList: show, shouldPlaySound: show, shouldSetBadge: false };
  },
});

/** Firebase Admin과 연결하는 Android의 FCM 기기 토큰. */
export async function registerFcmToken(): Promise<string | null> {
  if (!Device.isDevice || Platform.OS !== "android" || !authStorage.isAuthenticated()) return null;
  const accessToken = authStorage.getAccessToken();
  await Notifications.setNotificationChannelAsync("default", {
    name: "매장 운영 알림", importance: Notifications.AndroidImportance.HIGH,
  });
  let permission = await Notifications.getPermissionsAsync();
  if (!permission.granted) permission = await Notifications.requestPermissionsAsync();
  if (!permission.granted) return null;
  const { data } = await Notifications.getDevicePushTokenAsync();
  // 권한 팝업이 떠 있는 동안 계정이 바뀌면 이전 작업을 새 계정에 적용하지 않는다.
  if (authStorage.getAccessToken() !== accessToken) return null;
  await saveToken(String(data), accessToken);
  if (__DEV__) console.log("[FCM] token", data);
  return String(data);
}

// 마지막 응답과 수신 리스너가 동시에 전달하는 이벤트만 중복 방어한다.
// 처리 ID를 영구 저장하지 않아 같은 알림을 나중에 다시 확인할 수 있다.
let lastNavigation = { key: "", at: 0 };
function openFromNotification(data: PushData): void {
  const key = `${notificationContext()}|${notificationPath(data)}`;
  const now = Date.now();
  if (lastNavigation.key === key && now - lastNavigation.at < 1000) return;
  if (!authStorage.isAuthenticated()) {
    rememberPendingNotification(data);
    router.replace("/login");
  } else {
    router.push(notificationPath(data));
  }
  lastNavigation = { key, at: now };
}

export function setupNotificationListeners(): () => void {
  const tokenSub = Notifications.addPushTokenListener(({ data }) => {
    if (Platform.OS === "android" && authStorage.isAuthenticated()) {
      saveToken(String(data), authStorage.getAccessToken()).catch(() => console.warn("알림 기기 등록을 재시도해야 합니다."));
    }
  });
  const responseSub = Notifications.addNotificationResponseReceivedListener((response) => {
    openFromNotification(pushData(response.notification));
    Notifications.clearLastNotificationResponseAsync().catch(() => {});
  });
  Notifications.getLastNotificationResponseAsync().then(async (response) => {
    if (response) {
      openFromNotification(pushData(response.notification));
      await Notifications.clearLastNotificationResponseAsync();
    }
  }).catch(() => console.warn("알림 화면 이동 정보를 불러오지 못했습니다."));
  return () => { tokenSub.remove(); responseSub.remove(); };
}
