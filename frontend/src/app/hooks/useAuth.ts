import { useMutation } from "@tanstack/react-query";
import { login, logout, signup } from "../api/auth";
import { authStorage } from "../lib/auth-storage";
import type { LoginPayload, SignupPayload } from "../types/user";
import { queryClient } from "../lib/query-client";

export function useLogin() {
  return useMutation({
    mutationFn: (payload: LoginPayload) => login(payload),
    onSuccess: (tokens, variables) => {
      queryClient.clear();
      authStorage.setTokens(tokens);
      authStorage.setUsername(variables.username);
    },
  });
}

/**
 * 회원가입 → 자동 로그인 → 토큰 저장.
 * 백엔드 signup 응답에 토큰 없으므로 별도 login 호출.
 */
export function useSignup() {
  return useMutation({
    mutationFn: async (payload: SignupPayload) => {
      await signup(payload);
      const tokens = await login({
        username: payload.username,
        password: payload.password,
      });
      return tokens;
    },
    onSuccess: (tokens, variables) => {
      queryClient.clear();
      authStorage.setTokens(tokens);
      authStorage.setUsername(variables.username);
    },
  });
}

export function useLogout() {
  return useMutation({
    mutationFn: () => logout(),
    onSettled: () => {
      authStorage.clear();
      queryClient.clear();
    },
  });
}
