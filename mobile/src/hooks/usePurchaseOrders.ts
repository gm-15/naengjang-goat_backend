import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import {
  createPurchaseOrder,
  exportPurchaseOrders,
  getPurchaseSummary,
  listPurchaseOrders,
  receivePurchaseOrder,
  type ListPurchaseOrdersParams,
} from "../api/purchase-orders";
import type { CreatePurchaseOrderPayload, ReceivePurchaseOrderPayload } from "../types/order";

const PURCHASE_ORDERS_KEY = ["purchase-orders"] as const;

/** 최근 N일 자동 from/to 계산 */
export function defaultRange(daysBack = 30): { from: string; to: string } {
  const today = new Date();
  const past = new Date();
  past.setDate(today.getDate() - daysBack);
  const fmt = (d: Date) => {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${y}-${m}-${day}`;
  };
  return { from: fmt(past), to: fmt(today) };
}

export function usePurchaseOrders(params: ListPurchaseOrdersParams) {
  return useQuery({
    queryKey: [...PURCHASE_ORDERS_KEY, params],
    queryFn: () => listPurchaseOrders(params),
  });
}

export function useCreatePurchaseOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreatePurchaseOrderPayload) =>
      createPurchaseOrder(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: PURCHASE_ORDERS_KEY });
      qc.invalidateQueries({ queryKey: ["purchase-summary"] });
    },
  });
}

export function useReceivePurchaseOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: ReceivePurchaseOrderPayload }) =>
      receivePurchaseOrder(id, payload),
    onSuccess: () => {
      for (const key of ["purchase-orders", "purchase-summary", "batches", "inventory-batches", "low-stock", "inventory", "ingredients", "daily-report", "closing-recommendations"])
        qc.invalidateQueries({ queryKey: [key] });
    },
  });
}

export function useExportPurchaseOrders() {
  return useMutation({
    mutationFn: ({ from, to }: { from: string; to: string }) =>
      exportPurchaseOrders(from, to),
  });
}

export function usePurchaseSummary(from: string, to: string) {
  return useQuery({
    queryKey: ["purchase-summary", from, to],
    queryFn: () => getPurchaseSummary(from, to),
  });
}

/** 이번 달 from/to 계산 (1일 ~ 오늘) */
export function thisMonthRange(): { from: string; to: string } {
  const now = new Date();
  const first = new Date(now.getFullYear(), now.getMonth(), 1);
  const fmt = (d: Date) => {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${y}-${m}-${day}`;
  };
  return { from: fmt(first), to: fmt(now) };
}
