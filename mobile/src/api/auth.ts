import type {
  AuthTokens,
  LoginPayload,
  SignupPayload,
} from "../types/user";
import { apiClient } from "./client";
import { unregisterFcmConnection } from "../lib/fcm-connection";

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
 * 인증을 지우기 전에 현재 계정의 푸시 기기 연결을 해제한다.
 */
export async function logout(): Promise<void> {
  await unregisterFcmConnection();
}
