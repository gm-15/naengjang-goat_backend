/** GET /closing/notifications/{id} 응답 (원본 백엔드 docs/handoff/API_CONTRACT.md §5) */

export type NotificationType = "UPLOAD" | "OPENING";

export type DeliveryStatus =
  | "PENDING"
  | "SENT"
  | "NOT_CONFIGURED"
  | "WAITING_FOR_TOKEN"
  | "FAILED"
  | "EXPIRED";

export interface RecommendationItem {
  ingredientId: number;
  ingredientName: string;
  baseUnit: string;
  currentStock: number;
  dailyAvgSales: number;
  nextOrderDayDistance: number;
  recommendedQuantity: number | null;
  estimatedDepletionDate: string | null;
  stockAlert: boolean;
  buySignal: boolean;
  priceDataCoverage: number;
  priceReason: string | null;
  reason: string | null;
}

export interface Recommendations {
  businessDate: string;
  generatedAt: string;
  lastUploadedBusinessDate: string | null;
  lastReflectedAt: string | null;
  settingsConfigured: boolean;
  items: RecommendationItem[];
}

export interface ClosingNotification {
  notificationId: number;
  type: NotificationType | string;
  businessDate: string;
  title: string;
  body: string;
  deliveryStatus: DeliveryStatus | string;
  createdAt: string;
  sentAt: string | null;
  recommendations: Recommendations | null;
}

/** FCM data 페이로드 (값은 모두 문자열) */
export interface PushData {
  notificationId?: string;
  type?: string;
  businessDate?: string;
  route?: string;
}
