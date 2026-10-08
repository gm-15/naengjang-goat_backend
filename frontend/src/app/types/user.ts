/**
 * 백엔드 API_GUIDE 기준:
 * - POST /api/users/login → { accessToken, refreshToken } (user 객체 없음)
 * - POST /api/users/signup → "회원가입 완료" 문자열 (별도 로그인 호출 필요)
 *
 * /api/users/me 같은 사용자 조회 엔드포인트는 아직 없으므로
 * username만 클라이언트 측 localStorage 에 별도 보관.
 */

export interface LoginPayload {
  username: string;
  password: string;
}

export interface SignupPayload {
  username: string;
  password: string;
  ownerName: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}
