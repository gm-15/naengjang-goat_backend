import { apiClient } from "./client";
import type { OnboardPayload, OnboardResult } from "../types/onboard";

/**
 * POST /api/users/onboard — 매장 카테고리 선택 (인증 필요).
 * 선택한 카테고리의 레시피 템플릿이 메뉴로 자동 복사되고,
 * 누락된 재료는 placeholder(requiredQuantity=1)로 생성됨.
 */
export async function onboard(payload: OnboardPayload): Promise<OnboardResult> {
  return apiClient.post<OnboardResult>("/api/users/onboard", payload);
}

/**
 * PATCH /api/users/fcm-token — FCM 디바이스 토큰 등록/갱신 (인증 필요).
 * Phase 범위 밖이지만 함수 시그니처만 정의.
 */
export async function updateFcmToken(token: string): Promise<void> {
  return apiClient.patch<void>("/api/users/fcm-token", { token });
}
