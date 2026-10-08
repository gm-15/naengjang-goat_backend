# plan_kim_0423_01 — 냉장G.O.A.T 디자인 완성 + 확장성 구조 리팩토링

작성일: 2026-04-23
작업 위치: `C:\Users\User\Downloads\냉장G.O.A.T (1)\` (원본 직접 수정)
확정된 범위: 디자인 다듬기 + 데이터 레이어 추상화 (백엔드 연동은 제외)

---

## 0. 확정된 결정사항

| 항목 | 결정 |
|---|---|
| 작업 위치 | 원본 `Downloads\냉장G.O.A.T (1)\` 에 직접 수정 |
| 확장성 수준 | React Query(TanStack Query) + API 추상 레이어 + 커스텀 훅 |
| 색상 톤 | **기존 그대로 유지** (하늘색 그라데이션 `#0EA5E9 → #38BDF8`, 배경 `#F0F9FF → white`) |
| 반응형 | 데스크탑 + 모바일 두 버전 모두 지원 |
| PDFExporter | **유지** (제거 금지) |
| 백엔드 실제 연동 | 이번 작업 범위 아님 (Mock 반환하는 async 함수로 자리만 마련) |

---

## 1. 목표 (What & Why)

### What
- Figma Make로 export된 앱의 **디자인 완성도를 끌어올리고**, 나중에 백엔드 API를 붙일 때 **페이지 코드를 건드리지 않아도 되도록** 데이터 계층을 분리한다.

### Why
- 현재 5개 페이지에 `MOCK_*` 데이터가 분산 하드코딩돼 있어 실제 API 붙이는 순간 페이지 5개를 모두 다시 뜯어야 함.
- 모바일 폭(`max-w-sm` ≈ 384px)만 고려돼 있어 데스크탑에서는 가운데 얇게 뜨는 흉한 레이아웃.
- 그라데이션 버튼·뒤로가기 헤더·카테고리 뱃지가 페이지마다 중복 복붙됨 → 톤 일관성·유지보수 비용 문제.

---

## 2. 최종 디렉토리 구조

```
src/
├── main.tsx
├── app/
│   ├── App.tsx                    # QueryClientProvider로 감쌈
│   ├── routes.tsx                 # (변경 없음)
│   ├── providers/
│   │   └── AppProviders.tsx       # [신규] QueryClientProvider + 추후 인증 컨텍스트
│   ├── types/                     # [신규] 도메인 타입 통합
│   │   ├── ingredient.ts          # Ingredient, PriceSource, ProductData
│   │   ├── order.ts               # LowStockItem, OrderItem
│   │   └── user.ts                # User, SignupPayload, LoginPayload
│   ├── api/                       # [신규] API 추상 레이어
│   │   ├── client.ts              # fetch wrapper (baseURL, 에러, 토큰) — 지금은 미사용
│   │   ├── ingredients.ts         # listIngredients, createIngredient, deleteIngredient
│   │   ├── prices.ts              # getPriceDetail(id)
│   │   ├── orders.ts              # getLowStock, confirmOrder
│   │   └── auth.ts                # login, signup, logout
│   ├── hooks/                     # [신규] React Query 훅
│   │   ├── useIngredients.ts
│   │   ├── usePriceDetail.ts
│   │   ├── useLowStock.ts
│   │   └── useAuth.ts
│   ├── lib/                       # [신규]
│   │   ├── mock-data.ts           # 현재 흩어진 MOCK_*·INITIAL_* 전부 이곳으로
│   │   ├── query-client.ts        # QueryClient 인스턴스 + 기본 옵션
│   │   └── sleep.ts               # 네트워크 흉내용 (로딩 UI 확인)
│   ├── components/
│   │   ├── PDFExporter.tsx        # [유지] 수정 없음
│   │   ├── common/                # [신규] 페이지 간 중복 컴포넌트
│   │   │   ├── AppShell.tsx       # 데스크탑/모바일 반응형 프레임 (★핵심)
│   │   │   ├── PageHeader.tsx     # 뒤로가기 + 타이틀 + 설명
│   │   │   ├── GradientButton.tsx # 하늘색 그라데이션 버튼
│   │   │   ├── CategoryBadge.tsx  # 카테고리 뱃지
│   │   │   ├── EmptyState.tsx     # 빈 상태
│   │   │   └── LoadingState.tsx   # 스켈레톤/스피너
│   │   ├── figma/                 # (변경 없음)
│   │   └── ui/                    # (변경 없음 — shadcn 그대로)
│   └── pages/                     # 기존 5개 — 데이터는 훅으로, 디자인은 AppShell로 교체
│       ├── LoginPage.tsx
│       ├── MainPage.tsx
│       ├── LowestPricePage.tsx
│       ├── LowestPriceDetailPage.tsx
│       └── OrderPage.tsx
└── styles/ (변경 없음)
```

---

## 3. 반응형 전략 (색은 유지, 레이아웃만 확장)

### 핵심 아이디어: `AppShell` 컴포넌트

- **모바일 (< 768px)**: 현재와 동일 — 전체 폭 사용, `px-6`
- **태블릿 (768px ~ 1024px)**: 중앙 정렬 카드형 (모바일 프레임 시뮬레이션, `max-w-md`)
- **데스크탑 (≥ 1024px)**: **2-panel 레이아웃**
  - 좌측: 사이드바 (로고, 네비게이션: 메인/최저가/발주) — 로그인 후에만
  - 우측: 페이지 컨텐츠 (`max-w-2xl` ~ `max-w-4xl`, 페이지별 조절)
  - 배경 그라데이션은 전체 화면으로 확장

```tsx
<AppShell variant="auth" | "main">
  {children}
</AppShell>
```

- `variant="auth"` — LoginPage 전용 (사이드바 없음, 중앙 카드)
- `variant="main"` — 인증 후 페이지 (사이드바 + 메인 영역)

### Tailwind 브레이크포인트 사용
- 기존 `max-w-sm` 은 유지하되 `md:max-w-2xl lg:max-w-4xl` 로 단계적 확장
- 데스크탑에선 카드를 2-column grid로 배치 (LowestPrice 목록, OrderPage TOP 5)

### 색상 규칙 (★건드리지 않음)
- Primary: `#0EA5E9` → `#38BDF8` (그라데이션)
- Background: `#F0F9FF` → white
- Text: `#1e293b`, `#64748b`, `#94a3b8`, `#334155`
- Border: `#e2e8f0`
- Accent: 현재 사용 중인 orange/red urgency 색은 유지

---

## 4. 확장성 구조 상세

### 4-1. `api/ingredients.ts` 예시 (지금 = Mock, 나중 = 한 줄 교체)

```ts
import type { Ingredient } from "../types/ingredient";
import { MOCK_INGREDIENTS } from "../lib/mock-data";
import { sleep } from "../lib/sleep";

// ── 지금은 Mock ──
export async function listIngredients(): Promise<Ingredient[]> {
  await sleep(200);
  return MOCK_INGREDIENTS;
}

export async function createIngredient(payload: Omit<Ingredient, "id">): Promise<Ingredient> {
  await sleep(300);
  return { ...payload, id: Date.now() };
}

export async function deleteIngredient(id: number): Promise<void> {
  await sleep(200);
}

// ── 나중에 실제 API ──
// export async function listIngredients() {
//   return apiClient.get<Ingredient[]>("/ingredients");
// }
```

### 4-2. `hooks/useIngredients.ts` 예시

```ts
export function useIngredients() {
  return useQuery({
    queryKey: ["ingredients"],
    queryFn: listIngredients,
  });
}

export function useAddIngredient() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: createIngredient,
    // 낙관적 업데이트
    onMutate: async (newItem) => {
      await qc.cancelQueries({ queryKey: ["ingredients"] });
      const prev = qc.getQueryData<Ingredient[]>(["ingredients"]);
      qc.setQueryData<Ingredient[]>(["ingredients"], (old = []) => [
        ...old,
        { ...newItem, id: Date.now() },
      ]);
      return { prev };
    },
    onError: (_err, _new, ctx) => {
      if (ctx?.prev) qc.setQueryData(["ingredients"], ctx.prev);
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ["ingredients"] }),
  });
}
```

### 4-3. 페이지에서의 사용 (LowestPricePage 발췌)

```ts
const { data: ingredients = [], isLoading } = useIngredients();
const addMutation = useAddIngredient();
const deleteMutation = useDeleteIngredient();

// 기존의 useState<Ingredient[]> 로컬 상태 완전히 제거
```

### 4-4. QueryClient 기본 옵션 (`lib/query-client.ts`)

```ts
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,      // 1분
      retry: 1,
      refetchOnWindowFocus: true,
    },
  },
});
```

---

## 5. 타입 통합 예시 (`types/ingredient.ts`)

```ts
export type IngredientCategory =
  | "육류" | "채소" | "소스/양념" | "유제품" | "기타";

export interface Ingredient {
  id: number;
  name: string;
  category: IngredientCategory;
  price: number;
  unit: "kg" | "g" | "L" | "개";
  supplier: string;
}

export interface PriceSource {
  platform: string;
  price: number;
  url: string;
  logo: string;
}

export interface ProductData {
  id: number;
  name: string;
  category: IngredientCategory;
  image: string;
  kamisPrice: number;
  kamisDate: string;
  sources: PriceSource[];
}
```

현재 3개 파일에 중복 정의된 타입을 한 곳에서 import.

---

## 6. 페이지별 변경 요약

| 페이지 | 변경 내용 |
|---|---|
| LoginPage | `AppShell variant="auth"` 적용. `useLogin()`, `useSignup()` 훅 사용. 폼은 `react-hook-form`(이미 의존성 존재)로 교체. 데스크탑에선 좌측 브랜딩 히어로 + 우측 폼 2-column |
| MainPage | `AppShell variant="main"` (사이드바 포함). PDFExporter 유지. 데스크탑에선 2개 카드를 grid-cols-2로 배치 |
| LowestPricePage | `useIngredients()`, `useAddIngredient()`, `useDeleteIngredient()` 사용. 로컬 `useState<Ingredient[]>` 제거. 데스크탑에선 카드 그리드 (md:grid-cols-2 lg:grid-cols-3). 로딩 시 스켈레톤 |
| LowestPriceDetailPage | `usePriceDetail(id)` 사용. 데스크탑에선 이미지/KAMIS + 플랫폼 리스트 2-column |
| OrderPage | `useLowStock()` 사용. 알림 로직(useEffect with setInterval)은 그대로 유지. 데스크탑에선 카드 grid |

---

## 7. 설치/설정 변경

### `package.json` 의존성 추가
```
@tanstack/react-query: ^5.x
```
(이미 있는 `react-hook-form`, `motion`, `lucide-react`, `html2canvas`, `jspdf`는 그대로)

### `App.tsx` 수정
```tsx
<QueryClientProvider client={queryClient}>
  <RouterProvider router={router} />
</QueryClientProvider>
```

### `routes.tsx`
- 변경 없음 (경로 구조 유지)

---

## 8. 작업 체크리스트

### Phase A — 확장성 기반 (먼저)
- [x] `@tanstack/react-query` 설치 (package.json에 추가, `npm i` 는 사용자 실행 필요)
- [x] `src/app/types/` 타입 정의 3개 파일
- [x] `src/app/lib/mock-data.ts` 로 기존 MOCK 전부 이동
- [x] `src/app/lib/sleep.ts`, `src/app/lib/query-client.ts`
- [x] `src/app/api/` 5개 파일 (ingredients, prices, orders, auth, client)
- [x] `src/app/hooks/` 4개 파일
- [x] `src/app/providers/AppProviders.tsx`
- [x] `App.tsx` 를 AppProviders 사용하도록 수정

### Phase B — 공통 컴포넌트
- [x] `components/common/AppShell.tsx` (반응형 ★핵심)
- [x] `PageHeader.tsx`, `GradientButton.tsx`, `CategoryBadge.tsx`
- [x] `EmptyState.tsx`, `LoadingState.tsx`

### Phase C — 페이지 리팩토링 (디자인 + 훅 교체)
- [x] `LoginPage.tsx` — AppShell auth + 데스크탑 2-column
- [x] `MainPage.tsx` — AppShell main + 사이드바 + PDFExporter 유지
- [x] `LowestPricePage.tsx` — 훅 교체 + 카드 그리드 + 스켈레톤
- [x] `LowestPriceDetailPage.tsx` — 훅 교체 + 데스크탑 2-column
- [x] `OrderPage.tsx` — 훅 교체 + 카드 그리드

### Phase D — 검증
- [x] 파일 구조 plan대로 전부 생성 확인 (32개 파일)
- [x] tsconfig.json 존재 여부 확인 → 없음 (Vite + esbuild 트랜스파일만 수행, 타입체크는 생략)
- [ ] `npm i` 후 `npm run dev` 로 기동 → **사용자 실행 필요**
- [ ] 각 페이지 모바일(<768px)/태블릿(768~1024)/데스크탑(≥1024) 확인 → **사용자 실행 필요**
- [ ] PDFExporter 동작 확인 → **사용자 실행 필요**

---

## 9. 범위 밖 (이번 작업에서 안 건드림)

- 실제 백엔드 API 연결 (client.ts는 껍데기만)
- KAMIS/쿠팡/네이버 실제 크롤링 로직
- 로그인 세션·토큰 저장 로직 (나중에 auth.ts에서 처리)
- 알림(Notification API) 실제 푸시 — 현재 mock 타이머 유지
- 다국어, 다크모드
- 단위 테스트

---

## 10. 리스크 & 확인 포인트

- **shadcn ui 컴포넌트 50+개가 현재 페이지에서 거의 미사용**. 공통 컴포넌트 만들 때 shadcn `Card`, `Button`, `Input` 등을 활용할지 vs 현재 스타일대로 custom div를 유지할지 → **현재 스타일 유지** (색·그라데이션이 shadcn 기본 테마와 다르므로)
- **PDFExporter가 각 페이지를 렌더링하면서 캡처**. 훅으로 데이터 받는 구조로 바꾸면 PDF 캡처 시에도 QueryClientProvider 안에서 돌아야 함 → AppProviders로 감싸서 해결 가능
- **데스크탑 사이드바 네비게이션** 추가 시 기존 `뒤로 가기` 버튼과 UX 중복 → 데스크탑에선 뒤로가기 버튼 숨기고 사이드바 활성 상태 표시, 모바일에선 뒤로가기 유지

---

## 11. 리뷰 메모 공간

_여기에 의견·수정 사항 적어주세요. 반영 후 구현 시작합니다._

-
-
-
