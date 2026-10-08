import { useMemo, useState } from "react";
import { Text, View } from "react-native";
import { router } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { useLowestTop } from "../../../hooks/useLowestTop";
import type { LowestTopItem } from "../../../types/ingredient";
import { PriceTrendBadge } from "../../../components/PriceTrendBadge";
import { C } from "../../../components/theme";
import { Card, EmptyState, Input, PageHeader, Screen, Skeleton } from "../../../components/ui";

export default function LowestPriceList() {
  const { data: items = [], isLoading, isError, refetch, isRefetching } = useLowestTop(20);
  const [q, setQ] = useState("");

  const filtered = useMemo(() => {
    const t = q.trim().toLowerCase();
    return t ? items.filter((i) => i.name.toLowerCase().includes(t)) : items;
  }, [items, q]);

  return (
    <Screen refreshing={isRefetching} onRefresh={refetch}>
      <PageHeader title="최저가 재료" description="KAMIS 시세 + 온라인 최저가 비교" />
      <View style={{ marginBottom: 16 }}>
        <Input value={q} onChangeText={setQ} placeholder="재료 검색..." returnKeyType="search" clearButtonMode="while-editing" />
      </View>

      {isLoading ? (
        <Skeleton count={5} height={110} />
      ) : isError ? (
        <EmptyState icon="cloud-offline-outline" title="재료 목록을 불러오지 못했습니다" description="서버 주소와 네트워크를 확인해 주세요." actionLabel="다시 시도" onAction={() => refetch()} />
      ) : filtered.length === 0 ? (
        <EmptyState
          title={q ? "검색 결과가 없습니다" : "등록된 재료가 아직 없습니다"}
          description={q ? "다른 검색어를 시도해 보세요." : "온보딩에서 매장 카테고리를 선택하면 재료가 자동 등록됩니다."}
        />
      ) : (
        <View style={{ gap: 12 }}>
          {filtered.map((item) => (
            <PriceCard key={item.ingredientId} item={item} onPress={() => router.push(`/lowest-price/${item.ingredientId}`)} />
          ))}
        </View>
      )}
    </Screen>
  );
}

function PriceCard({ item, onPress }: { item: LowestTopItem; onPress: () => void }) {
  const has = item.todayPrice !== null;
  return (
    <Card onPress={onPress}>
      <View style={{ flexDirection: "row", alignItems: "center" }}>
        <View style={{ flex: 1, gap: 6 }}>
          <Text style={{ fontSize: 17, fontWeight: "700", color: C.text }}>{item.name}</Text>
          {has ? (
            <>
              <Text style={{ color: C.textMute, fontSize: 12 }}>
                오늘{" "}
                <Text style={{ fontSize: 20, fontWeight: "800", color: C.primary }}>
                  {item.todayPrice!.toLocaleString("ko-KR")}원
                </Text>{" "}
                /kg
              </Text>
              {item.monthAvg !== null && (
                <View style={{ flexDirection: "row", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                  <Text style={{ fontSize: 12, color: C.textMute }}>월 평균 {item.monthAvg.toLocaleString("ko-KR")}원</Text>
                  <PriceTrendBadge current={item.todayPrice!} average={item.monthAvg} />
                </View>
              )}
            </>
          ) : (
            <Text style={{ fontSize: 13, color: C.textMute }}>— 가격 수집 대기 중 (KAMIS 일일 배치로 자동 갱신)</Text>
          )}
        </View>
        <Ionicons name="chevron-forward" size={20} color={C.textMute} />
      </View>
    </Card>
  );
}
