/**
 * 백엔드 OnboardRequest.categories 허용값.
 * 출처: backend/RecipeTemplate.java, OnboardRequest.java
 */
export type RecipeCategory =
  | "KOREAN"
  | "WESTERN"
  | "CHINESE"
  | "JAPANESE"
  | "OTHER";

export interface OnboardPayload {
  categories: RecipeCategory[];
}

export interface OnboardResult {
  createdMenus: number;
  createdBom: number;
  /** placeholder requiredQuantity=1 로 생성된 재료. 사장님 직접 수정 필요 */
  newIngredients: string[];
}

export const CATEGORY_OPTIONS: Array<{
  value: RecipeCategory;
  label: string;
  emoji: string;
  description: string;
}> = [
  { value: "KOREAN", label: "한식", emoji: "🍚", description: "백반·찌개·구이" },
  { value: "WESTERN", label: "양식", emoji: "🍝", description: "파스타·스테이크·샐러드" },
  { value: "CHINESE", label: "중식", emoji: "🥟", description: "짜장면·짬뽕·볶음" },
  { value: "JAPANESE", label: "일식", emoji: "🍣", description: "초밥·돈카츠·우동" },
  { value: "OTHER", label: "기타", emoji: "🍽️", description: "카페·베이커리·간식 등" },
];
