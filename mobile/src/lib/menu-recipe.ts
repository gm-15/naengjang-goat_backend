import type { MenuRecipePayload } from "../types/workflow";

export interface RecipeInputRow {
  ingredientId: number | null;
  quantity: string;
  unit: string;
}

/** 한 메뉴 판매 시 실제 차감할 소모량을 백엔드 저장 범위에 맞게 검증합니다. */
export function menuRecipePayload(name: string, price: string, rows: RecipeInputRow[]): MenuRecipePayload {
  const trimmedName = name.trim();
  if (!trimmedName || trimmedName.length > 100) throw new Error("메뉴명은 1~100자로 입력해주세요.");
  if (!/^\d+$/.test(price.trim()) || Number(price) > 2_147_483_647) throw new Error("판매가는 0 이상의 정수로 입력해주세요.");
  if (rows.length === 0 || rows.length > 100) throw new Error("재료는 1~100개 등록해주세요.");
  const seen = new Set<number>();
  const recipe = rows.map((row, index) => {
    if (row.ingredientId === null || !Number.isInteger(row.ingredientId) || row.ingredientId <= 0) throw new Error(`${index + 1}번째 재료를 선택해주세요.`);
    if (seen.has(row.ingredientId)) throw new Error("같은 재료는 한 번만 등록하고 소모량을 합산해주세요.");
    seen.add(row.ingredientId);
    const quantity = row.quantity.trim();
    if (!/^(?:\d+(?:\.\d{1,3})?|\.\d{1,3})$/.test(quantity) || Number(quantity) < 0.001 || Number(quantity) > 9_999_999.999) throw new Error(`${index + 1}번째 소모량은 0.001~9999999.999 범위로 입력해주세요. 소수는 최대 셋째 자리까지 가능합니다.`);
    if (!["g", "kg", "ml", "L", "개"].includes(row.unit)) throw new Error(`${index + 1}번째 재료 단위를 확인해주세요.`);
    return { ingredientId: row.ingredientId, requiredQuantity: Number(quantity), unit: row.unit };
  });
  return { name: trimmedName, price: Number(price), recipe };
}
