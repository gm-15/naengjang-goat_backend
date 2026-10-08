import { useState } from "react";
import { Pressable, Text, View } from "react-native";
import { router } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { useOnboard } from "../hooks/useOnboard";
import { CATEGORY_OPTIONS, type RecipeCategory } from "../types/onboard";
import { C } from "../components/theme";
import { ErrorBox, GradientButton, IconTile, LinkButton, Screen, SuccessBox, s } from "../components/ui";

export default function OnboardScreen() {
  const onboardMutation = useOnboard();
  const [selected, setSelected] = useState<Set<RecipeCategory>>(new Set());
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [result, setResult] = useState<string | null>(null);

  const toggle = (v: RecipeCategory) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(v)) next.delete(v);
      else next.add(v);
      return next;
    });

  const submit = async () => {
    setErrorMsg(null);
    if (selected.size === 0) return setErrorMsg("하나 이상의 매장 카테고리를 선택해주세요.");
    try {
      const r = await onboardMutation.mutateAsync({ categories: Array.from(selected) });
      setResult(
        `✅ 초기 설정 완료 — 메뉴 ${r.createdMenus}개 · 재료 구성 ${r.createdBom}개 등록` +
          (r.newIngredients.length > 0 ? `\n새로 추가된 재료 ${r.newIngredients.length}개` : ""),
      );
      setTimeout(() => router.replace("/store-setup"), 1500);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "";
      setErrorMsg(msg.includes("401") ? "로그인이 만료되었습니다. 다시 로그인해주세요." : "초기 설정에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }
  };

  return (
    <Screen contentStyle={{ paddingTop: 48 }}>
      <View style={{ alignItems: "center", marginBottom: 28 }}>
        <IconTile name="storefront-outline" size={64} />
        <Text style={[s.h1, { marginTop: 16 }]}>매장 카테고리 선택</Text>
        <Text style={[s.sub, { textAlign: "center" }]}>
          여러 개 선택 가능해요. 선택한 카테고리의 추천 메뉴와 재료가 자동으로 등록됩니다.
        </Text>
      </View>

      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 12 }}>
        {CATEGORY_OPTIONS.map((o) => {
          const on = selected.has(o.value);
          return (
            <Pressable
              key={o.value}
              onPress={() => toggle(o.value)}
              style={[
                { width: "47.5%", padding: 16, borderRadius: 16, borderWidth: 2, backgroundColor: "#fff", borderColor: C.border },
                on && { borderColor: C.primary, backgroundColor: C.primarySoft },
              ]}
            >
              <Text style={{ fontSize: 30, marginBottom: 6 }}>{o.emoji}</Text>
              <Text style={{ fontWeight: "700", fontSize: 16, color: on ? C.primaryDark : C.text }}>{o.label}</Text>
              <Text style={{ fontSize: 12, color: C.textSub, marginTop: 2 }}>{o.description}</Text>
              {on && (
                <View style={{ position: "absolute", top: 10, right: 10 }}>
                  <Ionicons name="checkmark-circle" size={22} color={C.primary} />
                </View>
              )}
            </Pressable>
          );
        })}
      </View>

      <View style={{ gap: 12, marginTop: 24 }}>
        <ErrorBox message={errorMsg} />
        <SuccessBox message={result} />
        <GradientButton
          title={result ? "이동 중..." : "시작하기"}
          onPress={submit}
          loading={onboardMutation.isPending}
          disabled={selected.size === 0 || result !== null}
        />
        {!result && <LinkButton title="건너뛰고 매장 설정으로" onPress={() => router.replace("/store-setup")} />}
      </View>
    </Screen>
  );
}
