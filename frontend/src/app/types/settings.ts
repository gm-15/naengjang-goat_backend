/**
 * 백엔드 DTO 매핑.
 * 출처: backend/settings/dto/StoreSettings{Request,Response}.java
 */

export type DayOfWeek = "MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN";

export const DAY_OF_WEEK_LABEL: Record<DayOfWeek, string> = {
  MON: "월",
  TUE: "화",
  WED: "수",
  THU: "목",
  FRI: "금",
  SAT: "토",
  SUN: "일",
};

export const DAY_OF_WEEK_ORDER: DayOfWeek[] = [
  "MON",
  "TUE",
  "WED",
  "THU",
  "FRI",
  "SAT",
  "SUN",
];

export interface StoreSettings {
  openTime: string | null;       // "HH:mm:ss" 또는 "HH:mm", 미설정 시 null
  closeTime: string | null;
  orderDay: DayOfWeek | null;
  inventoryDay: DayOfWeek | null;
  configured: boolean;           // false 면 아직 설정 안 됨
}

export interface UpdateStoreSettingsPayload {
  openTime: string;       // "HH:mm" (백엔드 LocalTime 매핑)
  closeTime: string;
  orderDay: DayOfWeek;
  inventoryDay: DayOfWeek;
}
