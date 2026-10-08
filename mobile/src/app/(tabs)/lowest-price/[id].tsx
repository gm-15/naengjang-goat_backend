import { useEffect, useRef, useState } from "react";
import { Alert, AppState, Linking, Pressable, Text, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { LinearGradient } from "expo-linear-gradient";
import { Ionicons } from "@expo/vector-icons";
import { usePriceDetail } from "../../../hooks/usePriceDetail";
import { useCreatePurchaseOrder } from "../../../hooks/usePurchaseOrders";
import type { OnlinePrice, PriceDetail } from "../../../types/ingredient";
import { parseLocalDate, todayISO } from "../../../lib/date";
import { compatiblePurchaseUnits, preferredPurchaseUnit, previewPurchase } from "../../../lib/purchase-unit";
import { C, GRADIENT } from "../../../components/theme";
import { DateTimeField } from "../../../components/pickers";
import {
  Badge,
  Card,
  EmptyState,
  ErrorBox,
  Field,
  GradientButton,
  InfoCard,
  Input,
  PageHeader,
  Screen,
  SectionTitle,
  SelectField,
  Sheet,
  Spinner,
} from "../../../components/ui";

interface VisitedSource {
  source: string;
  sourceLabel: string;
  productName: string;
  price: number;
  sourceUrl?: string;
  weightGrams?: number | null;
  fromProduct?: boolean;
}

export default function LowestPriceDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const ingredientId = Number(id);
  const { data, isLoading, isError, refetch, isRefetching } = usePriceDetail(ingredientId);

  const pendingRef = useRef<VisitedSource | null>(null);
  const [visited, setVisited] = useState<VisitedSource | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);

  // 외부 쇼핑몰(브라우저)에서 앱으로 돌아오면 발주 추가 시트를 연다 (웹의 visibilitychange 대응)
  useEffect(() => {
    const sub = AppState.addEventListener("change", (state) => {
      if (state === "active" && pendingRef.current) {
        setVisited(pendingRef.current);
        pendingRef.current = null;
        setSheetOpen(true);
      }
    });
    return () => sub.remove();
  }, []);

  const visit = async (v: VisitedSource, url: string) => {
    pendingRef.current = { ...v, sourceUrl: url };
    try {
      await Linking.openURL(url);
    } catch {
      pendingRef.current = null;
      Alert.alert("구매 페이지", "외부 사이트를 열지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }
  };

  if (isLoading) {
    return (
      <Screen>
        <PageHeader title="로딩 중..." back />
        <Spinner />
      </Screen>
    );
  }
  if (isError || !data) {
    return (
      <Screen>
        <PageHeader title="조회 실패" back />
        <EmptyState icon="alert-circle-outline" title="재료 정보를 불러오지 못했습니다" actionLabel="다시 시도" onAction={() => refetch()} />
      </Screen>
    );
  }

  return (
    <Screen refreshing={isRefetching} onRefresh={refetch}>
      <PageHeader title={data.name} back />
      <KamisCard data={data} />

      {data.onlinePrices.length > 0 ? (
        <>
          <SectionTitle>온라인 최저가</SectionTitle>
          <View style={{ gap: 12 }}>
            {data.onlinePrices.map((p, i) => (
              <OnlinePriceCard key={`${p.source}-${i}`} p={p} onVisit={visit} />
            ))}
          </View>
        </>
      ) : (
        <SearchFallback data={data} onVisit={visit} />
      )}

      <View style={{ marginTop: 16 }}>
        <InfoCard
          title="가격 비교 안내"
          lines={[
            "KAMIS: 농산물 유통정보 공식 시세",
            data.onlinePrices.length > 0 ? "온라인 가격: 네이버 쇼핑·식자재왕 실시간 크롤링" : "온라인 가격: 수집된 항목 없음 — 검색 페이지로 대체",
            "구매 페이지에서 돌아오면 발주 기록 창이 자동으로 열립니다",
          ]}
        />
      </View>

      <Pressable
        onPress={() => {
          setVisited({ source: "MANUAL", sourceLabel: "", productName: data.name, price: compatiblePurchaseUnits(data.unit).includes("kg") ? data.kamis?.currentPricePerKg ?? 0 : 0 });
          setSheetOpen(true);
        }}
        style={{ alignItems: "center", paddingVertical: 16 }}
      >
        <Text style={{ color: C.primary, fontWeight: "600" }}>+ 실제 구매한 내용 기록하기</Text>
      </Pressable>

      {visited && (
        <OrderSheet
          visible={sheetOpen}
          data={data}
          visited={visited}
          onClose={() => setSheetOpen(false)}
          onDone={() => {
            setSheetOpen(false);
            router.navigate({ pathname: "/order", params: { tab: "history" } });
          }}
        />
      )}
    </Screen>
  );
}

function KamisCard({ data }: { data: PriceDetail }) {
  const k = data.kamis;
  return (
    <LinearGradient colors={GRADIENT} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={{ borderRadius: 20, padding: 20, marginBottom: 8 }}>
      <View style={{ flexDirection: "row", alignItems: "center", gap: 6 }}>
        <Ionicons name="stats-chart-outline" size={16} color="#fff" />
        <Text style={{ color: "rgba(255,255,255,0.9)", fontSize: 14 }}>KAMIS 공식 시세</Text>
      </View>
      {k && k.currentPricePerKg !== null ? (
        <>
          <Text style={{ color: "#fff", fontSize: 34, fontWeight: "800", marginTop: 8 }}>
            {k.currentPricePerKg.toLocaleString("ko-KR")}원<Text style={{ fontSize: 15, fontWeight: "500" }}> / kg</Text>
          </Text>
          <View style={{ flexDirection: "row", gap: 10, marginTop: 14 }}>
            {[
              ["주 평균", k.weekAvg],
              ["월 평균", k.monthAvg],
            ].map(([label, v]) => (
              <View key={label as string} style={{ flex: 1, backgroundColor: "rgba(255,255,255,0.18)", borderRadius: 12, padding: 10 }}>
                <Text style={{ color: "rgba(255,255,255,0.85)", fontSize: 12 }}>{label}</Text>
                <Text style={{ color: "#fff", fontWeight: "700", fontSize: 15, marginTop: 2 }}>
                  {v !== null ? `${(v as number).toLocaleString("ko-KR")}원` : "—"}
                </Text>
              </View>
            ))}
          </View>
          {k.priceDate ? <Text style={{ color: "rgba(255,255,255,0.75)", fontSize: 12, marginTop: 10 }}>기준일: {k.priceDate}</Text> : null}
        </>
      ) : (
        <>
          <Text style={{ color: "#fff", fontSize: 22, fontWeight: "700", marginTop: 8 }}>데이터 수집 중</Text>
          <Text style={{ color: "rgba(255,255,255,0.85)", fontSize: 13, marginTop: 4 }}>KAMIS 일일 배치 실행 후 표시됩니다.</Text>
        </>
      )}
    </LinearGradient>
  );
}

function OnlinePriceCard({ p, onVisit }: { p: OnlinePrice; onVisit: (v: VisitedSource, url: string) => void }) {
  return (
    <Card selected={p.isLowest}>
      <View style={{ flexDirection: "row", gap: 12 }}>
        <View style={{ width: 44, height: 44, borderRadius: 10, borderWidth: 1.5, borderColor: C.border, alignItems: "center", justifyContent: "center" }}>
          <Text style={{ fontSize: 20 }}>🛒</Text>
        </View>
        <View style={{ flex: 1, gap: 3 }}>
          <Text style={{ fontWeight: "700", fontSize: 16, color: C.text }}>{p.sourceLabel}</Text>
          <Text style={{ fontSize: 12, color: C.textSub }} numberOfLines={1}>
            {p.productName}
          </Text>
          {p.isLowest && <Badge label="최저가" />}
        </View>
        <View style={{ alignItems: "flex-end" }}>
          <Text style={{ fontSize: 20, fontWeight: "800", color: C.primary }}>{p.price.toLocaleString("ko-KR")}원</Text>
          {p.unitPricePerKg !== null && (
            <Text style={{ fontSize: 11, color: C.textMute }}>kg당 {p.unitPricePerKg.toLocaleString("ko-KR")}원</Text>
          )}
        </View>
      </View>
      <GradientButton
        title="구매하러 가기"
        icon="open-outline"
        small
        variant={p.isLowest ? "primary" : "soft"}
        style={{ marginTop: 12 }}
        onPress={() => onVisit({ source: p.source, sourceLabel: p.sourceLabel, productName: p.productName, price: p.price, weightGrams: p.weightGrams, fromProduct: true }, p.productUrl)}
      />
    </Card>
  );
}

function SearchFallback({ data, onVisit }: { data: PriceDetail; onVisit: (v: VisitedSource, url: string) => void }) {
  const labelMap: Record<string, string> = { NAVER_SEARCH: "네이버 쇼핑", SIKJAJAEWANG_SEARCH: "식자재왕" };
  return (
    <>
      <SectionTitle>외부 검색</SectionTitle>
      <Text style={{ color: C.textSub, fontSize: 13, marginTop: -6, marginBottom: 12 }}>
        수집된 온라인 가격이 없습니다. 아래 사이트에서 직접 검색해 보세요.
      </Text>
      <View style={{ gap: 12 }}>
        {data.externalSearchLinks.map((link) => {
          const label = labelMap[link.source] ?? link.source;
          return (
            <Card key={link.source} onPress={() => onVisit({ source: link.source, sourceLabel: label, productName: data.name, price: 0 }, link.url)}>
              <View style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
                <Text style={{ fontSize: 22 }}>🔍</Text>
                <View style={{ flex: 1 }}>
                  <Text style={{ fontWeight: "700", color: C.text }}>{label}</Text>
                  <Text style={{ fontSize: 12, color: C.textSub }}>"{data.name}" 검색 페이지로 이동</Text>
                </View>
                <Ionicons name="open-outline" size={18} color={C.textMute} />
              </View>
            </Card>
          );
        })}
      </View>
    </>
  );
}

function OrderSheet({
  visible,
  data,
  visited,
  onClose,
  onDone,
}: {
  visible: boolean;
  data: PriceDetail;
  visited: VisitedSource;
  onClose: () => void;
  onDone: () => void;
}) {
  const createMutation = useCreatePurchaseOrder();
  const sending = useRef(false);
  const units = compatiblePurchaseUnits(data.unit);
  const defaultUnit = visited.fromProduct ? "포장" : preferredPurchaseUnit(data.unit) ?? "";
  const [productName, setProductName] = useState(visited.productName);
  const [sourceUrl, setSourceUrl] = useState(visited.sourceUrl ?? "");
  const [orderedAt, setOrderedAt] = useState(todayISO());
  const [quantity, setQuantity] = useState("1");
  const [baseUnit, setBaseUnit] = useState<string>(defaultUnit);
  const [packageSize, setPackageSize] = useState(visited.weightGrams && units.includes("g") ? String(visited.weightGrams) : "");
  const [packageUnit, setPackageUnit] = useState<string>(units.includes("g") ? "g" : preferredPurchaseUnit(data.unit) ?? "");
  const [unitPrice, setUnitPrice] = useState(visited.price > 0 ? String(visited.price) : "");
  const [supplier, setSupplier] = useState(visited.sourceLabel);
  const [memo, setMemo] = useState("");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const busy = createMutation.isPending;

  useEffect(() => {
    if (visible) {
      setProductName(visited.productName);
      setSourceUrl(visited.sourceUrl ?? "");
      setOrderedAt(todayISO());
      setQuantity("1");
      setBaseUnit(visited.fromProduct ? "포장" : preferredPurchaseUnit(data.unit) ?? "");
      setPackageSize(visited.weightGrams && compatiblePurchaseUnits(data.unit).includes("g") ? String(visited.weightGrams) : "");
      setPackageUnit(compatiblePurchaseUnits(data.unit).includes("g") ? "g" : preferredPurchaseUnit(data.unit) ?? "");
      setUnitPrice(visited.price > 0 ? String(visited.price) : "");
      setSupplier(visited.sourceLabel);
      setMemo("");
      setErrorMsg(null);
    }
  }, [visible, visited, data.unit]);

  let preview: ReturnType<typeof previewPurchase> | null = null;
  let previewError = "";
  try {
    if (!quantity.trim() || !unitPrice.trim()) throw new Error("구매 수량과 단가를 입력해 주세요.");
    preview = previewPurchase(Number(quantity), baseUnit, data.unit, Number(unitPrice), Number(packageSize), packageUnit);
  } catch (error) { previewError = error instanceof Error ? error.message : "수량과 단위를 확인해 주세요."; }

  const confirm = async () => {
    if (sending.current) return;
    setErrorMsg(null);
    if (!productName.trim()) return setErrorMsg("실제 구매한 상품명을 입력해 주세요.");
    if (!supplier.trim()) return setErrorMsg("공급자를 입력해 주세요.");
    if (!preview) return setErrorMsg(previewError);
    const date = parseLocalDate(orderedAt);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(orderedAt) || date.getFullYear() !== Number(orderedAt.slice(0, 4)) || date.getMonth() + 1 !== Number(orderedAt.slice(5, 7)) || date.getDate() !== Number(orderedAt.slice(8, 10)) || orderedAt > todayISO())
      return setErrorMsg("주문일은 오늘까지의 올바른 날짜로 입력해 주세요.");
    if (sourceUrl.trim()) {
      try {
        const url = new URL(sourceUrl.trim());
        if (!["http:", "https:"].includes(url.protocol) || !url.hostname) throw new Error();
      } catch { return setErrorMsg("상품 링크는 http 또는 https 주소로 입력해 주세요."); }
    }
    sending.current = true;
    try {
      await createMutation.mutateAsync({
        ingredientId: data.ingredientId,
        orderedAt,
        quantity: Number(quantity),
        baseUnit,
        unitPrice: Number(unitPrice),
        supplier: supplier.trim(),
        productName: productName.trim(),
        sourceUrl: sourceUrl.trim() || undefined,
        packageSize: baseUnit === "포장" ? Number(packageSize) : undefined,
        packageUnit: baseUnit === "포장" ? packageUnit : undefined,
        memo: memo.trim() || undefined,
      });
      onDone();
      Alert.alert("구매 기록 완료", "배송 대기로 저장했습니다. 재료를 받은 뒤 발주 관리에서 입고 등록하면 재고에 반영됩니다.");
    } catch (err) {
      setErrorMsg(err instanceof Error ? `구매 기록 저장 실패: ${err.message}` : "구매 기록 저장에 실패했습니다.");
    } finally { sending.current = false; }
  };

  return (
    <Sheet visible={visible} onClose={() => { if (!sending.current) onClose(); }} title="실제 주문한 내용 기록" subtitle="외부 사이트에서 구매한 상품을 확인해 주세요">
      <View style={{ backgroundColor: C.primarySoft, borderRadius: 12, padding: 14, gap: 6 }}>
        <Row k="반영할 재료" v={data.name} />
        <Row k="재고 기본 단위" v={data.unit} />
        <Text style={{ color: C.textSub, fontSize: 12 }}>지금은 구매 기록만 저장합니다. 재료를 받은 뒤 입고 등록하세요.</Text>
      </View>
      <Field label="구매 상품명 *" hint="방문한 상품명을 미리 입력했습니다. 실제 구매 내용에 맞게 수정하세요.">
        <Input value={productName} onChangeText={setProductName} maxLength={255} placeholder="실제로 구매한 상품명" />
      </Field>
      <Field label="주문일 *"><DateTimeField value={orderedAt} onChange={setOrderedAt} /></Field>
      <Field label="구매 수량 단위 *" hint="상품 한 개 가격이면 포장을 선택하고 포장당 크기를 입력하세요.">
        <SelectField<string> title="구매 단위" value={baseUnit || null} options={[...units.map((unit) => ({ value: unit, label: unit })), { value: "포장", label: "포장 (봉지·박스·묶음)" }]} onChange={(unit) => { setBaseUnit(unit); setUnitPrice(""); }} />
      </Field>
      {baseUnit === "포장" && <View style={{ flexDirection: "row", gap: 10 }}>
        <View style={{ flex: 1 }}><Field label="포장 한 개의 크기 *"><Input value={packageSize} onChangeText={setPackageSize} keyboardType="decimal-pad" placeholder="예: 2" /></Field></View>
        <View style={{ flex: 1 }}><Field label="포장 크기 단위 *"><SelectField<string> title="포장 크기 단위" value={packageUnit || null} options={units.map((unit) => ({ value: unit, label: unit }))} onChange={setPackageUnit} /></Field></View>
      </View>}
      <View style={{ flexDirection: "row", gap: 10 }}>
        <View style={{ flex: 1 }}><Field label="구매 수량 *"><Input value={quantity} onChangeText={setQuantity} keyboardType="decimal-pad" suffix={baseUnit} /></Field></View>
        <View style={{ flex: 1 }}><Field label={`단가 * (원 / ${baseUnit || "구매 단위"})`}><Input value={unitPrice} onChangeText={setUnitPrice} keyboardType="decimal-pad" suffix="원" /></Field></View>
      </View>
      <View style={{ backgroundColor: C.primarySoft, borderRadius: 12, padding: 14, gap: 6 }}>
        {preview ? <><Row k="구매 총액" v={`${preview.totalAmount.toLocaleString("ko-KR", { maximumFractionDigits: 2 })}원`} /><Row k="수령 후 입고 예정" v={`${preview.expectedQuantityBase.toLocaleString("ko-KR", { maximumFractionDigits: 3 })}${data.unit}`} /></> : <Text style={{ color: C.textSub, fontSize: 13 }}>{previewError}</Text>}
      </View>
      <Field label="공급자 *"><Input value={supplier} onChangeText={setSupplier} maxLength={100} placeholder="예: 네이버 쇼핑 판매자, 식자재왕" /></Field>
      <Field label="상품 링크 (선택)"><Input value={sourceUrl} onChangeText={setSourceUrl} maxLength={2048} autoCapitalize="none" keyboardType="url" placeholder="https://..." /></Field>
      <Field label="메모 (선택)"><Input value={memo} onChangeText={setMemo} maxLength={4096} placeholder="배송 요청 사항 등" multiline style={{ minHeight: 60, textAlignVertical: "top" }} /></Field>
      <ErrorBox message={errorMsg} />
      <View style={{ flexDirection: "row", gap: 10 }}>
        <GradientButton title="구매하지 않음 · 닫기" variant="outline" onPress={onClose} disabled={busy} style={{ flex: 1 }} />
        <GradientButton title="주문한 내용 저장" onPress={confirm} loading={busy} disabled={!preview || units.length === 0} style={{ flex: 1 }} />
      </View>
    </Sheet>
  );
}

function Row({ k, v }: { k: string; v: string }) {
  return (
    <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
      <Text style={{ color: C.textSub, fontSize: 14 }}>{k}</Text>
      <Text style={{ color: C.text, fontWeight: "600", fontSize: 14 }}>{v}</Text>
    </View>
  );
}
