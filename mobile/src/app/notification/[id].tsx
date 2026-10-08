import { Text, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { useClosingNotification } from "../../hooks/useClosingNotification";
import type { RecommendationItem } from "../../types/notification";
import { C } from "../../components/theme";
import {
  Badge,
  Card,
  EmptyState,
  GradientButton,
  InfoCard,
  PageHeader,
  Screen,
  SectionTitle,
  Spinner,
  type Tone,
} from "../../components/ui";

const TYPE_LABEL: Record<string, string> = {
  UPLOAD: "판매 반영 알림",
  OPENING: "영업 전 알림",
};

const STATUS: Record<string, { label: string; tone: Tone }> = {
  SENT: { label: "발송됨", tone: "success" },
  PENDING: { label: "발송 대기", tone: "default" },
  WAITING_FOR_TOKEN: { label: "기기 토큰 대기", tone: "warning" },
  NOT_CONFIGURED: { label: "서버 푸시 미설정", tone: "warning" },
  FAILED: { label: "발송 실패", tone: "danger" },
  EXPIRED: { label: "만료", tone: "danger" },
};

function fmt(n: number | null | undefined) {
  if (n === null || n === undefined) return "-";
  return Number(n).toLocaleString("ko-KR", { maximumFractionDigits: 1 });
}

function ItemCard({ item }: { item: RecommendationItem }) {
  const u = item.baseUnit;
  return (
    <Card onPress={() => router.push(`/lowest-price/${item.ingredientId}`)}>
      <View style={{ flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 8 }}>
        <Text style={{ fontSize: 16, fontWeight: "700", color: C.text, flex: 1 }}>{item.ingredientName}</Text>
        {item.stockAlert ? <Badge label="재고 부족" tone="danger" /> : null}
        {item.buySignal ? <Badge label="매수 신호" tone="success" /> : null}
        {item.recommendedQuantity === null ? <Badge label="판단 자료 부족" tone="warning" /> : null}
      </View>
      <Text style={{ fontSize: 14, color: C.textSub }}>
        현재 {fmt(item.currentStock)}
        {u} · 하루 평균 {fmt(item.dailyAvgSales)}
        {u}
      </Text>
      {Number(item.recommendedQuantity) > 0 ? (
        <Text style={{ fontSize: 15, fontWeight: "600", color: C.primaryDark, marginTop: 6 }}>
          권장 발주 {fmt(item.recommendedQuantity)}
          {u}
        </Text>
      ) : null}
      {item.estimatedDepletionDate ? (
        <Text style={{ fontSize: 13, color: C.textSub, marginTop: 4 }}>소진 예상 {item.estimatedDepletionDate}</Text>
      ) : null}
      {item.reason ? <Text style={{ fontSize: 13, color: C.textSub, marginTop: 4 }}>{item.reason}</Text> : null}
      {item.priceReason ? <Text style={{ fontSize: 13, color: C.textSub, marginTop: 2 }}>{item.priceReason}</Text> : null}
    </Card>
  );
}

export default function NotificationDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const notificationId = Number(id);
  const { data, isLoading, isError, error, refetch, isRefetching } = useClosingNotification(notificationId);

  if (isLoading) {
    return (
      <Screen>
        <PageHeader title="알림 불러오는 중..." back />
        <Spinner />
      </Screen>
    );
  }
  if (isError || !data) {
    const notFound = (error as { status?: number } | null)?.status === 404;
    return (
      <Screen>
        <PageHeader title="알림" back />
        <EmptyState
          icon="notifications-off-outline"
          title={notFound ? "알림을 찾을 수 없습니다" : "알림을 불러오지 못했습니다"}
          description={notFound ? "다른 계정의 알림이거나 삭제된 알림입니다" : undefined}
          actionLabel="다시 시도"
          onAction={() => refetch()}
        />
      </Screen>
    );
  }

  const status = STATUS[data.deliveryStatus] ?? { label: data.deliveryStatus, tone: "default" as Tone };
  const rec = data.recommendations;
  const items = rec?.items ?? [];
  const alerts = items.filter((i) => i.stockAlert || i.buySignal || Number(i.recommendedQuantity) > 0);
  const unknown = items.filter((i) => i.recommendedQuantity === null && !alerts.includes(i));

  return (
    <Screen refreshing={isRefetching} onRefresh={refetch}>
      <PageHeader title={TYPE_LABEL[data.type] ?? "알림"} description={`영업일 ${data.businessDate}`} back />

      <Card>
        <View style={{ flexDirection: "row", gap: 6, marginBottom: 10 }}>
          <Badge label={TYPE_LABEL[data.type] ?? data.type} />
          <Badge label={status.label} tone={status.tone} />
        </View>
        <Text style={{ fontSize: 17, fontWeight: "700", color: C.text }}>{data.title}</Text>
        <Text style={{ fontSize: 14, color: C.textSub, marginTop: 6, lineHeight: 20 }}>{data.body}</Text>
      </Card>

      <SectionTitle>알림 당시 판단 ({alerts.length}건)</SectionTitle>
      {alerts.length > 0 ? (
        <View style={{ gap: 12 }}>
          {alerts.map((it) => (
            <ItemCard key={it.ingredientId} item={it} />
          ))}
        </View>
      ) : (
        <EmptyState
          icon={unknown.length || !rec ? "help-circle-outline" : "checkmark-circle-outline"}
          title={unknown.length || !rec ? "발주 판단에 필요한 자료를 확인해 주세요" : "판단 가능한 재료에 발주 신호가 없습니다"}
          description={unknown.length ? "판매 자료가 없거나 소비 이력이 부족합니다. 아래 재료의 확인 사항을 살펴보세요." : !rec ? "알림 당시 판단 자료가 없습니다. 현재 발주 판단을 확인하세요." : undefined}
        />
      )}

      {unknown.length > 0 && <>
        <SectionTitle>자료 확인이 필요한 재료 ({unknown.length}건)</SectionTitle>
        <View style={{ gap: 12 }}>{unknown.map((item) => <ItemCard key={item.ingredientId} item={item} />)}</View>
      </>}

      <View style={{ marginTop: 16, gap: 12 }}>
        <InfoCard
          title="안내"
          lines={[
            "위 내용은 알림을 보낸 시점의 판단입니다",
            rec?.lastReflectedAt ? `마지막 판매 반영 ${rec.lastReflectedAt.replace("T", " ").slice(0, 16)}` : "판매 반영 기록 없음",
            ...(rec && !rec.settingsConfigured ? ["매장 운영 설정이 없습니다. 설정 화면에서 운영 시간과 발주 요일을 입력하세요."] : []),
            "지금 재고 기준으로 다시 보려면 발주 화면을 확인하세요",
          ]}
        />
        <GradientButton title="지금 기준으로 발주 확인" onPress={() => router.push("/order")} />
      </View>
    </Screen>
  );
}
