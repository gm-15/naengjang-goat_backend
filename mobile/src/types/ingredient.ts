/**
 * 백엔드 DTO 매핑 (2026-06-04 기준).
 * 출처: backend/pricing/dto/*.java
 */

// ────────── 가격 추이 (Trend) ──────────

/** /prices/lowest-top 응답의 trend 요약 (TrendDto) */
export interface TrendSummary {
  high: number | null;
  current: number | null;
  low: number | null;
  windowDays: number | null;
}

/** /prices/{id}/trend 응답의 points[] 한 행 (TrendPointDto) */
export interface TrendPoint {
  date: string;                  // "YYYY-MM-DD"
  wholesalePrice: number | null;
  retailPrice: number | null;
  weekAvg: number | null;
  monthAvg: number | null;
  buySignal: boolean;
}

/** /prices/{id}/trend 응답 래퍼 (PriceTrendResponse) */
export interface PriceTrend {
  ingredientId: number;
  ingredientName: string;
  currentBuySignal: boolean;
  signalReason: string;
  dataCoverage: number;
  points: TrendPoint[];
}

// ────────── 외부 링크 ──────────

/** ExternalLinkDto */
export interface ExternalLink {
  source: string;                // "NAVER_SEARCH" | "SIKJAJAEWANG_SEARCH" 등
  url: string;
}

// ────────── 최저가 리스트 (lowest-top) ──────────

/** /prices/lowest-top 응답 한 항목 (LowestTopItemDto) */
export interface LowestTopItem {
  ingredientId: number;
  name: string;
  /** 재료 기본 단위 (g · ml · 개) — 재고 배치 수량 단위. 구버전 백엔드는 없음 */
  unit?: string | null;
  weekAvg: number | null;
  monthAvg: number | null;
  todayPrice: number | null;
  dropRatePct: number | null;
  trend: TrendSummary | null;
  externalLinks: ExternalLink[];
}

// ────────── 최저가 상세 (price detail) ──────────

/** PriceDetailDto.kamis (KamisPriceDto in pricing 패키지) */
export interface KamisPrice {
  currentPricePerKg: number | null;
  priceDate: string | null;      // "YYYY-MM-DD"
  weekAvg: number | null;
  monthAvg: number | null;
}

/** PriceDetailDto.onlinePrices[] (OnlinePriceDto) */
export interface OnlinePrice {
  source: string;                // "네이버_축산물", "식자재왕_축산/난류"
  sourceLabel: string;           // "네이버", "식자재왕"
  productName: string;
  productUrl: string;
  imageUrl: string | null;
  price: number;
  currency: string;
  isDiscount: boolean;
  weightGrams: number | null;
  unitPricePerKg: number | null;
  isLowest: boolean;
  fetchedAt: string;             // ISO datetime
}

/** GET /prices/{id} 응답 (PriceDetailDto) */
export interface PriceDetail {
  ingredientId: number;
  name: string;
  unit: string;                  // "g", "kg", "L"
  kamis: KamisPrice | null;
  onlinePrices: OnlinePrice[];
  externalSearchLinks: ExternalLink[];
}
