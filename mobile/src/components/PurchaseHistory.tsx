import { useRef, useState } from "react";
import { Alert, Linking, Pressable, Text, View } from "react-native";
import { ApiError } from "../api/client";
import { defaultRange, useExportPurchaseOrders, usePurchaseOrders, useReceivePurchaseOrder } from "../hooks/usePurchaseOrders";
import type { DeliveryStatus, PurchaseOrder } from "../types/order";
import { futureISO, parseLocalDate, todayISO } from "../lib/date";
import { DateTimeField } from "./pickers";
import { C } from "./theme";
import { Badge, Card, EmptyState, ErrorBox, Field, GradientButton, InfoCard, Sheet, Spinner } from "./ui";

const DELIVERY: Record<DeliveryStatus, { label: string; tone: "warning" | "success" | "default" }> = {
  WAITING: { label: "배송 대기", tone: "warning" },
  RECEIVED: { label: "입고 완료", tone: "success" },
  LEGACY: { label: "기존 기록", tone: "default" },
};
const quantityLabel = (quantity: number) => Number(quantity).toLocaleString("ko-KR", { maximumFractionDigits: 3 });
const money = (value: number) => `${Number(value).toLocaleString("ko-KR", { maximumFractionDigits: 2 })}원`;
const validDate = (value: string) => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = parseLocalDate(value);
  return !Number.isNaN(date.getTime()) && date.getFullYear() === Number(value.slice(0, 4))
    && date.getMonth() + 1 === Number(value.slice(5, 7)) && date.getDate() === Number(value.slice(8, 10));
};

/** 발주 관리 화면에서 사용하는 배송/입고 목록. 구매 기록만으로 재고를 늘리지 않는다. */
export function PurchaseHistory() {
  const [range, setRange] = useState(() => defaultRange());
  const [draftRange, setDraftRange] = useState(range);
  const [deliveryStatus, setDeliveryStatus] = useState<DeliveryStatus | undefined>();
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<PurchaseOrder | null>(null);
  const [rangeError, setRangeError] = useState<string | null>(null);
  const query = usePurchaseOrders({ ...range, deliveryStatus, page, size: 20 });
  const exportMutation = useExportPurchaseOrders();

  const applyRange = () => {
    if (!validDate(draftRange.from) || !validDate(draftRange.to)) return setRangeError("조회 기간을 올바른 날짜로 입력해 주세요.");
    if (draftRange.from > draftRange.to) return setRangeError("시작일은 종료일 이하여야 합니다.");
    const days = (parseLocalDate(draftRange.to).getTime() - parseLocalDate(draftRange.from).getTime()) / 86400000;
    if (days > 365) return setRangeError("조회 기간은 최대 365일입니다.");
    setRangeError(null);
    setRange(draftRange);
    setPage(0);
  };

  return (
    <View style={{ gap: 14 }}>
      <InfoCard title="배송받은 뒤 재고에 반영" lines={["실제 주문을 기록하면 배송 대기로 저장됩니다.", "재료를 받았을 때 입고 등록을 눌러 유통기한을 입력하세요.", "기존 기록은 재고 반영 여부를 확인할 수 없어 재입고할 수 없습니다."]} />
      <Card>
        <View style={{ flexDirection: "row", gap: 10 }}>
          <View style={{ flex: 1 }}><Field label="조회 시작일"><DateTimeField value={draftRange.from} onChange={(from) => setDraftRange((r) => ({ ...r, from }))} /></Field></View>
          <View style={{ flex: 1 }}><Field label="조회 종료일"><DateTimeField value={draftRange.to} onChange={(to) => setDraftRange((r) => ({ ...r, to }))} /></Field></View>
        </View>
        <ErrorBox message={rangeError} />
        <View style={{ flexDirection: "row", gap: 10, marginTop: 12 }}>
          <GradientButton title="기간 조회" small onPress={applyRange} style={{ flex: 1 }} />
          <GradientButton title="기간 엑셀" small variant="soft" icon="download-outline" loading={exportMutation.isPending} style={{ flex: 1 }} onPress={() => exportMutation.mutate(range, { onError: (error) => Alert.alert("내보내기 실패", error.message) })} />
        </View>
        <Text style={{ fontSize: 11, color: C.textMute, marginTop: 6 }}>엑셀에는 조회 기간의 모든 배송 상태가 포함됩니다.</Text>
      </Card>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8 }}>
        {([undefined, "WAITING", "RECEIVED", "LEGACY"] as const).map((status) => (
          <Pressable key={status ?? "ALL"} onPress={() => { setDeliveryStatus(status); setPage(0); }} style={{ paddingHorizontal: 12, paddingVertical: 9, borderRadius: 12, backgroundColor: deliveryStatus === status ? C.primary : "#fff", borderWidth: 1, borderColor: deliveryStatus === status ? C.primary : C.border }}>
            <Text style={{ color: deliveryStatus === status ? "#fff" : C.textSub, fontWeight: "600", fontSize: 13 }}>{status ? DELIVERY[status].label : "전체"}</Text>
          </Pressable>
        ))}
      </View>
      <Text style={{ color: C.textSub, fontSize: 12 }}>{range.from} ~ {range.to}{query.data ? ` · 총 ${query.data.totalElements}건` : ""}</Text>
      {query.isPending ? <Spinner /> : query.isError ? (
        <><ErrorBox message={`구매 기록을 불러오지 못했습니다: ${query.error.message}`} /><GradientButton title="다시 조회" variant="soft" onPress={() => query.refetch()} /></>
      ) : query.data?.content.length === 0 ? (
        <EmptyState icon="cube-outline" title="해당하는 구매 기록이 없습니다" description="조회 기간과 배송 상태를 확인해 주세요." />
      ) : query.data?.content.map((order) => {
        const delivery = DELIVERY[order.deliveryStatus] ?? DELIVERY.LEGACY;
        return (
          <Card key={order.id}>
            <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 8 }}><Badge label={delivery.label} tone={delivery.tone} /><Text style={{ color: C.textMute, fontSize: 12 }}>주문일 {order.orderedAt}</Text></View>
            <Text style={{ fontSize: 17, fontWeight: "700", color: C.text }}>{order.productName || order.ingredientName}</Text>
            <Text style={{ fontSize: 12, color: C.textSub, marginTop: 3 }}>{order.ingredientName} · {order.supplier}</Text>
            <Text style={{ color: C.text, marginTop: 10 }}>{quantityLabel(order.quantity)}{order.baseUnit} × {money(order.unitPrice)} / {order.baseUnit}</Text>
            <Text style={{ fontSize: 19, fontWeight: "800", color: C.primary, marginTop: 3 }}>총 {money(order.totalAmount)}</Text>
            {order.expectedQuantityBase != null && order.inventoryUnit && <Text style={{ color: C.textSub, marginTop: 8 }}>{order.deliveryStatus === "RECEIVED" ? "입고된 수량" : "입고 예정 수량"}: {quantityLabel(order.expectedQuantityBase)}{order.inventoryUnit}</Text>}
            {order.receivedAt && <Text style={{ color: C.textMute, fontSize: 12, marginTop: 4 }}>입고 등록 {order.receivedAt.replace("T", " ").slice(0, 16)}</Text>}
            {order.memo && <Text style={{ color: C.textSub, fontSize: 12, marginTop: 8 }}>메모: {order.memo}</Text>}
            {order.sourceUrl && <Pressable onPress={() => { Linking.openURL(order.sourceUrl as string).catch(() => Alert.alert("상품 링크", "상품 페이지를 열지 못했습니다.")); }} style={{ paddingVertical: 10 }}><Text style={{ color: C.primary, fontWeight: "600" }}>구매한 상품 페이지 ↗</Text></Pressable>}
            {order.deliveryStatus === "WAITING" && order.expectedQuantityBase != null && order.inventoryUnit && (
              <GradientButton title="수령 후 입고 등록" small icon="cube-outline" style={{ marginTop: 12 }} onPress={() => setSelected(order)} />
            )}
            {order.deliveryStatus === "LEGACY" && <Text style={{ color: C.textMute, fontSize: 12, marginTop: 8 }}>이전 방식의 기록입니다. 기존 재고 반영 여부를 확인하세요.</Text>}
          </Card>
        );
      })}
      {query.data && query.data.totalPages > 1 && <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 12 }}>
        <GradientButton title="이전" small variant="outline" disabled={query.data.first || query.isFetching} onPress={() => setPage((p) => Math.max(0, p - 1))} />
        <Text style={{ color: C.textSub }}>{query.data.number + 1} / {query.data.totalPages}</Text>
        <GradientButton title="다음" small variant="outline" disabled={query.data.last || query.isFetching} onPress={() => setPage((p) => p + 1)} />
      </View>}
      {selected && <ReceiptSheet key={selected.id} order={selected} onClose={() => setSelected(null)} onRefresh={() => { query.refetch(); }} />}
    </View>
  );
}

function ReceiptSheet({ order, onClose, onRefresh }: { order: PurchaseOrder; onClose: () => void; onRefresh: () => void }) {
  const mutation = useReceivePurchaseOrder();
  const sending = useRef(false);
  const [inboundDate, setInboundDate] = useState(todayISO());
  const [expirationDate, setExpirationDate] = useState(futureISO(14));
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);

  const confirm = async () => {
    if (sending.current || conflict) return;
    setError(null);
    if (!validDate(inboundDate) || !validDate(expirationDate)) return setError("입고일과 유통기한을 올바른 날짜로 입력해 주세요.");
    if (inboundDate < order.orderedAt || inboundDate > todayISO()) return setError("입고일은 주문일부터 오늘 사이여야 합니다.");
    if (expirationDate < todayISO() || expirationDate < inboundDate) return setError("유통기한은 오늘과 입고일 이후여야 합니다.");
    if (order.expectedQuantityBase == null || order.expectedQuantityBase <= 0) return setError("입고 예정 수량을 확인할 수 없습니다. 주문 목록을 다시 조회해 주세요.");
    sending.current = true;
    try {
      await mutation.mutateAsync({ id: order.id, payload: { receivedQuantity: order.expectedQuantityBase, inboundDate, expirationDate } });
      onClose();
      Alert.alert("입고 완료", `${order.ingredientName} ${quantityLabel(order.expectedQuantityBase)}${order.inventoryUnit}이 재고에 반영되었습니다.`);
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 409) {
        setConflict(true);
        setError("이미 입고되었거나 입고 가능한 주문이 아닙니다. 목록을 새로 조회했습니다. 이 창을 닫고 배송 상태를 확인해 주세요.");
      } else setError(reason instanceof Error ? `입고 등록 실패: ${reason.message}` : "입고 등록에 실패했습니다. 배송 상태를 확인한 후 다시 시도해 주세요.");
      onRefresh();
    } finally {
      sending.current = false;
    }
  };

  return (
    <Sheet visible onClose={() => { if (!sending.current) onClose(); }} title="배송 수령 · 입고 등록" subtitle="실제로 받은 재료의 유통기한을 확인하세요">
      <Card><Text style={{ color: C.text, fontSize: 17, fontWeight: "700" }}>{order.productName || order.ingredientName}</Text><Text style={{ color: C.textSub, marginTop: 8 }}>전체 수령: {quantityLabel(order.expectedQuantityBase ?? 0)}{order.inventoryUnit}</Text><Text style={{ color: C.textMute, fontSize: 12, marginTop: 6 }}>이번 버전은 주문 수량 전체 입고만 지원합니다.</Text></Card>
      <Field label="입고일 *"><DateTimeField value={inboundDate} onChange={setInboundDate} minimumDate={parseLocalDate(order.orderedAt)} /></Field>
      <Field label="유통기한 *"><DateTimeField value={expirationDate} onChange={setExpirationDate} minimumDate={parseLocalDate(todayISO())} /></Field>
      <ErrorBox message={error} />
      <View style={{ flexDirection: "row", gap: 10 }}><GradientButton title="닫기" variant="outline" disabled={mutation.isPending} onPress={onClose} style={{ flex: 1 }} /><GradientButton title="수령 확인 · 재고 반영" loading={mutation.isPending} disabled={conflict} onPress={confirm} style={{ flex: 1 }} /></View>
    </Sheet>
  );
}
