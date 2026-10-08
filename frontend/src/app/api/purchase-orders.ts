import { apiClient, downloadBlob, fetchBlob } from "./client";
import type {
  CreatePurchaseOrderPayload,
  PageResponse,
  PurchaseOrder,
  ReceivePurchasePayload,
  DeliveryStatus,
} from "../types/order";

export interface ListPurchaseOrdersParams {
  from: string; // "YYYY-MM-DD" 필수
  to: string; // "YYYY-MM-DD" 필수
  ingredientId?: number;
  status?: "PENDING" | "CONFIRMED" | "CANCELLED";
  deliveryStatus?: DeliveryStatus;
  page?: number;
  size?: number;
}

/**
 * GET /purchase-orders — 발주 목록 조회 (인증 필요).
 * from/to 는 필수 쿼리 파라미터.
 */
export async function listPurchaseOrders(
  params: ListPurchaseOrdersParams,
): Promise<PageResponse<PurchaseOrder>> {
  const qs = new URLSearchParams();
  qs.set("from", params.from);
  qs.set("to", params.to);
  if (params.ingredientId !== undefined) {
    qs.set("ingredientId", String(params.ingredientId));
  }
  if (params.status) qs.set("status", params.status);
  if (params.deliveryStatus) qs.set("deliveryStatus", params.deliveryStatus);
  if (params.page !== undefined) qs.set("page", String(params.page));
  if (params.size !== undefined) qs.set("size", String(params.size));

  return apiClient.get<PageResponse<PurchaseOrder>>(
    `/purchase-orders?${qs.toString()}`,
  );
}

/**
 * POST /purchase-orders — 발주 등록 (인증 필요).
 * orderedAt null 이면 백엔드가 오늘 날짜로 채움.
 * totalAmount 는 quantity × unitPrice 로 백엔드 계산.
 */
export async function createPurchaseOrder(
  payload: CreatePurchaseOrderPayload,
): Promise<PurchaseOrder> {
  return apiClient.post<PurchaseOrder>("/purchase-orders", payload);
}

export function receivePurchaseOrder(
  id: number,
  payload: ReceivePurchasePayload,
): Promise<PurchaseOrder> {
  return apiClient.post<PurchaseOrder>(
    `/purchase-orders/${id}/receive`,
    payload,
  );
}

/**
 * GET /purchase-orders/export — 기간별 발주서 Excel 다운로드 (인증 필요).
 * 클라이언트는 Blob 으로 받아 파일 다운로드 처리.
 */
export async function exportPurchaseOrders(
  from: string,
  to: string,
): Promise<void> {
  const blob = await fetchBlob(`/purchase-orders/export?from=${from}&to=${to}`);
  downloadBlob(blob, `purchase_orders_${from}_${to}.xlsx`);
}

// ────────── Summary ──────────

export interface PurchaseOrderSummary {
  totalCount: number;
  totalAmount: number;
  byIngredient: Array<{
    ingredientName: string;
    count: number;
    totalAmount: number;
  }>;
}

/**
 * GET /purchase-orders/summary — 기간별 발주 집계 (인증 필요).
 * CANCELLED 발주는 제외, byIngredient 는 totalAmount 내림차순.
 */
export async function getPurchaseSummary(
  from: string,
  to: string,
): Promise<PurchaseOrderSummary> {
  return apiClient.get<PurchaseOrderSummary>(
    `/purchase-orders/summary?from=${from}&to=${to}`,
  );
}
