import { useMemo, useState } from "react";
import { Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useQueryClient } from "@tanstack/react-query";
import { useDailyReport } from "../../hooks/useWorkflow";
import { useBatches, useCreateBatch } from "../../hooks/useInventoryBatches";
import type { LowestTopItem } from "../../types/ingredient";
import type { InventoryBatch } from "../../types/inventory";
import { bigUnitHint, formatQuantity, futureISO, isWithinDays, todayISO } from "../../lib/date";
import { C } from "../../components/theme";
import { DateTimeField } from "../../components/pickers";
import {
  Badge,
  Card,
  EmptyState,
  ErrorBox,
  Field,
  GradientButton,
  Input,
  PageHeader,
  Screen,
  SelectField,
  Sheet,
  Skeleton,
  Spinner,
} from "../../components/ui";

export default function InventoryScreen() {
  const qc = useQueryClient();
  const report = useDailyReport(todayISO());
  const { isLoading, refetch, isRefetching } = report;
  const ingredients: LowestTopItem[] = useMemo(() => (report.data?.currentInventory ?? []).map((item) => ({
    ingredientId: item.ingredientId, name: item.ingredientName, unit: item.baseUnit,
    weekAvg: null, monthAvg: null, todayPrice: null, dropRatePct: null, trend: null, externalLinks: [],
  })), [report.data]);
  const [addOpen, setAddOpen] = useState(false);
  const [detail, setDetail] = useState<LowestTopItem | null>(null);

  return (
    <Screen
      refreshing={isRefetching}
      onRefresh={() => {
        refetch();
        qc.invalidateQueries({ queryKey: ["batches"] });
      }}
    >
      <PageHeader
        title="재고 관리"
        description="재료별 현재 재고 + 입고 등록"
        right={<GradientButton title="입고" icon="add" small onPress={() => setAddOpen(true)} />}
      />

      {report.isError ? (
        <><ErrorBox message={report.error.message} /><GradientButton title="재고 다시 조회" onPress={() => refetch()} /></>
      ) : isLoading ? (
        <Skeleton count={5} height={110} />
      ) : ingredients.length === 0 ? (
        <EmptyState title="등록된 재료가 없습니다" description="온보딩에서 매장 카테고리를 선택하면 재료가 자동 등록됩니다." />
      ) : (
        <View style={{ gap: 12 }}>
          {ingredients.map((item) => (
            <StockCard key={item.ingredientId} item={item} onPress={() => setDetail(item)} />
          ))}
        </View>
      )}

      <AddBatchSheet visible={addOpen} ingredients={ingredients} onClose={() => setAddOpen(false)} />
      {detail && <BatchDetailSheet item={detail} onClose={() => setDetail(null)} />}
    </Screen>
  );
}

function StockCard({ item, onPress }: { item: LowestTopItem; onPress: () => void }) {
  const { data: batches = [], isLoading, error } = useBatches(item.ingredientId);
  const total = useMemo(() => batches.reduce((sum, b) => sum + Number(b.quantity), 0), [batches]);
  const nearest = useMemo(
    () => (batches.length ? [...batches].sort((a, b) => (a.expiresAt < b.expiresAt ? -1 : 1))[0].expiresAt : null),
    [batches],
  );
  const soon = nearest ? isWithinDays(nearest, 7) : false;

  return (
    <Card onPress={onPress}>
      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
        <Text style={{ fontSize: 17, fontWeight: "700", color: C.text }}>{item.name}</Text>
        {batches.length > 0 && <Badge label={`배치 ${batches.length}건`} />}
      </View>
      {error ? <ErrorBox message={error.message} /> : isLoading ? (
        <View style={{ height: 28, width: 100, backgroundColor: "#EEF2F7", borderRadius: 6, marginTop: 10 }} />
      ) : (
        <Text style={{ marginTop: 8, fontSize: 26, fontWeight: "800", color: total > 0 ? C.primary : C.textMute }}>
          {formatQuantity(total)}
          <Text style={{ fontSize: 14, fontWeight: "500", color: C.textMute }}> {item.unit ?? ""}</Text>
          {bigUnitHint(total, item.unit) ? (
            <Text style={{ fontSize: 12, fontWeight: "400", color: C.textMute }}>  ({bigUnitHint(total, item.unit)})</Text>
          ) : null}
        </Text>
      )}
      <View style={{ flexDirection: "row", alignItems: "center", marginTop: 10, paddingTop: 10, borderTopWidth: 1, borderColor: C.border }}>
        <Text style={{ flex: 1, fontSize: 12, color: soon ? "#B45309" : C.textSub }}>
          {nearest ? `가장 가까운 유통기한: ${nearest}${soon ? " ⚠ 임박" : ""}` : "재고 없음 — 입고가 필요합니다"}
        </Text>
        <Ionicons name="chevron-forward" size={18} color={C.textMute} />
      </View>
    </Card>
  );
}

function BatchDetailSheet({ item, onClose }: { item: LowestTopItem; onClose: () => void }) {
  const { data: batches = [], isLoading, error, refetch } = useBatches(item.ingredientId);
  const total = batches.reduce((s, b) => s + Number(b.quantity), 0);
  return (
    <Sheet visible onClose={onClose} title={item.name} subtitle="배치별 잔여 재고 (유통기한 빠른 순)">
      {error ? <><ErrorBox message={error.message} /><GradientButton title="배치 다시 조회" onPress={() => refetch()} /></> : isLoading ? (
        <Spinner />
      ) : batches.length === 0 ? (
        <Text style={{ textAlign: "center", color: C.textSub, paddingVertical: 20 }}>등록된 배치가 없습니다</Text>
      ) : (
        batches.map((b, i) => <BatchRow key={b.batchId} batch={b} index={i} unit={item.unit ?? ""} />)
      )}
      <View style={{ flexDirection: "row", justifyContent: "space-between", paddingTop: 12, borderTopWidth: 1, borderColor: C.border }}>
        <Text style={{ color: C.textSub }}>합계 ({batches.length}건)</Text>
        <Text style={{ fontWeight: "800", color: C.primary, fontSize: 16 }}>
          {formatQuantity(total)} {item.unit ?? ""}
        </Text>
      </View>
      <GradientButton title="닫기" variant="outline" onPress={onClose} />
    </Sheet>
  );
}

function BatchRow({ batch, index, unit }: { batch: InventoryBatch; index: number; unit: string }) {
  const soon = isWithinDays(batch.expiresAt, 7);
  return (
    <View
      style={{
        flexDirection: "row",
        alignItems: "center",
        gap: 12,
        padding: 12,
        borderRadius: 12,
        borderWidth: 1,
        borderColor: soon ? "#FDE68A" : C.border,
        backgroundColor: soon ? C.warningSoft : "#fff",
      }}
    >
      <View style={{ width: 26, height: 26, borderRadius: 13, backgroundColor: C.primarySoft2, alignItems: "center", justifyContent: "center" }}>
        <Text style={{ fontSize: 12, fontWeight: "700", color: C.primaryDark }}>{index + 1}</Text>
      </View>
      <View style={{ flex: 1 }}>
        <Text style={{ fontWeight: "700", color: C.text }}>
          {formatQuantity(Number(batch.quantity))} {unit}
        </Text>
        <Text style={{ fontSize: 12, color: soon ? "#B45309" : C.textMute }}>
          유통기한 {batch.expiresAt}
          {soon ? " ⚠ 임박" : ""}
        </Text>
      </View>
      <Text style={{ fontSize: 11, color: C.textMute }}>#{batch.batchId}</Text>
    </View>
  );
}

function AddBatchSheet({
  visible,
  ingredients,
  onClose,
}: {
  visible: boolean;
  ingredients: LowestTopItem[];
  onClose: () => void;
}) {
  const createMutation = useCreateBatch();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [quantity, setQuantity] = useState("");
  const [cost, setCost] = useState("");
  const [inboundDate, setInboundDate] = useState(todayISO());
  const [expirationDate, setExpirationDate] = useState(futureISO(14));
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const reset = () => {
    setSelectedId(null);
    setQuantity("");
    setCost("");
    setInboundDate(todayISO());
    setExpirationDate(futureISO(14));
    setErrorMsg(null);
  };

  const currentId = selectedId ?? ingredients[0]?.ingredientId ?? null;
  const currentUnit = ingredients.find((i) => i.ingredientId === currentId)?.unit ?? "";

  const submit = async () => {
    setErrorMsg(null);
    const id = currentId;
    if (!id) return setErrorMsg("재료를 선택해주세요.");
    const qty = Number.parseFloat(quantity);
    if (!Number.isFinite(qty) || qty <= 0 || !/^\d+(\.\d{1,3})?$/.test(quantity)) return setErrorMsg("수량은 0보다 큰 값으로 소수 셋째 자리까지 입력해주세요.");
    const unitCost = cost ? Number(cost) : undefined;
    if (unitCost !== undefined && (!Number.isFinite(unitCost) || unitCost < 0 || !/^\d+(\.\d{1,4})?$/.test(cost))) return setErrorMsg("단가는 0 이상으로 소수 넷째 자리까지 입력해주세요.");
    if (!expirationDate) return setErrorMsg("유통기한을 입력해주세요.");
    try {
      await createMutation.mutateAsync({
        ingredientId: id,
        quantity: qty,
        costPerUnit: unitCost,
        inboundDate: inboundDate || undefined,
        expirationDate,
      });
      reset();
      onClose();
    } catch (err) {
      setErrorMsg(err instanceof Error ? `입고 실패: ${err.message}` : "입고에 실패했습니다.");
    }
  };

  return (
    <Sheet visible={visible} onClose={onClose} title="📦 재고 입고" subtitle="입고된 재료를 배치로 등록합니다">
      <Field label="재료 *">
        <SelectField
          title="재료 선택"
          value={currentId}
          options={ingredients.map((i) => ({ value: i.ingredientId, label: i.name }))}
          onChange={setSelectedId}
        />
      </Field>
      <View style={{ flexDirection: "row", gap: 10 }}>
        <View style={{ flex: 1 }}>
          <Field label="수량 *">
            <Input value={quantity} onChangeText={setQuantity} keyboardType="decimal-pad" placeholder="1000" suffix={currentUnit} />
          </Field>
        </View>
        <View style={{ flex: 1 }}>
          <Field label="단가" hint={`재고 기준 단위 ${currentUnit} 하나당 가격`}>
            <Input value={cost} onChangeText={setCost} keyboardType="decimal-pad" placeholder="(선택)" suffix={`원/${currentUnit}`} />
          </Field>
        </View>
      </View>
      <View style={{ flexDirection: "row", gap: 10 }}>
        <View style={{ flex: 1 }}>
          <Field label="입고일">
            <DateTimeField value={inboundDate} onChange={setInboundDate} />
          </Field>
        </View>
        <View style={{ flex: 1 }}>
          <Field label="유통기한 *">
            <DateTimeField value={expirationDate} onChange={setExpirationDate} />
          </Field>
        </View>
      </View>
      <ErrorBox message={errorMsg} />
      <View style={{ flexDirection: "row", gap: 10 }}>
        <GradientButton title="취소" variant="outline" onPress={onClose} disabled={createMutation.isPending} style={{ flex: 1 }} />
        <GradientButton title="입고 등록" onPress={submit} loading={createMutation.isPending} style={{ flex: 1 }} />
      </View>
    </Sheet>
  );
}
