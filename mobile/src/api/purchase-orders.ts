import { Platform } from "react-native";
import { File, Paths } from "expo-file-system";
import * as Sharing from "expo-sharing";
import { apiClient, authHeaders, ApiError } from "./client";
import { getApiBaseUrl } from "../lib/config";
import type {
  CreatePurchaseOrderPayload,
  PageResponse,
  PurchaseOrder,
  DeliveryStatus,
  ReceivePurchaseOrderPayload,
} from "../types/order";

export interface ListPurchaseOrdersParams {
  from: string;
  to: string;
  ingredientId?: number;
  status?: "PENDING" | "CONFIRMED" | "CANCELLED";
  deliveryStatus?: DeliveryStatus;
  page?: number;
  size?: number;
}

export async function listPurchaseOrders(
  params: ListPurchaseOrdersParams,
): Promise<PageResponse<PurchaseOrder>> {
  const qs: string[] = [`from=${params.from}`, `to=${params.to}`];
  if (params.ingredientId !== undefined) qs.push(`ingredientId=${params.ingredientId}`);
  if (params.status) qs.push(`status=${params.status}`);
  if (params.deliveryStatus) qs.push(`deliveryStatus=${params.deliveryStatus}`);
  if (params.page !== undefined) qs.push(`page=${params.page}`);
  if (params.size !== undefined) qs.push(`size=${params.size}`);
  return apiClient.get<PageResponse<PurchaseOrder>>(`/purchase-orders?${qs.join("&")}`);
}

export async function createPurchaseOrder(
  payload: CreatePurchaseOrderPayload,
): Promise<PurchaseOrder> {
  return apiClient.post<PurchaseOrder>("/purchase-orders", payload);
}

export async function receivePurchaseOrder(
  id: number,
  payload: ReceivePurchaseOrderPayload,
): Promise<PurchaseOrder> {
  return apiClient.post<PurchaseOrder>(`/purchase-orders/${id}/receive`, payload);
}

/**
 * GET /purchase-orders/export → .xlsx 를 캐시에 받은 뒤 공유 시트로 넘긴다
 * (카톡·드라이브·파일 앱 등으로 저장/전송).
 */
export async function exportPurchaseOrders(from: string, to: string): Promise<void> {
  const url = `${getApiBaseUrl()}/purchase-orders/export?from=${from}&to=${to}`;
  const filename = `purchase_orders_${from}_${to}.xlsx`;

  if (Platform.OS === "web") {
    const res = await fetch(url, { headers: authHeaders() });
    if (!res.ok) throw new ApiError(res.status, `API ${res.status}`);
    const blob = await res.blob();
    const href = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = href;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(href);
    return;
  }

  const dest = new File(Paths.cache, filename);
  if (dest.exists) dest.delete();
  const file = await File.downloadFileAsync(url, dest, { headers: authHeaders() });
  if (await Sharing.isAvailableAsync()) {
    await Sharing.shareAsync(file.uri, {
      mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      dialogTitle: "발주 기록 엑셀",
    });
  }
}

export interface PurchaseOrderSummary {
  totalCount: number;
  totalAmount: number;
  byIngredient: Array<{
    ingredientName: string;
    count: number;
    totalAmount: number;
  }>;
}

export async function getPurchaseSummary(
  from: string,
  to: string,
): Promise<PurchaseOrderSummary> {
  return apiClient.get<PurchaseOrderSummary>(`/purchase-orders/summary?from=${from}&to=${to}`);
}
