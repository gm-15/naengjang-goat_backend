import { apiClient } from "./client";
import type { CreateBatchPayload, InventoryBatch } from "../types/inventory";

/**
 * GET /ingredients/{ingredientId}/batches — 재료별 잔여 배치 목록 (FIFO 정렬, quantity > 0).
 */
export async function listBatches(
  ingredientId: number,
): Promise<InventoryBatch[]> {
  return apiClient.get<InventoryBatch[]>(
    `/ingredients/${ingredientId}/batches`,
  );
}

/**
 * POST /inventory/batches — 신규 배치 입고.
 */
export async function createBatch(
  payload: CreateBatchPayload,
): Promise<InventoryBatch> {
  return apiClient.post<InventoryBatch>("/inventory/batches", payload);
}
