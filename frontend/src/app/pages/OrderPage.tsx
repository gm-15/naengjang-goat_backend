import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router";
import { AppShell } from "../components/common/AppShell";
import { PageHeader } from "../components/common/PageHeader";
import { RecommendationsPanel } from "../components/common/RecommendationsPanel";
import { PriceChart } from "../components/common/PriceChart";
import { getRecommendations } from "../api/workflow";
import { usePriceTrend } from "../hooks/usePriceTrend";
import {
  defaultRange,
  useExportPurchaseOrders,
  usePurchaseOrders,
  useReceivePurchaseOrder,
} from "../hooks/usePurchaseOrders";
import { formatQuantity, todayKorean } from "../lib/workflow";
import type { DeliveryStatus, PurchaseOrder } from "../types/order";

export default function OrderPage() {
  const [params, setParams] = useSearchParams();
  const history = params.get("tab") === "history";
  const switchTab = (isHistory: boolean) => {
    const next = new URLSearchParams(params);
    if (isHistory) next.set("tab", "history");
    else next.delete("tab");
    setParams(next);
  };
  return (
    <AppShell variant="main">
      <PageHeader
        title="발주 · 배송 관리"
        description="판매 반영 후 구매를 판단하고, 실제 수령한 주문을 입고합니다."
        backTo="/main"
      />
      <div className="flex flex-wrap gap-2 mb-5">
        <button
          onClick={() => switchTab(false)}
          className={`px-4 py-2 rounded-xl ${!history ? "bg-sky-500 text-white" : "bg-white border text-slate-600"}`}
        >
          현재 발주 판단
        </button>
        <button
          onClick={() => switchTab(true)}
          className={`px-4 py-2 rounded-xl ${history ? "bg-sky-500 text-white" : "bg-white border text-slate-600"}`}
        >
          구매 · 배송 기록
        </button>
      </div>
      {history ? (
        <HistoryTab />
      ) : (
        <>
          <RecommendationsPanel />
          <PriceTrendSection />
        </>
      )}
    </AppShell>
  );
}

function PriceTrendSection() {
  const { data: recommendations } = useQuery({
    queryKey: ["closing-recommendations", todayKorean()],
    queryFn: () => getRecommendations(todayKorean()),
  });
  const [selected, setSelected] = useState<number | undefined>();
  const currentId = selected ?? recommendations?.items[0]?.ingredientId;
  const { data, isLoading, isError } = usePriceTrend(currentId, 30);
  if (!recommendations?.items.length) return null;
  const points = (data?.points ?? [])
    .map((p) => ({
      date: p.date,
      price: p.wholesalePrice ?? p.retailPrice ?? 0,
    }))
    .filter((p) => p.price > 0);
  const last = data?.points[data.points.length - 1];
  return (
    <section className="mt-6 bg-white rounded-xl border border-slate-200 p-4">
      <div className="flex items-center justify-between flex-wrap gap-3 mb-4">
        <h2 className="font-semibold text-slate-800">공공 가격 추세</h2>
        <select
          aria-label="가격 추세 재료"
          value={currentId ?? ""}
          onChange={(e) => setSelected(Number(e.target.value))}
          className="border rounded-lg p-2"
        >
          {recommendations.items.map((i) => (
            <option key={i.ingredientId} value={i.ingredientId}>
              {i.ingredientName}
            </option>
          ))}
        </select>
      </div>
      {isLoading ? (
        <p>가격을 확인하고 있습니다…</p>
      ) : isError ? (
        <p className="text-slate-500">가격 추세를 불러오지 못했습니다.</p>
      ) : points.length ? (
        <PriceChart
          history={points}
          current={last?.wholesalePrice ?? last?.retailPrice ?? 0}
          monthly={last?.monthAvg ?? 0}
          weekly={last?.weekAvg ?? 0}
        />
      ) : (
        <p className="text-slate-500">아직 비교할 가격 자료가 없습니다.</p>
      )}
    </section>
  );
}

function HistoryTab() {
  const [params, setParams] = useSearchParams();
  const initial = defaultRange(30);
  const [from, setFrom] = useState(initial.from),
    [to, setTo] = useState(initial.to),
    [page, setPage] = useState(0);
  const deliveryValue = params.get("delivery");
  const delivery = ["WAITING", "RECEIVED", "LEGACY"].includes(
    deliveryValue ?? "",
  )
    ? (deliveryValue as DeliveryStatus)
    : undefined;
  const validRange = from !== "" && to !== "" && from <= to;
  const { data, isLoading, error, refetch } = usePurchaseOrders({
    from,
    to,
    deliveryStatus: delivery,
    page,
    size: 20,
  });
  const [receiving, setReceiving] = useState<PurchaseOrder | null>(null);
  const [success, setSuccess] = useState("");
  const exportMutation = useExportPurchaseOrders();
  useEffect(() => {
    setPage(0);
  }, [from, to, delivery]);
  const filter = (status?: DeliveryStatus) => {
    const next = new URLSearchParams(params);
    status ? next.set("delivery", status) : next.delete("delivery");
    setParams(next);
  };
  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border p-4 space-y-3">
        <p className="text-sm text-slate-600">
          구매 기록은 배송 대기로 저장됩니다. 실제 배송받은 뒤 수령 후 입고를
          눌러주세요.
        </p>
        <div className="flex flex-wrap gap-2">
          {(
            [
              [undefined, "전체"],
              ["WAITING", "배송 대기"],
              ["RECEIVED", "입고 완료"],
              ["LEGACY", "기존 기록"],
            ] as const
          ).map(([status, label]) => (
            <button
              key={label}
              onClick={() => filter(status)}
              className={`px-3 py-2 rounded-lg text-sm ${delivery === status ? "bg-sky-500 text-white" : "bg-slate-100 text-slate-600"}`}
            >
              {label}
            </button>
          ))}
        </div>
        <div className="flex flex-wrap gap-3 items-end">
          <label className="text-sm">
            시작일
            <input
              type="date"
              aria-label="구매 조회 시작일"
              value={from}
              max={to}
              onChange={(e) => setFrom(e.target.value)}
              className="block border rounded-lg p-2"
            />
          </label>
          <label className="text-sm">
            종료일
            <input
              type="date"
              aria-label="구매 조회 종료일"
              value={to}
              min={from}
              max={todayKorean()}
              onChange={(e) => setTo(e.target.value)}
              className="block border rounded-lg p-2"
            />
          </label>
          <button
            disabled={!validRange || exportMutation.isPending}
            onClick={() => exportMutation.mutate({ from, to })}
            className="p-2 rounded-lg border text-sm text-sky-600 disabled:opacity-50"
          >
            {exportMutation.isPending ? "다운로드 중…" : "구매 이력 엑셀"}
          </button>
        </div>
        <p className="text-xs text-slate-500">
          기간별 조회는 최대 365일을 지원합니다.
        </p>
      </div>
      {!validRange && (
        <p role="alert" className="text-red-700">
          조회 날짜를 확인해주세요.
        </p>
      )}
      {exportMutation.error && (
        <p role="alert" className="text-red-700">
          {exportMutation.error.message}
        </p>
      )}
      {success && (
        <p
          role="status"
          className="p-3 bg-emerald-50 text-emerald-800 rounded-xl"
        >
          {success}
        </p>
      )}
      {error ? (
        <p role="alert" className="bg-red-50 p-4 text-red-700 rounded-xl">
          {error.message}
          <button onClick={() => refetch()} className="ml-3 underline">
            다시 시도
          </button>
        </p>
      ) : isLoading ? (
        <p className="p-6 text-slate-500">구매 기록을 확인하고 있습니다…</p>
      ) : !data?.content.length ? (
        <p className="p-6 text-slate-500 text-center">
          이 기간에 해당하는 구매 기록이 없습니다.{" "}
          <Link to="/lowest-price" className="underline text-sky-600">
            가격 비교
          </Link>
        </p>
      ) : (
        <>
          <p className="text-sm text-slate-500">
            총 {data.totalElements}건 · {from} ~ {to}
          </p>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {data.content.map((order) => (
              <article
                key={order.id}
                data-testid={`purchase-${order.id}`}
                className="p-4 bg-white rounded-xl border border-slate-200 space-y-2"
              >
                <div className="flex justify-between gap-2">
                  <span className="text-sm text-slate-500">
                    {order.orderedAt} · {order.supplier}
                  </span>
                  <span
                    className={`text-xs px-2 py-1 rounded ${order.deliveryStatus === "WAITING" ? "bg-amber-50 text-amber-800" : order.deliveryStatus === "RECEIVED" ? "bg-emerald-50 text-emerald-800" : "bg-slate-100 text-slate-600"}`}
                  >
                    {deliveryLabel(order.deliveryStatus)}
                  </span>
                </div>
                <h3 className="font-semibold text-slate-800">
                  {order.productName || order.ingredientName}
                </h3>
                <p className="text-xs text-slate-500">
                  재료: {order.ingredientName}
                </p>
                <p className="text-sm text-slate-600">
                  {formatQuantity(order.quantity)} {order.baseUnit} ×{" "}
                  {formatQuantity(order.unitPrice)}원 / {order.baseUnit}
                </p>
                <p className="text-sky-700 font-semibold">
                  총 {formatQuantity(order.totalAmount)}원
                </p>
                {order.expectedQuantityBase != null && (
                  <p className="text-sm text-slate-600">
                    {order.deliveryStatus === "RECEIVED"
                      ? "입고 수량"
                      : "입고 예정"}{" "}
                    {formatQuantity(order.expectedQuantityBase)}{" "}
                    {order.inventoryUnit}
                  </p>
                )}
                {order.sourceUrl && (
                  <a
                    href={order.sourceUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-block text-sm text-sky-600 underline"
                  >
                    구매한 상품 보기
                  </a>
                )}
                {order.memo && (
                  <p className="text-xs text-slate-500 break-words">
                    {order.memo}
                  </p>
                )}
                {order.deliveryStatus === "WAITING" &&
                  order.expectedQuantityBase != null &&
                  order.status !== "CANCELLED" && (
                    <button
                      onClick={() => {
                        setSuccess("");
                        setReceiving(order);
                      }}
                      className="block mt-2 px-4 py-2.5 rounded-xl bg-sky-500 text-white text-sm"
                    >
                      수령 후 입고
                    </button>
                  )}
                {order.deliveryStatus === "RECEIVED" && (
                  <p className="text-xs text-emerald-700">
                    입고 처리 {order.receivedAt?.replace("T", " ")} · 재고 반영
                    완료
                  </p>
                )}
                {order.deliveryStatus === "LEGACY" && (
                  <p className="text-xs text-slate-500">
                    과거 입고 여부를 자동 판단할 수 없는 기존 기록입니다.
                  </p>
                )}
              </article>
            ))}
          </div>
          {data.totalPages > 1 && (
            <div className="flex justify-center items-center gap-3">
              <button
                disabled={data.first}
                onClick={() => setPage((p) => p - 1)}
                className="border rounded-lg p-2 disabled:opacity-50"
              >
                이전
              </button>
              <span>
                {data.number + 1} / {data.totalPages}
              </span>
              <button
                disabled={data.last}
                onClick={() => setPage((p) => p + 1)}
                className="border rounded-lg p-2 disabled:opacity-50"
              >
                다음
              </button>
            </div>
          )}
        </>
      )}
      {receiving && (
        <ReceiptDialog
          key={receiving.id}
          order={receiving}
          onClose={() => setReceiving(null)}
          onSaved={() => {
            setSuccess(
              "수령한 주문을 입고했습니다. 재고와 발주 판단이 갱신됩니다.",
            );
            setReceiving(null);
          }}
        />
      )}
    </div>
  );
}

function ReceiptDialog({
  order,
  onClose,
  onSaved,
}: {
  order: PurchaseOrder;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [inboundDate, setInboundDate] = useState(todayKorean());
  const [expirationDate, setExpirationDate] = useState("");
  const mutation = useReceivePurchaseOrder();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!expirationDate || order.expectedQuantityBase == null) return;
    try {
      await mutation.mutateAsync({
        id: order.id,
        payload: {
          receivedQuantity: order.expectedQuantityBase,
          inboundDate,
          expirationDate,
        },
      });
      onSaved();
    } catch {
      /* Show server error in the dialog. */
    }
  };
  return (
    <div
      className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4"
      onClick={() => {
        if (!mutation.isPending) onClose();
      }}
    >
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="receive-title"
        onSubmit={submit}
        onClick={(e) => e.stopPropagation()}
        className="p-6 bg-white rounded-2xl w-full max-w-md space-y-4"
      >
        <h2 id="receive-title" className="text-xl font-semibold">
          배송 수령 · 입고
        </h2>
        <p className="text-slate-600 text-sm">
          {order.productName || order.ingredientName}
        </p>
        <p className="bg-sky-50 p-3 rounded-xl text-sm text-sky-800">
          입고 수량 {formatQuantity(order.expectedQuantityBase ?? 0)}{" "}
          {order.inventoryUnit}
          <br />
          등록한 주문 전체 수령을 처리합니다.
        </p>
        <label className="block text-sm">
          입고일
          <input
            type="date"
            value={inboundDate}
            min={order.orderedAt}
            max={todayKorean()}
            onChange={(e) => setInboundDate(e.target.value)}
            required
            className="mt-1 w-full p-3 border rounded-xl"
          />
        </label>
        <label className="block text-sm">
          유통기한
          <input
            type="date"
            value={expirationDate}
            min={todayKorean()}
            onChange={(e) => setExpirationDate(e.target.value)}
            required
            className="mt-1 w-full p-3 border rounded-xl"
          />
        </label>
        {mutation.error && (
          <p
            role="alert"
            className="bg-red-50 text-red-700 p-3 rounded-xl text-sm"
          >
            {mutation.error.message}
          </p>
        )}
        <div className="flex gap-3">
          <button
            type="button"
            onClick={onClose}
            disabled={mutation.isPending}
            className="flex-1 border rounded-xl p-3"
          >
            닫기
          </button>
          <button
            data-testid="receive-confirm"
            type="submit"
            disabled={mutation.isPending}
            className="flex-1 rounded-xl p-3 bg-sky-500 text-white disabled:opacity-50"
          >
            {mutation.isPending ? "입고 중…" : "입고 완료"}
          </button>
        </div>
      </form>
    </div>
  );
}
function deliveryLabel(status: DeliveryStatus): string {
  return status === "WAITING"
    ? "배송 대기"
    : status === "RECEIVED"
      ? "입고 완료"
      : "기존 기록";
}
