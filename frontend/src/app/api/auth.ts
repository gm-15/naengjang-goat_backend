import type {
  AuthTokens,
  LoginPayload,
  SignupPayload,
} from "../types/user";
import { apiClient } from "./client";

/**
 * 회원가입.
 * 백엔드 응답: 200 OK + 문자열 "회원가입이 성공적으로 완료되었습니다."
 * 별도 로그인 호출 필요 (응답에 토큰 없음).
 */
export async function signup(payload: SignupPayload): Promise<string> {
  return apiClient.post<string>("/api/users/signup", payload, {
    skipAuth: true,
  });
}

/**
 * 로그인.
 * 백엔드 응답: { accessToken, refreshToken }
 */
export async function login(payload: LoginPayload): Promise<AuthTokens> {
  return apiClient.post<AuthTokens>("/api/users/login", payload, {
    skipAuth: true,
  });
}

/**
 * 로그아웃 — 백엔드에 별도 엔드포인트 없음. 로컬 토큰 제거는 호출처에서 처리.
 */
export async function logout(): Promise<void> {
  // 백엔드에서 토큰 무효화 API 추가될 경우 여기서 호출
  return Promise.resolve();
}
