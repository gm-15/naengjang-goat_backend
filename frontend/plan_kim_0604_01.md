# plan_kim_0604_01 — 백엔드 API 실연동 (Mock → 실제 API 전환)

작성일: 2026-06-04
선행 자료: `BACKEND_GUIDE.md` (프론트 추정), `API_GUIDE.md` (실제 백엔드 스펙)
이번 범위: Phase A ~ F (인증 / 온보딩 / 조회 / 발주 / buy-signal). 신규 도메인(메뉴·판매·FCM)은 별도 plan.

---

## 0. 확정된 결정사항

| 항목 | 결정 |
|---|---|
| Base URL | `http://localhost:8080` (`.env.local` 신규 생성) |
| 토큰 저장 위치 | `localStorage["auth_access_token"]`, `localStorage["auth_refresh_token"]` |
| 토큰 만료 시 처리 | 401 응답 → `localStorage.clear()` + `/` 로그인 화면 리다이렉트 (Refresh 흐름은 백엔드 협의 후 별도 plan) |
| 회원가입 폼 | 3필드(`username`/`password`/`ownerName`)로 축소 |
| 온보딩 | `/onboard` 신규 라우트, 회원가입 직후 강제 진입 |
| 발주기록 localStorage | 이번에 **제거** (백엔드 `purchase-orders`로 완전 이전) |
| 재료 CRUD | 가이드에 미존재 → **"재료 추가" 버튼 숨김** (Phase D에서 처리) |
| `/api/users/me` 부재 | 로그인 시 입력한 `username`만 `localStorage`에 저장 후 사이드바 표시 |
| 데모 계정 | `demo` / `demo1234` (가이드 명시) |
| 신규 도메인 (메뉴/판매/FCM) | 이번 plan **범위 밖** — plan_kim_0604_02 로 분리 |

---

## 1. 백엔드 미확정 가정사항 (작업 중 검증 필요)

확인 못 받은 항목에 대한 잠정 결정. 실제 동작이 다르면 빨간 표시 항목부터 수정.

| # | 가정 | 영향 |
|---|---|---|
| 1 | 🟡 `/api/users/me` 없음 → username만 표시 | 사이드바 표기 단순화 |
| 2 | 🔴 재료 추가/삭제 API 없음 | LowestPricePage 재료 추가 UI 제거. 있으면 복구 |
| 3 | 🔴 메뉴 CRUD `POST/PATCH/DELETE /menus` 없음 | 신규 도메인이라 이번 범위 밖. 영향 없음 |
| 4 | 🟡 가격 응답에 이미지 URL 없음 | Unsplash fallback 유지 |
| 5 | 🟢 Refresh token 흐름 미구현 | 단순 401 → 재로그인 |
| 6 | 🟢 CORS 5173 허용됨 | 가이드에 명시 |

---

## 2. 환경 설정

### 2-1. `.env.local` 신규 생성 (프로젝트 루트)
```
VITE_API_BASE_URL=http://localhost:8080
```

### 2-2. 백엔드 서버 기동 확인
- 사용자가 백엔드를 별도로 띄워야 함 (`localhost:8080`)
- Swagger UI(`http://localhost:8080/swagger-ui/index.html`)로 사전 동작 확인 권장

---

## 3. Phase별 작업 계획

### Phase A — 인프라 (API 클라이언트 + 토큰 자동 주입)

**파일**
- `src/app/api/client.ts` (수정)
- `src/app/lib/auth-storage.ts` (신규)

**작업 내용**
- `apiClient` 가 매 요청마다 `localStorage["auth_access_token"]` 자동 주입
- 401 응답 시 `localStorage.clear()` + `window.location.href = "/"` 로 리다이렉트
- baseURL 환경변수로 분리

**검증**: 콘솔에서 `apiClient.get('/menus')` 호출 시 401 응답 후 자동 리다이렉트

---

### Phase B — 인증 (회원가입 / 로그인 / 로그아웃)

**파일**
- `types/user.ts` (수정 — SignupPayload 3필드 축소, AuthResult 변경)
- `api/auth.ts` (Mock 제거, 실 API 호출로 교체)
- `hooks/useAuth.ts` (응답 처리 변경)
- `pages/LoginPage.tsx` (회원가입 폼 8필드 → 3필드 축소)
- `components/common/AppShell.tsx` (사이드바 로그아웃 처리)

**타입 변경**
```ts
// before
interface SignupPayload {
  username, password, email, name, storeName, storeAddress,
  openTime, closeTime, orderDay, businessType
}
// after
interface SignupPayload {
  username: string;
  password: string;
  ownerName: string;
}

// before
interface AuthResult { user: User; token: string; }
// after
interface AuthResult { accessToken: string; refreshToken: string; }
```

**페이지 변경**
- `LoginScreen`: 그대로 (이메일·비번만)
- `SignupScreen`: 8필드 → 3필드(username·password·ownerName)로 축소. 가게 정보 입력 영역 제거
- 로그인/회원가입 성공 시:
  1. `authStorage.set({ accessToken, refreshToken })`
  2. `localStorage.setItem("username", username)` (사이드바 표기용)
  3. **신규 사용자는 `/onboard` 로**, 기존 사용자는 `/main`으로 이동

**검증**: 데모 계정 로그인 → 사이드바에 "demo" 표기 → 페이지 새로고침 시 토큰 유지

---

### Phase C — 온보딩

**파일**
- `pages/OnboardPage.tsx` (신규)
- `api/users.ts` (신규 — onboard, fcm-token)
- `hooks/useOnboard.ts` (신규)
- `routes.tsx` (`/onboard` 라우트 추가)

**디자인 (AppShell variant="auth" 재사용)**
```
┌────────────────────────────────────────┐
│  매장 카테고리를 선택해주세요              │
│  여러 개 선택 가능합니다                  │
│                                        │
│  [ ◯ 한식 ]  [ ◯ 양식 ]  [ ◯ 중식 ]    │
│  [ ◯ 일식 ]  [ ◯ 카페 ] [ ◯ 베이커리 ] │
│                                        │
│  선택한 카테고리의 추천 메뉴와 재료가     │
│  자동으로 등록됩니다.                    │
│                                        │
│           [ 시작하기 ]                  │
└────────────────────────────────────────┘
```

**카테고리 enum 가정** (가이드 예시: `KOREAN`, `WESTERN`)
- `KOREAN` / `WESTERN` / `CHINESE` / `JAPANESE` / `CAFE` / `BAKERY` 6개 가정. 실제 백엔드 enum 확인 후 보정.

**플로우**
1. 가입 성공 → `/onboard`
2. 카테고리 선택 → `POST /api/users/onboard`
3. 응답 받아 토스트로 "메뉴 N개 / 재료 N개 등록 완료"
4. `/main` 으로 이동

**검증**: 신규 가입 → 온보딩 → 메인 진입 후 `/menus` 호출 시 메뉴 N개 반환

---

### Phase D — 핵심 조회 (재고 부족 / 가격)

#### D-1. 재료 (Ingredient)

**파일**
- `types/order.ts` (LowStockItem 필드 확장)
- `api/orders.ts` → `api/ingredients-stock.ts` 로 리네임 (의미 명확화)
- `hooks/useLowStock.ts` (경로 변경)

**LowStockItem 확장**
```ts
interface LowStockItem {
  ingredientId: number;          // ★변경: id → ingredientId
  ingredientName: string;        // ★변경: name → ingredientName
  currentStock: number;
  baseUnit: string;              // ★변경: unit → baseUnit
  dailyAvgSales: number;         // ★신규
  nextOrderDayDistance: number;  // ★신규: N일 후 발주 권장
  stockRatio: number;            // ★신규: 0.25 = 25%
  grade: "SAFE" | "WARNING" | "DANGER"; // ★신규 (기존 urgencyLevel 대체)
  estimatedDepletionDate: string;// ★신규
  orderAlert: boolean;           // ★신규
}
```

**OrderPage 카드 UI 변경**
- `urgencyLevel` (high/medium/low) → `grade` (DANGER/WARNING/SAFE) 매핑
- "마지막 발주 N일 전" → "예상 소진일: 2026-06-05" + "N일 후 발주" 로 교체
- "일평균 1.2kg" 정보 추가 표시

#### D-2. 가격 (Price)

**파일**
- `types/ingredient.ts` (`Ingredient`, `ProductData` 재정의)
- `api/prices.ts` (3개 엔드포인트로 분리)
- `hooks/usePriceDetail.ts`, `hooks/usePriceHistory.ts` (응답 매핑 변경)
- `pages/LowestPricePage.tsx` (카드 표시 필드 변경)
- `pages/LowestPriceDetailPage.tsx` (차트·뱃지 매핑 + buy-signal 표시)

**Ingredient 재정의**
```ts
// before
interface Ingredient { id, name, category, price, unit, supplier, monthlyAvgPrice }

// after — lowest-top 응답 기반
interface Ingredient {
  ingredientId: number;
  name: string;
  weekAvg: number;
  monthAvg: number;
  todayPrice: number;
  dropRatePct: number;           // 월평균 대비 하락률
  externalLinks: {
    source: string;              // "NAVER", "SIKJAJAEWANG" 등
    url: string;
  }[];
}
```

**`api/prices.ts` 3개 엔드포인트**
```ts
listLowestTop(limit = 5)               // GET /prices/lowest-top
getPriceDetail(ingredientId)           // GET /prices/{id}
getPriceTrend(ingredientId, days = 30) // GET /prices/{id}/trend
```

**LowestPricePage 카드 변경**
- 기존: `8,500원 / kg | 월 평균 8,750원 ↓2.9%`
- 변경: `오늘 18,500원 | 월평균 24,350원 [↓ 24% 하락]`

**LowestPriceDetailPage 변경**
- KAMIS 카드 → "오늘 도매가" + "주평균/월평균" 카드로 재구성
- 플랫폼 카드(쿠팡/네이버/컬리) → `externalLinks[].source` 매핑 (로고 이모지 유지)
- 차트는 `/prices/{id}/trend` 의 `points[]` 사용 (별도 훅으로 분리)
- **`currentBuySignal: true`** 면 상단에 "지금 발주 적기" 강조 배너 + `signalReason` 텍스트 표시

---

### Phase E — 발주 (PurchaseOrder)

**파일**
- `types/order.ts` (`OrderRecord` 삭제, `PurchaseOrder` 신규)
- `api/order-history.ts` → `api/purchase-orders.ts` 로 리네임
- `hooks/useOrderHistory.ts` → `hooks/usePurchaseOrders.ts` 로 리네임
- `pages/LowestPriceDetailPage.tsx` (발주 모달 supplier/memo/unitPrice 추가)
- `pages/OrderPage.tsx` (발주 기록 탭 페이지네이션 + 필드 변경)

**PurchaseOrder 타입**
```ts
interface PurchaseOrder {
  id: number;
  ingredientId: number;
  ingredientName: string;
  orderedAt: string;             // "2026-06-04"
  quantity: number;
  baseUnit: string;              // "kg"
  unitPrice: number;
  totalAmount: number;
  supplier: string;
  memo?: string;
  status: "PENDING" | "CONFIRMED" | "CANCELLED";
  createdAt: string;             // ISO
}
```

**발주 모달 (LowestPriceDetailPage)**
- 기존: 수량 + 자동 platform 캡처
- 추가: `supplier` 필수 입력 (텍스트), `memo` 선택 입력 (textarea)
- platform → supplier 변환: 외부 사이트 방문한 플랫폼명을 `supplier` 기본값으로 채워줌

**발주 목록 페이지네이션**
- `Page<T>` 응답 구조:
  ```ts
  interface PageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;     // 현재 페이지
    size: number;
  }
  ```
- 무한 스크롤보다 단순 "더 보기" 버튼 권장 (시연 단순함)
- 기본 필터: 최근 30일 (`from`/`to` 자동 계산)

**필수 쿼리 파라미터**
- `GET /purchase-orders` 호출 시 `from`, `to` 필수. 프론트가 자동 계산: 오늘 - 30일 ~ 오늘

**Excel 다운로드 (옵션, 시연용)**
- 발주 기록 탭 상단에 "엑셀 내보내기" 버튼
- `apiClient.get('/purchase-orders/export', { responseType: 'blob' })` → File 저장

**localStorage 발주기록 제거**
- `naengjang_goat__order_history_v1` 키 더 이상 사용 안 함
- 기존 사용자 마이그레이션은 백엔드 `POST /purchase-orders` 반복 호출로 처리 가능하지만 이번 범위 밖 (Mock 데모 한정 데이터라 무시 OK)

---

### Phase F — buy-signal 알림 통합

**파일**
- `pages/OrderPage.tsx` (더미 알림 → 실데이터)
- `hooks/useBuySignalAlert.ts` (신규)

**로직**
- OrderPage 진입 시 재고 부족 TOP 1 재료의 `/prices/{id}/trend` 호출
- `currentBuySignal: true` 면 기존 더미 모달 자리에 실제 데이터로 표시
- `signalReason` 텍스트를 그대로 메시지로 활용
- `currentBuySignal: false` 면 모달 표시 안 함

**모달 디자인** — 기존 더미와 동일, 텍스트만 실데이터로 교체
- `"닭고기가 매우 쌉니다"` → `"{ingredientName}이(가) 매우 쌉니다"`
- 가격 박스: `weekAvg / monthAvg / todayPrice` 실데이터
- "확인하기" 버튼 → `/lowest-price/{ingredientId}` 로 이동

---

## 4. 작업 순서 (실행 단위)

| 순서 | Phase | 작업 단위 | 예상 |
|---|---|---|---|
| 1 | A | `.env.local` + `client.ts` + `auth-storage.ts` | 1h |
| 2 | B | 회원가입 폼 축소 + 로그인 응답 매핑 + 사이드바 로그아웃 | 2h |
| 3 | C | OnboardPage 신규 + 라우트 + 가입 후 분기 | 2h |
| 4 | D-1 | LowStockItem 확장 + OrderPage 카드 표시 | 1.5h |
| 5 | D-2 | Ingredient 재정의 + 3개 가격 API + 카드/상세/차트 | 3h |
| 6 | E | PurchaseOrder 전환 + 발주 모달 + 페이지네이션 | 2.5h |
| 7 | F | buy-signal 실데이터 연동 | 1h |
| 8 | 검증 | 데모 계정으로 전체 플로우 확인 | 1h |
| **합계** |  |  | **14h** |

---

## 5. Phase별 commit 권장

각 Phase 끝에 git commit 분리 — 문제 발생 시 phase 단위 롤백 가능.

```
A: feat(api): connect to real backend via apiClient + token auto-injection
B: refactor(auth): switch signup/login to real API (3-field signup)
C: feat(onboard): add category selection step after signup
D: refactor(stock,price): map LowStockItem/Ingredient to real API schema
E: refactor(orders): switch purchase orders from localStorage to backend API
F: feat(buy-signal): replace dummy alert with real /prices/trend signal
```

---

## 6. 리스크 & 대응

| 리스크 | 대응 |
|---|---|
| 백엔드 서버 미기동 | 모든 API가 fetch 실패. 사용자가 `localhost:8080` 기동 후 진행 |
| CORS 오류 | 가이드에 5173 허용 명시. 다른 포트로 dev 띄우면 백엔드 설정 추가 요청 |
| 가정 1·2가 틀린 경우 (재료 CRUD 존재) | LowestPricePage 추가 UI 복구. 작업 후 발견 시 단발 수정 |
| 가정 4가 틀린 경우 (이미지 URL 응답에 있음) | fallback 코드 유지하면 자동 호환 |
| 카테고리 enum 값 다름 | OnboardPage 카테고리 라벨/값을 백엔드 응답 보고 보정 |
| `/api/users/me` 추후 추가됨 | username 표시 → 실제 사용자명/매장명으로 교체 (단순) |

---

## 7. 범위 밖 (별도 plan)

### plan_kim_0604_02 — 메뉴 / 판매 (POS) 통합 (예정)
- `pages/MenuPage.tsx` 신규
- `pages/SalesPage.tsx` 신규
- `/menus`, `/orders` (POS 판매) API 연동
- 사이드바 메뉴 항목 추가
- 메뉴 등록 → BOM 설정 → 판매 시 재고 자동 차감 플로우

### plan_kim_0604_03 — FCM 푸시 알림 (예정)
- Firebase SDK 통합
- `PATCH /api/users/fcm-token` 등록
- 백그라운드 buy-signal 푸시 수신

### 기타 범위 밖
- Refresh token 재발급 흐름 (백엔드 추후 협의)
- 발주 기간 집계(`GET /purchase-orders/summary`) UI
- 재료 카테고리 수정(`PATCH /ingredients/{id}/category`) UI

---

## 8. 검증 시나리오 (Phase 완료 후)

### 시나리오 1 — 신규 가입 플로우
1. `/` 로그인 화면 → "회원가입" 클릭
2. 3필드 입력 (testuser / test1234 / 김건우) → 가입
3. **`/onboard` 자동 진입** → 한식·양식 선택 → "시작하기"
4. "메뉴 41개, 재료 N개 등록 완료" 토스트
5. `/main` 진입

### 시나리오 2 — 데모 계정 플로우
1. 로그인 (demo / demo1234)
2. `/main` 진입 (사이드바에 "demo" 표시)
3. `/lowest-price` → 카드에 todayPrice + dropRatePct 표시
4. 특정 재료 클릭 → 상세 진입 → 30일 trend 차트 + buy-signal 배너 (있을 때)
5. "구매하러 가기" → 외부 사이트 → 복귀 → 발주 모달 (수량+공급자+메모) → "발주 추가"
6. `/order?tab=history` 자동 이동 → 백엔드에서 받은 발주 기록 표시
7. "엑셀 내보내기" → .xlsx 다운로드 확인

### 시나리오 3 — buy-signal 동작
1. `/order` 진입 → 재고 부족 TOP 1 의 `/prices/{id}/trend` 자동 호출
2. `currentBuySignal: true` 인 경우만 알림 모달 표시
3. `signalReason` 텍스트 그대로 표시
4. "확인하기" → 해당 재료 상세 페이지로

---

## 9. 리뷰 메모 공간

_수정 사항 있으면 적어주세요. 없으면 "구현 시작해"_

-
-
-
