export interface MenuSummary {
  menuId: number;
  name: string;
  price: number;
  recipe: MenuRecipeIngredient[];
}

export interface MenuRecipeIngredient {
  ingredientId: number;
  ingredientName: string;
  baseUnit: string;
  requiredQuantity: number;
  unit: string;
}

export interface MenuRecipePayload {
  name: string;
  price: number;
  recipe: Array<{ ingredientId: number; requiredQuantity: number; unit: string }>;
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

export interface PosUploadResult {
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
