import { Text, View } from "react-native";
import { router } from "expo-router";
import { StoreSettingsForm } from "../components/StoreSettingsForm";
import { LinkButton, Screen, s } from "../components/ui";

/** 가입 직후 매장 운영 설정 (탭바 없는 풀스크린) */
export default function StoreSetupScreen() {
  return (
    <Screen contentStyle={{ paddingTop: 48 }}>
      <View style={{ alignItems: "center", marginBottom: 28 }}>
        <Text style={{ fontSize: 48 }}>🏪</Text>
        <Text style={[s.h1, { marginTop: 8 }]}>매장 운영 설정</Text>
        <Text style={[s.sub, { textAlign: "center" }]}>
          영업 시간과 발주 요일을 입력해주세요. 발주 알림과 재고 계산에 사용됩니다.
        </Text>
      </View>
      <StoreSettingsForm submitLabel="저장 후 시작하기" onSaved={() => router.replace("/main")} />
      <LinkButton title="나중에 설정하기" onPress={() => router.replace("/main")} />
    </Screen>
  );
}
