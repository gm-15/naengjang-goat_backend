/**
 * 백엔드 DTO 매핑.
 * 출처: backend/inventory/dto/BatchRequest.java, BatchResponse.java
 */

export interface InventoryBatch {
  batchId: number;
  quantity: number;
  expiresAt: string;        // "YYYY-MM-DD" (FIFO 정렬 기준)
}

export interface CreateBatchPayload {
  ingredientId: number;
  quantity: number;
  costPerUnit?: number;
  inboundDate?: string;     // null 이면 백엔드가 오늘로 채움
  expirationDate: string;   // 필수
}
