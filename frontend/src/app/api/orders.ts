import { apiClient } from "./client";
import type { LowStockItem } from "../types/order";

/**
 * GET /ingredients/low-stock — 재고 부족 Top N (인증 필요)
 * 정렬: stockRatio 오름차순 (위험한 항목 먼저).
 */
export async function getLowStock(limit = 5): Promise<LowStockItem[]> {
  return apiClient.get<LowStockItem[]>(
    `/ingredients/low-stock?limit=${limit}`,
  );
}
