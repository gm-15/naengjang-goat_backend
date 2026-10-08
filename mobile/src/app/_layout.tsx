import { useEffect, useState } from "react";
import { View } from "react-native";
import { Stack, useRootNavigationState } from "expo-router";
import { StatusBar } from "expo-status-bar";
import * as SplashScreen from "expo-splash-screen";
import { QueryClientProvider } from "@tanstack/react-query";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { queryClient } from "../lib/query-client";
import { storage } from "../lib/storage";
import { registerFcmToken, setupNotificationListeners } from "../lib/notifications";
import { C } from "../components/theme";

SplashScreen.preventAutoHideAsync().catch(() => {});

export default function RootLayout() {
  const [ready, setReady] = useState(false);
  const navigation = useRootNavigationState();

  useEffect(() => {
    storage
      .hydrate()
      .catch(() => {})
      .finally(() => {
        setReady(true);
        SplashScreen.hideAsync().catch(() => {});
      });
  }, []);

  // 저장소 복원 후: 로그인 상태면 FCM 토큰 재등록 + 알림 리스너 등록
  useEffect(() => {
    if (!ready || !navigation?.key) return;
    registerFcmToken().catch(() => {});
    return setupNotificationListeners();
  }, [ready, navigation?.key]);

  if (!ready) return <View style={{ flex: 1, backgroundColor: C.bg }} />;

  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <StatusBar style="dark" />
        <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: C.bg } }} />
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}
