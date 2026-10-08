import { useCallback, useEffect, useMemo, useState } from "react";
import { Pressable, Text, View } from "react-native";
import { router, useFocusEffect, useLocalSearchParams } from "expo-router";
import { useQueryClient } from "@tanstack/react-query";
import { useClosingRecommendations } from "../../hooks/useWorkflow";
import { usePriceTrend } from "../../hooks/usePriceTrend";
import type { RecommendationItem } from "../../types/notification";
import { formatQuantity, todayISO } from "../../lib/date";
import { PurchaseHistory } from "../../components/PurchaseHistory";
import { PriceChart, type ChartPoint } from "../../components/PriceChart";
import { C } from "../../components/theme";
import { Badge, Card, EmptyState, ErrorBox, GradientButton, InfoCard, PageHeader, Screen, SectionTitle, SelectField, Skeleton, Spinner } from "../../components/ui";

type TabKey = "low-stock" | "history";
type Filter = "all" | "attention" | "unknown";
const messageOf = (error: unknown) => error instanceof Error ? error.message : "잠시 후 다시 시도해주세요.";

export default function OrderScreen() {
  const qc = useQueryClient();
  const params = useLocalSearchParams<{ tab?: string }>();
  const [tab, setTab] = useState<TabKey>(params.tab === "history" ? "history" : "low-stock");
  const [filter, setFilter] = useState<Filter>("all");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [businessDate, setBusinessDate] = useState(todayISO);
  const query = useClosingRecommendations(businessDate);
  const recommendations = query.data;
  const items = useMemo(() => [...(recommendations?.items ?? [])].sort((a, b) => {
    const priority = (item: RecommendationItem) => (item.stockAlert ? 4 : 0) + (item.buySignal ? 2 : 0) + (item.recommendedQuantity == null ? 1 : 0);
    return priority(b) - priority(a) || a.ingredientName.localeCompare(b.ingredientName, "ko");
  }), [recommendations?.items]);

  useEffect(() => {
    if (params.tab === "history" || params.tab === "low-stock") setTab(params.tab);
  }, [params.tab]);
  useEffect(() => {
    if (items.length > 0 && !items.some((item) => item.ingredientId === selectedId)) {
      setSelectedId(items[0].ingredientId);
    }
  }, [items, selectedId]);
  useFocusEffect(useCallback(() => {
    const today = todayISO();
    if (today !== businessDate) setBusinessDate(today);
    else void query.refetch();
    void qc.invalidateQueries({ queryKey: ["purchase-orders"] });
  }, [businessDate, query.refetch, qc]));

  const attentionCount = items.filter((item) => item.stockAlert || item.buySignal).length;
  const unknownCount = items.filter((item) => item.recommendedQuantity == null).length;
  const filtered = items.filter((item) => filter === "all" || (filter === "attention" ? item.stockAlert || item.buySignal : item.recommendedQuantity == null));
  const refresh = () => {
    void query.refetch();
    void qc.invalidateQueries({ queryKey: ["purchase-orders"] });
    void qc.invalidateQueries({ queryKey: ["price-trend"] });
  };

  return (
    <Screen refreshing={query.isRefetching} onRefresh={refresh}>
      <PageHeader title="발주 관리" description="판매 이력과 현재 재고로 판단하고 배송 받은 뒤 입고하세요" />
      <View style={{ flexDirection: "row", gap: 8, marginBottom: 16 }}>
        <GradientButton title="현재 발주 판단" variant={tab === "low-stock" ? "primary" : "outline"} style={{ flex: 1 }} onPress={() => setTab("low-stock")} />
        <GradientButton title="배송·구매 기록" variant={tab === "history" ? "primary" : "outline"} style={{ flex: 1 }} onPress={() => setTab("history")} />
      </View>

      {tab === "history" ? <PurchaseHistory /> : (
        <View style={{ gap: 14 }}>
          {query.isLoading ? <Skeleton count={3} height={150} /> : query.error ? (
            <View style={{ gap: 10 }}>
              <ErrorBox message={messageOf(query.error)} />
              <GradientButton title="발주 판단 다시 조회" variant="outline" onPress={() => void query.refetch()} />
            </View>
          ) : recommendations ? (
            <>
              <Card style={{ gap: 10 }}>
                <Text style={{ fontSize: 16, fontWeight: "700", color: C.text }}>확인할 재료 {attentionCount}개 · 판단 대기 {unknownCount}개</Text>
                <Text style={{ fontSize: 12, color: C.textSub }}>판단 기준일 {recommendations.businessDate}</Text>
                <Text style={{ fontSize: 12, color: C.textSub }}>마지막 판매 영업일: {recommendations.lastUploadedBusinessDate ?? "없음"}</Text>
                <Text style={{ fontSize: 12, color: C.textMute }}>판매 반영: {recommendations.lastReflectedAt?.replace("T", " ").slice(0, 19) ?? "미반영"}</Text>
                <Text style={{ fontSize: 12, color: C.textMute }}>판단 갱신: {recommendations.generatedAt.replace("T", " ").slice(0, 19)}</Text>
                {!recommendations.settingsConfigured ? (
                  <View style={{ gap: 8 }}>
                    <Text style={{ color: "#92400E", lineHeight: 19 }}>영업 시작 시간과 발주일을 설정해야 매장 기준의 판단과 영업 3시간 전 알림을 받을 수 있습니다.</Text>
                    <GradientButton title="매장 운영 설정" variant="soft" small onPress={() => router.navigate("/settings")} />
                  </View>
                ) : null}
                {!recommendations.lastUploadedBusinessDate || unknownCount > 0 ? (
                  <View style={{ gap: 8 }}>
                    <Text style={{ color: "#92400E", lineHeight: 19 }}>최근 판매 자료나 재료 소비 이력이 부족한 항목은 부족 여부와 권장 수량을 계산할 수 없습니다. 재고가 충분하다는 의미는 아닙니다.</Text>
                    <GradientButton title="POS 판매 자료 반영" variant="soft" small onPress={() => router.navigate("/operations")} />
                  </View>
                ) : null}
              </Card>

              {items.length === 0 ? (
                <EmptyState title="등록한 재료가 없습니다" description="메뉴와 레시피를 먼저 등록하면 재료별 판단을 확인할 수 있습니다." actionLabel="메뉴 설정" onAction={() => router.push("/onboard")} />
              ) : (
                <>
                  <View style={{ flexDirection: "row", gap: 6 }}>
                    {([ ["all", "전체"], ["attention", "확인 필요"], ["unknown", "판단 대기"] ] as const).map(([value, label]) => (
                      <Pressable key={value} onPress={() => setFilter(value)} style={{ flex: 1, paddingVertical: 10, alignItems: "center", borderRadius: 10, borderWidth: 1, borderColor: filter === value ? C.primary : C.border, backgroundColor: filter === value ? C.primarySoft2 : "#fff" }}>
                        <Text style={{ color: filter === value ? C.primaryDark : C.textSub, fontWeight: "600" }}>{label}</Text>
                      </Pressable>
                    ))}
                  </View>
                  {filtered.length === 0 ? (
                    <EmptyState title={filter === "unknown" ? "판단 대기 항목이 없습니다" : "이 조건에 해당하는 재료가 없습니다"} description={unknownCount > 0 ? "판단 대기 항목은 별도로 확인해주세요." : "전체 목록에서 재고와 판단 근거를 확인할 수 있습니다."} />
                  ) : filtered.map((item) => <RecommendationCard key={item.ingredientId} item={item} onSelect={() => setSelectedId(item.ingredientId)} selected={selectedId === item.ingredientId} />)}

                  <Card style={{ gap: 10 }}>
                    <SectionTitle>최근 30일 가격 추세</SectionTitle>
                    <SelectField title="재료 선택" value={selectedId} options={items.map((item) => ({ value: item.ingredientId, label: item.ingredientName }))} onChange={setSelectedId} />
                    <TrendSection ingredientId={selectedId} />
                  </Card>
                  <InfoCard title="발주 판단 기준" lines={[
                    "권장 수량은 최근 판매 소비량과 사용 가능한 현재 재고를 기준으로 계산합니다.",
                    "가격 신호는 공공 가격 추세에 따른 판단입니다. 실제 판매 상품의 최저가와 가격은 상세 화면에서 확인하세요.",
                    "구매 기록을 저장해도 재고는 늘지 않습니다. 배송·구매 기록에서 수령 후 입고해야 반영됩니다.",
                  ]} />
                </>
              )}
            </>
          ) : null}
        </View>
      )}
    </Screen>
  );
}

function RecommendationCard({ item, onSelect, selected }: { item: RecommendationItem; onSelect: () => void; selected: boolean }) {
  const known = item.recommendedQuantity != null;
  return (
    <Card onPress={onSelect} selected={selected} style={{ gap: 9 }}>
      <View style={{ flexDirection: "row", gap: 6, flexWrap: "wrap" }}>
        <Badge label={!known ? "수량 판단 대기" : item.stockAlert ? "재고 부족 예상" : "재고 충분"} tone={!known ? "warning" : item.stockAlert ? "danger" : "success"} />
        {item.buySignal ? <Badge label="가격 신호" tone="success" /> : null}
      </View>
      <Text style={{ color: C.text, fontSize: 18, fontWeight: "700" }}>{item.ingredientName}</Text>
      <Text style={{ color: C.textSub, fontSize: 13 }}>현재 재고 {formatQuantity(item.currentStock)} {item.baseUnit} · 일평균 소비 {formatQuantity(item.dailyAvgSales)} {item.baseUnit}</Text>
      <Text style={{ color: item.stockAlert ? C.danger : C.text, fontWeight: "700" }}>
        {known ? `권장 발주 ${formatQuantity(item.recommendedQuantity as number)} ${item.baseUnit}` : "권장 수량을 아직 계산할 수 없습니다"}
      </Text>
      <Text style={{ color: C.textSub, fontSize: 12 }}>다음 발주일까지 {item.nextOrderDayDistance}일 · 예상 소진 {item.estimatedDepletionDate ?? "판단 대기"}</Text>
      <Text style={{ color: C.textSub, lineHeight: 19 }}>{item.reason ?? "재고 판단 근거 없음"}</Text>
      <Text style={{ color: C.textSub, fontSize: 12, lineHeight: 18 }}>{item.priceReason ?? "가격 판단 데이터 없음"} · 가격 자료 {item.priceDataCoverage}일</Text>
      <GradientButton title="상품·가격 확인" variant="soft" small icon="pricetag-outline" onPress={() => router.push(`/lowest-price/${item.ingredientId}`)} />
    </Card>
  );
}

function TrendSection({ ingredientId }: { ingredientId: number | null }) {
  const query = usePriceTrend(ingredientId ?? undefined, 30);
  if (query.isLoading) return <Spinner />;
  if (query.error) return <ErrorBox message={messageOf(query.error)} />;
  const data = query.data;
  const history: ChartPoint[] = (data?.points ?? [])
    .map((point) => ({ date: point.date, price: point.wholesalePrice ?? point.retailPrice ?? 0 }))
    .filter((point) => point.price > 0);
  if (!data || history.length === 0) return <Text style={{ color: C.textSub }}>가격 추세 자료가 아직 없습니다.</Text>;
  const last = data.points[data.points.length - 1];
  return (
    <View style={{ gap: 10 }}>
      <Text style={{ color: C.textSub, fontSize: 12 }}>{data.signalReason}</Text>
      <PriceChart history={history} monthly={last.monthAvg ?? 0} weekly={last.weekAvg ?? 0} current={last.wholesalePrice ?? last.retailPrice ?? 0} />
    </View>
  );
}
