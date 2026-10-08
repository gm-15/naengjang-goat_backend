import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { getDailyReport } from "../api/workflow";
import { todayKorean } from "../lib/workflow";
import { motion, AnimatePresence } from "motion/react";
import { AppShell } from "../components/common/AppShell";
import { PageHeader } from "../components/common/PageHeader";
import { GradientButton } from "../components/common/GradientButton";
import { CardSkeleton } from "../components/common/LoadingState";
import { EmptyState } from "../components/common/EmptyState";
import { useBatches, useCreateBatch } from "../hooks/useInventoryBatches";
interface InventoryIngredient {
  ingredientId: number;
  name: string;
  unit: string;
}
import type { InventoryBatch } from "../types/inventory";

export default function InventoryPage() {
  const {
    data: report,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ["daily-report", todayKorean()],
    queryFn: () => getDailyReport(todayKorean()),
  });
  const ingredients: InventoryIngredient[] = (
    report?.currentInventory ?? []
  ).map((item) => ({
    ingredientId: item.ingredientId,
    name: item.ingredientName,
    unit: item.baseUnit,
  }));
  const [showAddModal, setShowAddModal] = useState(false);
  const [detailItem, setDetailItem] = useState<InventoryIngredient | null>(
    null,
  );

  return (
    <AppShell variant="main">
      <PageHeader
        title="재고 관리"
        description="재료별 잔여 재고 · 초기 재고와 별도 입고 등록"
        backTo="/main"
        right={
          <GradientButton
            fullWidth={false}
            onClick={() => setShowAddModal(true)}
          >
            + 재고 입고
          </GradientButton>
        }
      />

      <p className="mb-4 p-3 bg-sky-50 rounded-xl text-sm text-slate-600">
        배송 대기 주문은{" "}
        <Link
          to={"/order?tab=history&delivery=WAITING"}
          className="text-sky-600 underline"
        >
          발주 · 배송 관리
        </Link>
        에서 수령 후 입고하세요. 이 화면에서는 초기 재고와 구매 기록 없는 입고를
        등록합니다.
      </p>
      {error ? (
        <p role="alert" className="text-red-700 p-4">
          {error.message}
          <button onClick={() => refetch()} className="ml-3 underline">
            다시 시도
          </button>
        </p>
      ) : isLoading ? (
        <CardSkeleton count={6} heightClass="h-36" />
      ) : ingredients.length === 0 ? (
        <EmptyState
          title="등록된 재료가 없습니다"
          description="매장 카테고리를 선택한 뒤 메뉴와 레시피를 확인해주세요."
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
          {ingredients.map((item, index) => (
            <StockCard
              key={item.ingredientId}
              item={item}
              index={index}
              onClick={() => setDetailItem(item)}
            />
          ))}
        </div>
      )}

      {/* Add batch modal */}
      <AnimatePresence>
        {showAddModal && (
          <AddBatchModal
            ingredients={ingredients}
            onClose={() => setShowAddModal(false)}
          />
        )}
      </AnimatePresence>

      {/* Batch detail modal */}
      <AnimatePresence>
        {detailItem && (
          <BatchDetailModal
            item={detailItem}
            onClose={() => setDetailItem(null)}
          />
        )}
      </AnimatePresence>
    </AppShell>
  );
}

// ────────── StockCard ──────────

function StockCard({
  item,
  index,
  onClick,
}: {
  item: InventoryIngredient;
  index: number;
  onClick: () => void;
}) {
  const { data: batches = [], isLoading } = useBatches(item.ingredientId);

  const totalQuantity = useMemo(
    () => batches.reduce((sum, b) => sum + Number(b.quantity), 0),
    [batches],
  );

  const nearestExpiry = useMemo(() => {
    if (batches.length === 0) return null;
    return [...batches].sort((a, b) => (a.expiresAt < b.expiresAt ? -1 : 1))[0]
      ?.expiresAt;
  }, [batches]);

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.03 * index }}
      onClick={onClick}
      className="bg-white rounded-xl p-4 border-2 border-[#e2e8f0] hover:border-[#0EA5E9] hover:shadow-md transition-all cursor-pointer group relative"
    >
      <div className="flex items-start justify-between mb-3">
        <h3 className="font-semibold text-[#1e293b] truncate">{item.name}</h3>
        {batches.length > 0 && (
          <span className="text-xs px-2 py-0.5 rounded-md bg-[#F0F9FF] text-[#0EA5E9] flex-shrink-0 ml-2">
            배치 {batches.length}건
          </span>
        )}
      </div>

      {isLoading ? (
        <div className="h-12 bg-[#f1f5f9] rounded-md animate-pulse" />
      ) : (
        <>
          <div className="flex items-baseline gap-1 mb-2">
            <span
              className={`text-2xl font-bold ${
                totalQuantity > 0 ? "text-[#0EA5E9]" : "text-[#94a3b8]"
              }`}
            >
              {formatQuantity(totalQuantity)}
            </span>
            <span className="text-sm text-[#94a3b8]">{unitOf(item)}</span>
            {bigUnitHint(totalQuantity, item.unit) && (
              <span className="text-xs text-[#94a3b8] ml-1">
                ({bigUnitHint(totalQuantity, item.unit)})
              </span>
            )}
          </div>

          <div className="text-xs text-[#64748b] pt-2 border-t border-[#e2e8f0]">
            {nearestExpiry
              ? `가장 가까운 유통기한: ${nearestExpiry}`
              : "재고 없음 — 입고가 필요합니다"}
          </div>
        </>
      )}

      <svg
        className="absolute right-4 bottom-4 w-5 h-5 text-[#e2e8f0] group-hover:text-[#0EA5E9] transition-colors"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M9 5l7 7-7 7"
        />
      </svg>
    </motion.div>
  );
}

// ────────── BatchDetailModal ──────────

function BatchDetailModal({
  item,
  onClose,
}: {
  item: InventoryIngredient;
  onClose: () => void;
}) {
  const { data: batches = [], isLoading } = useBatches(item.ingredientId);

  const totalQuantity = batches.reduce((s, b) => s + Number(b.quantity), 0);

  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-end sm:items-center justify-center z-50 sm:px-6 sm:py-8 overflow-y-auto"
      onClick={onClose}
    >
      <motion.div
        initial={{ y: 40, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        exit={{ y: 40, opacity: 0 }}
        onClick={(e) => e.stopPropagation()}
        className="bg-white rounded-t-3xl sm:rounded-2xl w-full sm:max-w-md p-6 pb-[calc(1.5rem+env(safe-area-inset-bottom))] sm:pb-6 max-h-[90dvh] overflow-y-auto"
      >
        <h2 className="text-xl font-semibold text-[#1e293b] mb-1">
          {item.name}
        </h2>
        <p className="text-sm text-[#64748b] mb-5">
          배치별 잔여 재고 (FIFO 순)
        </p>

        {isLoading ? (
          <div className="h-32 bg-[#f1f5f9] rounded-xl animate-pulse" />
        ) : batches.length === 0 ? (
          <div className="text-center py-8 text-[#94a3b8]">
            등록된 배치가 없습니다
          </div>
        ) : (
          <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
            {batches.map((b, i) => (
              <BatchRow
                key={b.batchId}
                batch={b}
                index={i}
                unit={unitOf(item)}
              />
            ))}
          </div>
        )}

        <div className="mt-5 pt-4 border-t border-[#e2e8f0] flex items-center justify-between">
          <span className="text-sm text-[#64748b]">
            합계 ({batches.length}건)
          </span>
          <span className="text-lg font-semibold text-[#0EA5E9]">
            {formatQuantity(totalQuantity)} {unitOf(item)}
          </span>
        </div>

        <div className="mt-5">
          <GradientButton variant="outline" onClick={onClose}>
            닫기
          </GradientButton>
        </div>
      </motion.div>
    </div>
  );
}

function BatchRow({
  batch,
  index,
  unit,
}: {
  batch: InventoryBatch;
  index: number;
  unit: string;
}) {
  const expired = batch.expiresAt < todayKorean();
  const isExpiringSoon = !expired && isWithinDays(batch.expiresAt, 7);

  return (
    <div
      className={`flex items-center justify-between p-3 rounded-lg border ${
        expired
          ? "border-red-200 bg-red-50"
          : isExpiringSoon
            ? "border-amber-200 bg-amber-50"
            : "border-[#e2e8f0] bg-white"
      }`}
    >
      <div className="flex items-center gap-3">
        <span className="w-7 h-7 rounded-full bg-gradient-to-br from-[#0EA5E9] to-[#38BDF8] text-white text-xs font-bold flex items-center justify-center">
          {index + 1}
        </span>
        <div>
          <div className="text-sm font-semibold text-[#1e293b]">
            {formatQuantity(Number(batch.quantity))} {unit}
          </div>
          <div
            className={`text-xs ${
              expired
                ? "text-red-700 font-medium"
                : isExpiringSoon
                  ? "text-amber-700 font-medium"
                  : "text-[#94a3b8]"
            }`}
          >
            유통기한 {batch.expiresAt}
            {expired && " · 기한 지남"}
            {isExpiringSoon && " ⚠ 임박"}
          </div>
        </div>
      </div>
      <span className="text-xs text-[#94a3b8]">#{batch.batchId}</span>
    </div>
  );
}

// ────────── AddBatchModal ──────────

function AddBatchModal({
  ingredients,
  onClose,
}: {
  ingredients: InventoryIngredient[];
  onClose: () => void;
}) {
  const createMutation = useCreateBatch();
  const [selectedId, setSelectedId] = useState<number | "">(
    ingredients[0]?.ingredientId ?? "",
  );
  const [quantity, setQuantity] = useState<string>("");
  const [costPerUnit, setCostPerUnit] = useState<string>("");
  const [inboundDate, setInboundDate] = useState<string>(todayISO());
  const [expirationDate, setExpirationDate] = useState<string>("");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    if (!selectedId) {
      setErrorMsg("재료를 선택해주세요.");
      return;
    }
    const qty = Number.parseFloat(quantity);
    if (!qty || qty <= 0) {
      setErrorMsg("수량을 0보다 큰 값으로 입력해주세요.");
      return;
    }
    if (
      costPerUnit &&
      (!Number.isFinite(Number(costPerUnit)) || Number(costPerUnit) < 0)
    ) {
      setErrorMsg("재고 단위당 단가를 확인해주세요.");
      return;
    }
    if (!expirationDate) {
      setErrorMsg("유통기한을 입력해주세요.");
      return;
    }
    try {
      await createMutation.mutateAsync({
        ingredientId: Number(selectedId),
        quantity: qty,
        costPerUnit: costPerUnit ? Number(costPerUnit) : undefined,
        inboundDate: inboundDate || undefined,
        expirationDate,
      });
      onClose();
    } catch (err) {
      setErrorMsg(
        err instanceof Error
          ? `입고 실패: ${err.message}`
          : "입고에 실패했습니다.",
      );
    }
  };

  const inputClass =
    "w-full px-4 py-3 rounded-xl bg-white border-2 border-[#e2e8f0] focus:border-[#0EA5E9] focus:outline-none transition-colors";

  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-end sm:items-center justify-center z-50 sm:px-6 sm:py-8 overflow-y-auto"
      onClick={onClose}
    >
      <motion.div
        initial={{ y: 40, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        exit={{ y: 40, opacity: 0 }}
        onClick={(e) => e.stopPropagation()}
        className="bg-white rounded-t-3xl sm:rounded-2xl w-full sm:max-w-md p-6 pb-[calc(1.5rem+env(safe-area-inset-bottom))] sm:pb-6 max-h-[90dvh] overflow-y-auto"
      >
        <div className="w-14 h-14 mx-auto mb-3 rounded-full bg-gradient-to-br from-[#0EA5E9] to-[#38BDF8] flex items-center justify-center text-2xl">
          📦
        </div>
        <h2 className="text-xl font-semibold text-[#1e293b] text-center mb-1">
          재고 입고
        </h2>
        <p className="text-sm text-[#64748b] text-center mb-5">
          입고된 재료를 배치로 등록합니다
        </p>

        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="block text-sm text-[#334155] mb-1.5">
              재료 *
            </label>
            <select
              value={selectedId}
              onChange={(e) =>
                setSelectedId(e.target.value ? Number(e.target.value) : "")
              }
              className={inputClass}
              required
            >
              {ingredients.map((i) => (
                <option key={i.ingredientId} value={i.ingredientId}>
                  {i.name}
                </option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm text-[#334155] mb-1.5">
                수량 *
              </label>
              <div className="flex items-center gap-2">
                <input
                  type="number"
                  min={0.001}
                  step={0.001}
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                  placeholder="10"
                  className={inputClass}
                  required
                />
                <span className="text-[#64748b] font-medium">
                  {unitOf(
                    ingredients.find((i) => i.ingredientId === selectedId),
                  )}
                </span>
              </div>
            </div>
            <div>
              <label className="block text-sm text-[#334155] mb-1.5">
                단가
              </label>
              <div className="flex items-center gap-2">
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  value={costPerUnit}
                  onChange={(e) => setCostPerUnit(e.target.value)}
                  placeholder="(선택)"
                  className={inputClass}
                />
                <span className="text-[#64748b] font-medium text-sm">
                  원 /{" "}
                  {unitOf(
                    ingredients.find((i) => i.ingredientId === selectedId),
                  )}
                </span>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm text-[#334155] mb-1.5">
                입고일
              </label>
              <input
                type="date"
                max={todayKorean()}
                value={inboundDate}
                onChange={(e) => setInboundDate(e.target.value)}
                className={inputClass}
              />
            </div>
            <div>
              <label className="block text-sm text-[#334155] mb-1.5">
                유통기한 *
              </label>
              <input
                type="date"
                min={todayKorean()}
                value={expirationDate}
                onChange={(e) => setExpirationDate(e.target.value)}
                className={inputClass}
                required
              />
            </div>
          </div>

          {errorMsg && (
            <div className="px-3 py-2 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
              {errorMsg}
            </div>
          )}

          <div className="flex gap-3 pt-2">
            <GradientButton
              variant="outline"
              onClick={onClose}
              disabled={createMutation.isPending}
            >
              취소
            </GradientButton>
            <GradientButton type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? "등록 중..." : "입고 등록"}
            </GradientButton>
          </div>
        </form>
      </motion.div>
    </div>
  );
}

// ────────── helpers ──────────

/** 재료 기본 단위 (g · ml · 개). 배치 수량은 이 단위로 저장된다. */
function unitOf(item: InventoryIngredient | undefined): string {
  return item?.unit || "";
}

/** 5000 g → "5 kg", 1500 ml → "1.5 L" 보조 표기 */
function bigUnitHint(
  qty: number,
  unit: string | null | undefined,
): string | null {
  if (qty < 1000) return null;
  if (unit === "g") return `${formatQuantity(qty / 1000)} kg`;
  if (unit === "ml") return `${formatQuantity(qty / 1000)} L`;
  return null;
}

function formatQuantity(value: number): string {
  if (Number.isInteger(value)) return value.toString();
  return value.toLocaleString("ko-KR", { maximumFractionDigits: 3 });
}

function todayISO(): string {
  return todayKorean();
}

function isWithinDays(dateStr: string, days: number): boolean {
  try {
    const target = new Date(dateStr);
    const today = new Date();
    const diff = (target.getTime() - today.getTime()) / (1000 * 60 * 60 * 24);
    return diff <= days;
  } catch {
    return false;
  }
}
