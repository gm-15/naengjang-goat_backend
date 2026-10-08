/**
 * 백엔드 DTO 매핑 (2026-06-04 기준).
 * 출처: backend/inventory/dto/LowStockItemDto, purchase/dto/PurchaseOrder*
 */

// ────────── 재고 부족 ──────────

/** StockGrade enum (backend/inventory/domain/StockGrade) */
export type StockGrade = "SUFFICIENT" | "NORMAL" | "DANGER";

/** GET /ingredients/low-stock 응답 한 항목 (LowStockItemDto) */
export interface LowStockItem {
  ingredientId: number;
  ingredientName: string;
  currentStock: number;           // BigDecimal → number
  baseUnit: string;
  dailyAvgSales: number;
  nextOrderDayDistance: number;
  stockRatio: number;             // 1.0 = 100%
  grade: StockGrade;
  estimatedDepletionDate: string | null;  // "YYYY-MM-DD"
  orderAlert: boolean;
}

// ────────── 발주 (PurchaseOrder) ──────────

export type PurchaseStatus = "PENDING" | "CONFIRMED" | "CANCELLED";
export type DeliveryStatus = "WAITING" | "RECEIVED" | "LEGACY";

/** GET /purchase-orders 응답 한 항목 (PurchaseOrderResponse) */
export interface PurchaseOrder {
  id: number;
  ingredientId: number;
  ingredientName: string;
  orderedAt: string;              // "YYYY-MM-DD"
  quantity: number;               // BigDecimal → number
  baseUnit: string;
  unitPrice: number;
  totalAmount: number;
  supplier: string;
  memo: string | null;
  status: PurchaseStatus;
  createdAt: string;              // ISO datetime
  productName: string | null;
  sourceUrl: string | null;
  deliveryStatus: DeliveryStatus;
  expectedQuantityBase: number | null;
  inventoryUnit: string | null;
  receivedAt: string | null;
  receivedBatchId: number | null;
}

/** POST /purchase-orders 요청 (PurchaseOrderRequest) */
export interface CreatePurchaseOrderPayload {
  ingredientId: number;
  orderedAt?: string;             // null 시 백엔드에서 오늘로 대체
  quantity: number;
  baseUnit: string;
  unitPrice: number;
  supplier: string;
  memo?: string;
  productName?: string;
  sourceUrl?: string;
  /** baseUnit="포장"인 경우 포장 한 개의 실제 크기와 단위 */
  packageSize?: number;
  packageUnit?: string;
}

export interface ReceivePurchaseOrderPayload {
  /** 주문 응답의 expectedQuantityBase 그대로 전송. 이번 버전은 전체 수령만 지원. */
  receivedQuantity: number;
  expirationDate: string;
  inboundDate?: string;
}

/** Spring Page<T> 응답 */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;                 // 현재 페이지 (0-base)
  size: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  empty: boolean;
}
