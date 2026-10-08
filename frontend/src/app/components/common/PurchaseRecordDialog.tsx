import { useState } from "react";
import type { PriceDetail } from "../../types/ingredient";
import { useCreatePurchaseOrder } from "../../hooks/usePurchaseOrders";
import {
  allowedUnits,
  formatQuantity,
  quantityInBase,
} from "../../lib/workflow";

export interface VisitedSource {
  source: string;
  sourceLabel: string;
  productName: string;
  price: number;
  sourceUrl?: string;
  weightGrams?: number | null;
}
const fieldClass =
  "w-full px-3 py-2.5 rounded-xl border-2 border-[#e2e8f0] focus:border-[#0EA5E9] focus:outline-none";

export function PurchaseRecordDialog({
  data,
  source,
  onClose,
  onSaved,
}: {
  data: PriceDetail;
  source: VisitedSource;
  onClose: () => void;
  onSaved: () => void;
}) {
  const units = allowedUnits(data.unit);
  const knownWeight =
    source.weightGrams != null &&
    source.weightGrams > 0 &&
    ["g", "kg"].includes(data.unit);
  const [productName, setProductName] = useState(
    source.productName || data.name,
  );
  const [sourceUrl, setSourceUrl] = useState(source.sourceUrl ?? "");
  const [quantity, setQuantity] = useState("1");
  const [unit, setUnit] = useState("포장");
  const [packageSize, setPackageSize] = useState(
    knownWeight ? String(source.weightGrams) : "",
  );
  const [packageUnit, setPackageUnit] = useState(units[0]);
  const [unitPrice, setUnitPrice] = useState(
    source.price > 0 ? String(source.price) : "",
  );
  const [supplier, setSupplier] = useState(
    source.sourceLabel === "직접 입력" ? "" : source.sourceLabel,
  );
  const [memo, setMemo] = useState("");
  const [error, setError] = useState("");
  const mutation = useCreatePurchaseOrder();
  const qty = Number(quantity),
    price = Number(unitPrice),
    pack = Number(packageSize);
  const baseQuantity =
    unit === "포장"
      ? quantityInBase(qty * pack, packageUnit, data.unit)
      : quantityInBase(qty, unit, data.unit);
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError("");
    if (
      !Number.isFinite(qty) ||
      qty <= 0 ||
      qty > 9999999.999 ||
      !Number.isFinite(price) ||
      unitPrice.trim() === "" ||
      price < 0 ||
      price > 99999999.99
    ) {
      setError("구매 수량과 실제 단가를 확인해주세요.");
      return;
    }
    if (
      unit === "포장" &&
      (!Number.isInteger(qty) || !Number.isFinite(pack) || pack <= 0)
    ) {
      setError("포장 수량은 정수로, 포장당 내용량은 0보다 크게 입력해주세요.");
      return;
    }
    if (!productName.trim() || !supplier.trim()) {
      setError("상품명과 공급자를 입력해주세요.");
      return;
    }
    if (
      !Number.isFinite(baseQuantity) ||
      baseQuantity <= 0 ||
      baseQuantity > 9999999.999
    ) {
      setError("재고로 환산되는 총 수량을 확인해주세요.");
      return;
    }
    try {
      await mutation.mutateAsync({
        ingredientId: data.ingredientId,
        quantity: qty,
        baseUnit: unit,
        unitPrice: price,
        supplier: supplier.trim(),
        productName: productName.trim(),
        sourceUrl: sourceUrl.trim() || undefined,
        ...(unit === "포장" ? { packageSize: pack, packageUnit } : {}),
        memo: memo.trim() || undefined,
      });
      onSaved();
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "구매 기록을 저장하지 못했습니다.",
      );
    }
  };
  return (
    <div
      className="fixed inset-0 bg-black/50 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4"
      onClick={() => {
        if (!mutation.isPending) onClose();
      }}
    >
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="purchase-heading"
        onSubmit={submit}
        onClick={(e) => e.stopPropagation()}
        className="bg-white rounded-t-3xl sm:rounded-2xl w-full max-w-lg max-h-[90dvh] overflow-y-auto p-6 space-y-4"
      >
        <h2
          id="purchase-heading"
          className="text-xl font-semibold text-slate-800"
        >
          실제 구매 기록
        </h2>
        <p className="text-sm text-slate-600">
          외부 사이트에서 주문한 상품과 수량을 기록하세요. 배송 대기로 저장되며,
          수령 후 입고할 때 재고가 늘어납니다.
        </p>
        <p className="text-sm text-sky-700">
          연결 재료: {data.name} · 재고 단위 {data.unit}
        </p>
        <label className="block text-sm">
          상품명
          <input
            data-testid="product-name"
            value={productName}
            onChange={(e) => setProductName(e.target.value)}
            maxLength={255}
            required
            className={fieldClass}
          />
        </label>
        <label className="block text-sm">
          상품 링크
          <input
            data-testid="source-url"
            type="url"
            value={sourceUrl}
            onChange={(e) => setSourceUrl(e.target.value)}
            maxLength={2048}
            placeholder="https://… (선택)"
            className={fieldClass}
          />
        </label>
        <div className="grid grid-cols-2 gap-3">
          <label className="block text-sm">
            구매 수량
            <input
              data-testid="purchase-quantity"
              type="number"
              min={unit === "포장" ? "1" : "0.001"}
              step={unit === "포장" ? "1" : "0.001"}
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              required
              className={fieldClass}
            />
          </label>
          <label className="block text-sm">
            구매 단위
            <select
              data-testid="purchase-unit"
              value={unit}
              onChange={(e) => {
                setUnit(e.target.value);
                setUnitPrice("");
              }}
              className={fieldClass}
            >
              {[...units, "포장"].map((u) => (
                <option key={u}>{u}</option>
              ))}
            </select>
          </label>
        </div>
        {unit === "포장" && (
          <div className="grid grid-cols-2 gap-3">
            <label className="block text-sm">
              포장당 내용량
              <input
                data-testid="package-size"
                type="number"
                min="0.001"
                step="0.001"
                value={packageSize}
                onChange={(e) => setPackageSize(e.target.value)}
                required
                className={fieldClass}
              />
            </label>
            <label className="block text-sm">
              내용량 단위
              <select
                data-testid="package-unit"
                value={packageUnit}
                onChange={(e) => setPackageUnit(e.target.value)}
                className={fieldClass}
              >
                {units.map((u) => (
                  <option key={u}>{u}</option>
                ))}
              </select>
            </label>
          </div>
        )}
        <label className="block text-sm">
          실제 단가 (원 / {unit})
          <input
            data-testid="purchase-price"
            type="number"
            min="0"
            step="0.01"
            value={unitPrice}
            onChange={(e) => setUnitPrice(e.target.value)}
            required
            className={fieldClass}
          />
        </label>
        <p className="text-xs text-slate-500">
          사이트의 표시 가격과 실제 결제 가격이 다르면 수정하세요. 포장 상품은
          포장 1개 가격을 입력하세요.
        </p>
        <div className="bg-sky-50 rounded-xl p-3 text-sm text-sky-800">
          총 구매 금액:{" "}
          {Number.isFinite(qty * price) ? formatQuantity(qty * price) : "—"}원
          <br />
          입고 예정 재고:{" "}
          {Number.isFinite(baseQuantity) && baseQuantity > 0
            ? formatQuantity(baseQuantity)
            : "—"}{" "}
          {data.unit}
        </div>
        <label className="block text-sm">
          공급자
          <input
            data-testid="purchase-supplier"
            value={supplier}
            onChange={(e) => setSupplier(e.target.value)}
            maxLength={100}
            required
            className={fieldClass}
          />
        </label>
        <label className="block text-sm">
          메모
          <textarea
            value={memo}
            onChange={(e) => setMemo(e.target.value)}
            maxLength={4096}
            rows={2}
            className={fieldClass}
          />
        </label>
        {error && (
          <p
            role="alert"
            className="text-sm text-red-700 bg-red-50 p-3 rounded-xl"
          >
            {error}
          </p>
        )}
        <div className="flex gap-3">
          <button
            type="button"
            disabled={mutation.isPending}
            onClick={onClose}
            className="flex-1 py-3 border rounded-xl"
          >
            구매하지 않았어요
          </button>
          <button
            data-testid="purchase-confirm"
            type="submit"
            disabled={mutation.isPending}
            className="flex-1 py-3 bg-sky-500 text-white rounded-xl disabled:opacity-50"
          >
            {mutation.isPending ? "저장 중…" : "배송 대기로 저장"}
          </button>
        </div>
      </form>
    </div>
  );
}
