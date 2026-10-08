import { useState } from "react";
import { Alert, Pressable, Text, View } from "react-native";
import { router } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import Constants from "expo-constants";
import { authStorage } from "../../lib/auth-storage";
import { useLogout } from "../../hooks/useAuth";
import { getApiBaseUrl } from "../../lib/config";
import { StoreSettingsForm } from "../../components/StoreSettingsForm";
import { ServerUrlSheet } from "../../components/ServerUrlSheet";
import { C } from "../../components/theme";
import { Card, PageHeader, Screen, SectionTitle } from "../../components/ui";

export default function SettingsScreen() {
  const logoutMutation = useLogout();
  const [serverOpen, setServerOpen] = useState(false);
  const [, force] = useState(0);

  const logout = () =>
    Alert.alert("로그아웃", "로그아웃 하시겠습니까?", [
      { text: "취소", style: "cancel" },
      {
        text: "로그아웃",
        style: "destructive",
        onPress: async () => {
          try {
            await logoutMutation.mutateAsync();
            router.replace("/login");
          } catch (error) {
            Alert.alert("로그아웃 실패", error instanceof Error ? error.message : "서버 연결을 확인한 뒤 다시 시도해 주세요.");
          }
        },
      },
    ]);

  return (
    <Screen>
      <PageHeader title="매장 운영 설정" description="발주 알림과 재고 계산에 사용됩니다" />
      <StoreSettingsForm />

      <View style={{ height: 20 }} />
      <SectionTitle>앱 설정</SectionTitle>
      <Card style={{ padding: 0 }}>
        <Row icon="server-outline" label="서버 주소" value={getApiBaseUrl().replace(/^https?:\/\//, "")} onPress={() => setServerOpen(true)} />
        <View style={{ height: 1, backgroundColor: C.border, marginHorizontal: 16 }} />
        <Row icon="person-circle-outline" label="계정" value={authStorage.getUsername() ?? "-"} />
        <View style={{ height: 1, backgroundColor: C.border, marginHorizontal: 16 }} />
        <Row icon="log-out-outline" label="로그아웃" danger onPress={logout} />
      </Card>
      <Text style={{ textAlign: "center", color: C.textMute, fontSize: 12, marginTop: 16 }}>
        냉장 G.O.A.T v{Constants.expoConfig?.version ?? "1.0.0"}
      </Text>

      <ServerUrlSheet
        visible={serverOpen}
        onClose={() => {
          setServerOpen(false);
          force((n) => n + 1);
        }}
      />
    </Screen>
  );
}

function Row({
  icon,
  label,
  value,
  onPress,
  danger,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  value?: string;
  onPress?: () => void;
  danger?: boolean;
}) {
  return (
    <Pressable onPress={onPress} disabled={!onPress} style={{ flexDirection: "row", alignItems: "center", gap: 12, padding: 16 }}>
      <Ionicons name={icon} size={20} color={danger ? C.danger : C.textSub} />
      <Text style={{ flex: 1, fontSize: 15, color: danger ? C.danger : C.text }}>{label}</Text>
      {value ? (
        <Text style={{ color: C.textSub, fontSize: 13, maxWidth: "55%" }} numberOfLines={1}>
          {value}
        </Text>
      ) : null}
      {onPress && !danger ? <Ionicons name="chevron-forward" size={18} color={C.textMute} /> : null}
    </Pressable>
  );
}
