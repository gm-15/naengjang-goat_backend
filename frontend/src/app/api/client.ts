import { authStorage } from "../lib/auth-storage";
import { queryClient } from "../lib/query-client";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export interface RequestOptions extends Omit<RequestInit, "body"> {
  /** 명시 토큰. 없으면 authStorage의 accessToken 자동 사용 */
  token?: string;
  /** 인증 헤더 강제 비활성화 (예: 로그인/회원가입) */
  skipAuth?: boolean;
}

interface InternalOptions extends RequestOptions {
  body?: BodyInit | null;
}

function buildHeaders(
  options: RequestOptions,
  hasJsonBody: boolean,
): HeadersInit {
  const headers = new Headers(options.headers ?? {});

  if (hasJsonBody && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  if (!options.skipAuth) {
    const token = options.token ?? authStorage.getAccessToken();
    if (token && !headers.has("Authorization")) {
      headers.set("Authorization", `Bearer ${token}`);
    }
  }

  return headers;
}

function handleUnauthorized(): void {
  authStorage.clear();
  queryClient.clear();
  if (typeof window === "undefined") return;
  // 이미 로그인 화면이면 무한 리다이렉트 방지
  if (window.location.pathname === "/") return;
  window.location.href = "/";
}

async function request<T>(
  path: string,
  options: InternalOptions = {},
): Promise<T> {
  const {
    token: _token,
    skipAuth: _skipAuth,
    headers: _headers,
    body,
    ...rest
  } = options;
  const hasJsonBody = typeof body === "string" && body.length > 0;

  let res: Response;
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      ...rest,
      body,
      headers: buildHeaders(options, hasJsonBody),
    });
  } catch {
    throw new ApiError(
      0,
      "서버에 연결할 수 없습니다. 네트워크와 서버 주소를 확인해주세요.",
    );
  }

  if (res.status === 401) {
    handleUnauthorized();
    throw new ApiError(401, "로그인이 만료됐습니다. 다시 로그인해주세요.");
  }

  if (!res.ok) {
    throw await responseError(res);
  }

  if (res.status === 204) return undefined as T;

  const contentType = res.headers.get("Content-Type") ?? "";
  if (!contentType.includes("application/json")) {
    return (await res.text()) as unknown as T;
  }
  return (await res.json()) as T;
}

export const apiClient = {
  get: <T>(path: string, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "GET" }),

  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, {
      ...options,
      method: "POST",
      body: body !== undefined ? JSON.stringify(body) : null,
    }),

  postForm: <T>(path: string, body: FormData, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "POST", body }),

  put: <T>(path: string, body: unknown, options?: RequestOptions) =>
    request<T>(path, {
      ...options,
      method: "PUT",
      body: JSON.stringify(body),
    }),

  patch: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, {
      ...options,
      method: "PATCH",
      body: body !== undefined ? JSON.stringify(body) : null,
    }),

  del: <T>(path: string, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "DELETE" }),
};

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function responseError(res: Response): Promise<ApiError> {
  const fallback =
    res.status === 409
      ? "이미 처리된 요청입니다. 새로고침 후 확인해주세요."
      : res.status === 413
        ? "파일은 5MB 이하로 올려주세요."
        : res.status >= 500
          ? "서버 처리 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요."
          : "요청을 처리하지 못했습니다. 입력값을 확인해주세요.";
  const text = await res.text().catch(() => "");
  try {
    const data = JSON.parse(text);
    return new ApiError(res.status, data.message || data.detail || fallback);
  } catch {
    return new ApiError(
      res.status,
      text && !text.startsWith("<") ? text : fallback,
    );
  }
}

/**
 * Excel 등 바이너리 파일 다운로드용. JSON wrap 없이 raw Blob 반환.
 */
export async function fetchBlob(
  path: string,
  options: RequestOptions = {},
): Promise<Blob> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: "GET",
    headers: buildHeaders(options, false),
  });

  if (res.status === 401) {
    handleUnauthorized();
    throw new ApiError(401, "로그인이 만료됐습니다. 다시 로그인해주세요.");
  }
  if (!res.ok) {
    throw await responseError(res);
  }
  return res.blob();
}

/**
 * Blob을 받아 브라우저에서 파일 다운로드 트리거.
 */
export function downloadBlob(blob: Blob, filename: string): void {
  if (typeof window === "undefined") return;
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
}
