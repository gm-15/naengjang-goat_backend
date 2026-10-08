import { apiClient } from "./client";
import type {
  LowestTopItem,
  PriceDetail,
  PriceTrend,
} from "../types/ingredient";

/**
 * GET /prices/lowest-top — 최저가 재료 Top N (인증 필요)
 */
export async function listLowestTop(limit = 5): Promise<LowestTopItem[]> {
  return apiClient.get<LowestTopItem[]>(`/prices/lowest-top?limit=${limit}`);
}

/**
 * GET /prices/{ingredientId} — 재료 상세 시세 (인증 필요)
 *  - kamis: KAMIS 도소매 시세 (null 가능)
 *  - onlinePrices: 네이버/식자재왕 등 온라인 가격 (빈 배열 가능)
 *  - externalSearchLinks: 검색 페이지 URL (fallback용)
 */
export async function getPriceDetail(
  ingredientId: number,
): Promise<PriceDetail> {
  return apiClient.get<PriceDetail>(`/prices/${ingredientId}`);
}

/**
 * GET /prices/{ingredientId}/trend — 30일 가격 추이 + buy-signal (인증 필요)
 */
export async function getPriceTrend(
  ingredientId: number,
  days = 30,
): Promise<PriceTrend> {
  return apiClient.get<PriceTrend>(
    `/prices/${ingredientId}/trend?days=${days}`,
  );
}
