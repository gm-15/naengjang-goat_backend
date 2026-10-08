# plan_kim_0423_02 — PDF 제거 + 가격 비교/차트 + 발주기록 기능 추가

작성일: 2026-04-23
선행 작업: `plan_kim_0423_01.md` (이미 구현 완료)
이번 범위: 4가지 기능 추가·수정 + 1개 제거

---

## 0. 확정된 결정사항

| 항목 | 결정 |
|---|---|
| Q1-1 PDFExporter 파일 | **삭제** (제 권장안, 이견 없으면 진행) |
| Q1-2 jspdf/html2canvas 의존성 | **package.json에서 제거** (제 권장안) |
| Q2-1 월 평균 출처 | **KAMIS 30일 평균** |
| Q2-2 월 평균 표시 위치 | **카드 우측에 비교 뱃지** (`↓ 3% (월 평균 대비)`) |
| Q2-3 변동 색상 + 퍼센트 | **둘 다 구현** (싸면 초록, 비싸면 빨강, % 함께) |
| Q2-4 데이터 소스 | **`Ingredient` 타입에 `monthlyAvgPrice` 추가, Mock 하드코딩** |
| Q3-1 그래프 Y축 | **가격** |
| Q3-2 그래프 형식 | **30일치 일별 시계열 + 월평균/주평균/현재 ReferenceLine 3개** |
| Q3-3 차트 위치 | **발주 페이지 상단에 선택된 제품 큰 차트** (기본 TOP1 선택) |
| Q3-4 라이브러리 | Recharts (이미 설치됨) |
| Q4-1 저장 시점 | "발주 추가" 버튼 클릭 시 |
| Q4-2 저장 필드 | `OrderRecord` + **수량 입력 필드 추가** |
| Q4-3 저장 위치 | **API 추상 + localStorage 구현** |
| Q4-4 기록 UI | **발주 페이지에 탭 2개** (재고 부족 / 발주 기록) |
| Q4-5 표시 형식 | **날짜별 그룹 리스트** |
| Q4-6 부가 기능 | **삭제 기능만** |

---

## 1. 변경 파일 요약

### 삭제 (1개)
- `src/app/components/PDFExporter.tsx`

### 수정 (6개)
- `package.json` — `jspdf`, `html2canvas` 제거
- `src/app/types/ingredient.ts` — `monthlyAvgPrice` 필드 추가
- `src/app/types/order.ts` — `OrderRecord` 타입 추가
- `src/app/lib/mock-data.ts` — `monthlyAvgPrice`, 30일 `priceHistory` 추가
- `src/app/pages/MainPage.tsx` — PDFExporter 섹션 제거
- `src/app/pages/LowestPricePage.tsx` — 카드에 `PriceTrendBadge` 표시
- `src/app/pages/LowestPriceDetailPage.tsx` — 발주 확인 모달에 수량 입력 + 저장 로직
- `src/app/pages/OrderPage.tsx` — 상단 차트 섹션 + 탭 2개 (재고부족/발주기록)

### 신규 (6개)
- `src/app/types/ingredient.ts` 에 `PricePoint` 인터페이스 추가
- `src/app/lib/price-history.ts` — 30일 가격 히스토리 생성 (seeded random)
- `src/app/api/order-history.ts` — localStorage 기반 발주기록 CRUD
- `src/app/hooks/useOrderHistory.ts` — 조회/저장/삭제 훅
- `src/app/hooks/usePriceHistory.ts` — 30일 가격 히스토리 조회 훅
- `src/app/components/common/PriceChart.tsx` — Recharts LineChart 래퍼
- `src/app/components/common/PriceTrendBadge.tsx` — 월 평균 대비 뱃지

---

## 2. 상세 설계

### 2-1. 타입 확장

```ts
// src/app/types/ingredient.ts

export interface Ingredient {
  id: number;
  name: string;
  category: IngredientCategory;
  price: number;               // 현재(공급자) 가격
  unit: IngredientUnit;
  supplier: string;
  monthlyAvgPrice: number;     // ★ 신규: KAMIS 30일 평균
}

export interface PricePoint {
  date: string;                // "2026-03-25" (YYYY-MM-DD)
  price: number;
}

// ProductData에도 priceHistory 추가
export interface ProductData {
  // ... 기존
  priceHistory: PricePoint[];  // ★ 신규: 최근 30일
}
```

```ts
// src/app/types/order.ts

export interface OrderRecord {
  id: number;
  orderedAt: string;           // ISO: "2026-04-23T14:30:00"
  ingredientId: number;
  ingredientName: string;
  platform: string;            // "쿠팡" 등
  price: number;
  unit: string;
  quantity: number;            // ★ 수량
}

export interface CreateOrderRecordPayload {
  ingredientId: number;
  ingredientName: string;
  platform: string;
  price: number;
  unit: string;
  quantity: number;
}
```

### 2-2. 가격 히스토리 생성 (Mock)

```ts
// src/app/lib/price-history.ts

export function generatePriceHistory(
  basePrice: number,
  days = 30,
  seed = 42,
): PricePoint[] {
  // seeded random(간단한 LCG) 으로 ±8% 내의 변동폭
  // 오늘부터 역순으로 days일치 생성
}

export function calculateAverages(history: PricePoint[]) {
  return {
    monthly: Math.round(avg(history)),
    weekly: Math.round(avg(history.slice(-7))),
    current: history[history.length - 1]?.price ?? 0,
  };
}
```

Seeded random 이유: 새로고침해도 같은 그래프가 나오도록. 재료 id를 seed로 사용.

### 2-3. PriceTrendBadge (최저가 페이지용)

```tsx
// src/app/components/common/PriceTrendBadge.tsx

interface PriceTrendBadgeProps {
  current: number;
  average: number;
  label?: string;  // "월 평균 대비"
}

// diffPct = (current - average) / average * 100
// diffPct < 0 → 초록 "↓ 3.2% (월 평균 대비)"
// diffPct > 0 → 빨강 "↑ 2.8% (월 평균 대비)"
// diffPct ≈ 0 → 회색 "= 월 평균 수준"
```

**최저가 페이지 카드 UI 변경** (카드 하단 추가):
```
[카테고리 뱃지]
닭고기 (1kg)
A업체
8,500원 / kg
────────────────────
월 평균 8,750원  [↓ 2.9% 초록뱃지]
```

### 2-4. PriceChart (발주 페이지용)

```tsx
// src/app/components/common/PriceChart.tsx

interface PriceChartProps {
  history: PricePoint[];
  monthly: number;
  weekly: number;
  current: number;
  unit?: string;
}

// Recharts LineChart
// - X축: date (MM-DD)
// - Y축: price
// - Line: history 실선 (파랑 #0EA5E9)
// - ReferenceLine: monthly (점선 주황), weekly (점선 보라), current (실선 초록)
// - Tooltip 커스텀: 날짜 + 가격
// - 반응형: <ResponsiveContainer width="100%" height={300}>
```

### 2-5. OrderPage 레이아웃 (구조 변경)

```
┌─────────────────────────────────────────┐
│  PageHeader "발주 관리"                   │
├─────────────────────────────────────────┤
│  [경고 카드: 긴급 발주 N개]                │
├─────────────────────────────────────────┤
│  선택된 제품 가격 추세 차트 (★신규)         │
│  ┌─────────────────────────────────┐   │
│  │ [제품 선택 드롭다운] 현재: 8,500원 │   │
│  │                                  │   │
│  │       [30일 LineChart]            │   │
│  │                                  │   │
│  │ 월평균 8,750 | 주평균 8,600       │   │
│  └─────────────────────────────────┘   │
├─────────────────────────────────────────┤
│  [ 재고 부족 TOP 5 ] [ 발주 기록 ]        │ ← 탭
│  (탭 컨텐츠)                             │
└─────────────────────────────────────────┘
```

**탭 1 — 재고 부족 TOP 5**: 기존 그대로 유지, 각 카드 클릭 시 **그 제품의 차트로 위 섹션이 바뀜** (차트는 스크롤 상단에 고정적 위치)

**탭 2 — 발주 기록**: 날짜별 그룹 리스트
```
2026-04-23 (오늘)
  ┌ 닭고기 1kg × 10kg
  │ 쿠팡 · 8,500원 · 소계 85,000원          [🗑]
  └

2026-04-22 (어제)
  ┌ 양파 1kg × 5kg
  │ 네이버 · 2,500원 · 소계 12,500원        [🗑]
  └
```

**탭 상태 관리**: URL 쿼리 파라미터 `?tab=history` 로 유지 → 최저가 상세에서 발주 추가 후 `navigate('/order?tab=history')` 로 이동하면 자동으로 기록 탭이 활성화됨.

### 2-6. LowestPriceDetailPage — 발주 확인 모달 확장

```
┌──────────────────────────┐
│    📋 발주 확인           │
│                          │
│ 해당 상품을 발주 목록에     │
│ 추가하시겠습니까?          │
│                          │
│ 수량                      │
│ ┌────────┐               │
│ │   5    │ kg            │  ← ★신규 input
│ └────────┘               │
│                          │
│  [취소]  [발주 추가]       │
└──────────────────────────┘
```

"발주 추가" 클릭 시:
1. `useSaveOrderRecord().mutate({ ingredientId, ingredientName, platform, price, unit, quantity })`
2. 성공 시 `navigate('/order?tab=history')`

플랫폼 정보 추적: "구매하러 가기" 클릭 시 방문한 플랫폼을 state로 저장 → 모달에서 `source.platform` 표시 + 저장 시 함께 기록.

### 2-7. localStorage 스키마

```ts
// src/app/api/order-history.ts

const STORAGE_KEY = "naengjang_goat__order_history_v1";

export async function listOrderHistory(): Promise<OrderRecord[]> {
  await sleep(100);
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return [];
  try {
    return JSON.parse(raw) as OrderRecord[];
  } catch {
    return [];
  }
  // 나중에: return apiClient.get<OrderRecord[]>("/orders/history");
}

export async function saveOrderRecord(
  payload: CreateOrderRecordPayload,
): Promise<OrderRecord> {
  await sleep(200);
  const existing = await listOrderHistory();
  const record: OrderRecord = {
    id: Date.now(),
    orderedAt: new Date().toISOString(),
    ...payload,
  };
  localStorage.setItem(STORAGE_KEY, JSON.stringify([record, ...existing]));
  return record;
  // 나중에: return apiClient.post<OrderRecord>("/orders/history", payload);
}

export async function deleteOrderRecord(id: number): Promise<void> {
  await sleep(100);
  const existing = await listOrderHistory();
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify(existing.filter((r) => r.id !== id)),
  );
  // 나중에: return apiClient.del<void>(`/orders/history/${id}`);
}
```

키 이름에 `_v1` 접미사: 나중에 스키마 바꿀 때 마이그레이션 지점 마련.

### 2-8. 훅 인터페이스

```ts
// src/app/hooks/useOrderHistory.ts
export function useOrderHistory() { /* useQuery */ }
export function useSaveOrderRecord() { /* useMutation + optimistic */ }
export function useDeleteOrderRecord() { /* useMutation + optimistic */ }

// src/app/hooks/usePriceHistory.ts
export function usePriceHistory(id: number | undefined) {
  // usePriceDetail 호출 후 priceHistory 가공
  // returns { history, monthly, weekly, current, isLoading }
}
```

---

## 3. 반응형 고려사항

- **차트**: 모바일에선 `height={220}`, 데스크탑에선 `height={320}`. `<ResponsiveContainer>` 사용.
- **탭**: shadcn `Tabs` 컴포넌트 (`components/ui/tabs.tsx`) 재사용. 이미 설치됨.
- **발주 기록 리스트**: 데스크탑에선 2열 그리드, 모바일에선 세로 리스트.
- **발주 확인 모달 수량 입력**: 모바일 바텀시트 / 데스크탑 센터 다이얼로그 유지.

---

## 4. 작업 체크리스트

### Phase A — 타입 & 데이터
- [x] `types/ingredient.ts` — `monthlyAvgPrice` + `PricePoint` + `priceHistory` 추가
- [x] `types/order.ts` — `OrderRecord`, `CreateOrderRecordPayload` 추가
- [x] `lib/price-history.ts` — seeded random 생성기
- [x] `lib/mock-data.ts` — 각 재료에 `monthlyAvgPrice` 하드코딩, `MOCK_PRICE_DETAILS`에 `priceHistory` 주입

### Phase B — API & 훅
- [x] `api/order-history.ts` — localStorage CRUD
- [x] `hooks/useOrderHistory.ts`
- [x] `hooks/usePriceHistory.ts`
- [x] `api/prices.ts` — 정의되지 않은 id도 fallback으로 처리 (ingredient 기반 synth)

### Phase C — 공통 컴포넌트
- [x] `components/common/PriceTrendBadge.tsx`
- [x] `components/common/PriceChart.tsx` (Recharts)

### Phase D — 페이지 수정
- [x] `pages/MainPage.tsx` — PDFExporter 제거
- [x] `pages/LowestPricePage.tsx` — 카드에 PriceTrendBadge
- [x] `pages/LowestPriceDetailPage.tsx` — 발주 확인 모달에 수량 입력 + 저장 로직
- [x] `pages/OrderPage.tsx` — 상단 차트 섹션 + 탭 2개 + 발주기록 리스트

### Phase E — 정리
- [x] `components/PDFExporter.tsx` 파일 삭제
- [x] `package.json` — `jspdf`, `html2canvas` 제거

### Phase F — 검증
- [x] 파일 구조 확인 (36개)
- [x] PDFExporter 잔여 참조 없음 확인 (grep 통과)
- [ ] 사용자: `npm i` 재실행 (의존성 변경 반영) + `npm run dev`
- [ ] 플로우 확인:
  1. 최저가 페이지 → 카드에 월 평균 뱃지 표시
  2. 최저가 상세 → "구매하러 가기" → 외부 사이트 → 복귀 → 수량 입력 → 발주 추가
  3. `/order?tab=history` 이동 → 발주 기록에 방금 항목 표시
  4. 발주 페이지 상단 차트에서 제품 선택 시 차트 갱신

---

## 5. 범위 밖

- 월별 총 발주 금액 요약 (Q4-6에서 제외하기로)
- 같은 재료 재발주 버튼 (Q4-6에서 제외)
- 차트의 캔들/바 차트 옵션 (LineChart 하나만)
- 기록 탭에서의 검색/필터
- 차트 zoom/pan

---

## 6. 리스크 & 확인 포인트

- **Recharts는 이미 설치됨** (package.json 확인 완료: `recharts: 2.15.2`). 설치 추가 불필요.
- **shadcn Tabs 컴포넌트**(`components/ui/tabs.tsx`)는 이미 존재. 그대로 사용.
- **PDFExporter 제거 시 import 잔여 확인**: MainPage가 유일한 사용처라 한 곳만 정리하면 됨.
- **priceHistory 계산 비용**: 새로고침할 때마다 매번 seeded random으로 생성 → 가볍기 때문에 캐싱 불필요, useQuery staleTime 1분이면 충분.
- **모달에서 수량 입력 검증**: 빈 값 / 음수 / 0 방지. 기본값 `1` 세팅.
- **탭 URL 파라미터**: `useSearchParams` (react-router v7) 사용.

---

## 7. 리뷰 메모 공간

_수정 사항 있으면 여기에 적어주세요. 없으면 "구현 시작해"_

-
-
-
