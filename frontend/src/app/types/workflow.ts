export interface MenuRecipeIngredient {
  ingredientId: number;
  ingredientName: string;
  baseUnit: string;
  requiredQuantity: number;
  unit: string;
}
export interface Menu {
  menuId: number;
  name: string;
  price: number;
  recipe: MenuRecipeIngredient[];
}
export interface MenuPayload {
  name: string;
  price: number;
  recipe: Array<{
    ingredientId: number;
    requiredQuantity: number;
    unit: string;
  }>;
}
export interface PosMenuMapping {
  id: number;
  userId: number;
  posCode: string;
  menuId: number;
}
export interface MenuSale {
  menuId: number;
  menuName: string;
  posCode: string | null;
  quantity: number;
  salesAmount: number;
}
export interface IngredientConsumption {
  ingredientId: number;
  ingredientName: string;
  baseUnit: string;
  quantity: number;
}
export interface UploadResult {
  uploadId: number;
  businessDate: string;
  duplicate: boolean;
  salesQuantity: number;
  salesAmount: number;
  menuSales: MenuSale[];
  ingredientConsumption: IngredientConsumption[];
  reflectedAt: string;
}
export interface DailyReport {
  businessDate: string;
  uploaded: boolean;
  salesQuantity: number;
  salesAmount: number;
  menuSales: MenuSale[];
  ingredientConsumption: IngredientConsumption[];
  currentInventory: IngredientConsumption[];
  inventoryAsOf: string;
  reflectedAt: string | null;
  lastUploadedBusinessDate: string | null;
}
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
  priceReason: string;
  reason: string;
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
  type: "UPLOAD" | "OPENING";
  businessDate: string;
  title: string;
  body: string;
  deliveryStatus: string;
  createdAt: string;
  sentAt: string | null;
  recommendations: Recommendations;
}
