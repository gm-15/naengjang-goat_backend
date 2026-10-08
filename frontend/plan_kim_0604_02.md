# plan_kim_0604_02 — 백엔드 실연동 (4개 화면 한정)

작성일: 2026-06-04
선행 자료: `API_GUIDE.md`, `kim_action_guide_0601.md`, `plan_kim_0604_01.md`
이번 범위: **로그인 · 메인 · 재고 화면 · 발주 화면** 4개 + 부속 페이지 일부
백엔드 저장소: https://github.com/gm-15/naengjang-goat_backend

---

## 0. 확정된 결정사항

| 항목 | 결정 |
|---|---|
| Base URL | `http://localhost:8080` (`.env.local` 신규 생성) |
| 인증 경로 | `/api/users/*` (API_GUIDE 기준) |
| 발주 경로 | `/purchase-orders` |
| 토큰 저장 | `localStorage["auth_access_token"]`, `auth_refresh_token`, `username` |
| 401 처리 | localStorage 비우고 `/` 리다이렉트 |
| 회원가입 폼 | 3필드 (`username` / `password` / `ownerName`) |
| 발주기록 localStorage | 제거, 백엔드 `purchase-orders`로 이전 |
| sim의 prices 작업 | LowestPriceDetailPage는 **Mock 한 줄만 풀면 동작** (1:1 매핑 보장) |
| 신규 도메인 (메뉴/판매) | **범위 밖** — 별도 plan_kim_0604_03 |
| FCM 푸시 | **범위 밖** — 별도 plan_kim_0604_03 |

---

## 1. 범위 — 4개 화면 + 부속

| # | 페이지 | 백엔드 연동 작업 |
|---|---|---|
| 1 | **LoginPage** | `/api/users/signup`, `/api/users/login` |
| 2 | **MainPage** | (조회 API 없음, 사이드바 username 표시만) |
| 3 | **재고 화면** ★확인 필요 | `/ingredients/low-stock` 또는 LowestPricePage |
| 4 | **OrderPage (발주 화면)** | `/ingredients/low-stock`, `/purchase-orders`, buy-signal |
| 부속 | **LowestPriceDetailPage** | `/prices/{id}` (sim 작업으로 거의 자동) |
| 부속 | **OnboardPage (신규)** | `/api/users/onboard` (신규 가입 시연 시) |

### ★확인 필요 항목 (답변 주셔야 확정 가능)
1. **"재고 화면" 정의**
   - (A) LowestPricePage (재료 목록·검색) — 가능성 높음
   - (B) OrderPage 내 "재고 부족 TOP 5" 탭
   - 둘 다일 수도 있음. 답변 주세요.
2. **LowestPriceDetailPage 포함 여부** — 발주 플로우상 필수로 보임. 포함하는 것으로 가정 진행.
3. **신규 가입 시연 여부** — 데모 계정만 쓰면 OnboardPage 생략 가능. 신규 가입 시연도 필요하면 OnboardPage 신규 필요.

→ **본 plan에서는 (A) + LowestPriceDetailPage 포함 + OnboardPage 포함 으로 진행**, 답변 다르면 항목 제거.

---

## 2. 사전 환경 셋업

### 2-1. 백엔드 클론
- **클론 위치 권장**: `C:\Users\User\Downloads\naengjang-goat_backend\`
- 명령:
  ```bash
  cd "C:/Users/User/Downloads"
  git clone https://github.com/gm-15/naengjang-goat_backend.git
  cd naengjang-goat_backend
  ```
- (sim이 만든 `feat/prices-view-dto` 브랜치가 별도라면) `git checkout feat/prices-view-dto`
- Java/Gradle 환경: `./gradlew bootRun` 또는 IDE 실행

### 2-2. 백엔드 기동 확인
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- 데모 로그인:
  ```
  POST /api/users/login
  { "username": "demo", "password": "demo1234" }
  ```
- 응답에서 `accessToken` 확보 후 `Authorization: Bearer <token>` 으로 다른 엔드포인트 확인

### 2-3. 프론트 환경변수
```
# C:\Users\User\Downloads\냉장G.O.A.T (1)\.env.local
VITE_API_BASE_URL=http://localhost:8080
```

---

## 3. 가정사항 (백엔드 미확인 부분)

| # | 가정 | 작업 후 검증 방법 |
|---|---|---|
| 1 | 🟡 `/api/users/me` 없음 → username만 localStorage에 저장 | 사이드바에 "demo" 표시되면 OK |
| 2 | 🔴 재료 추가/삭제 API 없음 → LowestPricePage 추가 버튼 숨김 | Swagger에서 `POST /ingredients` 존재 시 복구 |
| 3 | 🟡 가격 응답 이미지 URL 없을 수 있음 | sources에 image 없으면 Unsplash fallback 유지 |
| 4 | 🟢 데모 계정은 온보딩 완료 상태 | `/ingredients/low-stock` 응답에 데이터 있으면 OK |
| 5 | 🟡 FCM 토큰 미등록도 화면 동작 가능 | 푸시는 안 오지만 모달 알림은 in-app으로 표시 |
| 6 | ⚠️ FCM 경로 `/api/users/fcm-token` (sim 표기 `/users/fcm-token` 와 달라도 `/api/` 포함 가정) | 범위 밖이라 영향 없음 |

---

## 4. Phase별 작업 계획

### Phase A — 인프라 (1h)
- [ ] `.env.local` 생성
- [ ] `src/app/lib/auth-storage.ts` 신규 — accessToken/refreshToken/username get·set·clear
- [ ] `src/app/api/client.ts` 수정
  - baseURL → `import.meta.env.VITE_API_BASE_URL`
  - 모든 요청에 `authStorage.getAccessToken()` 자동 주입
  - 401 응답 시 `authStorage.clear()` + `window.location.href = "/"`

### Phase B — 인증 (LoginPage) (2h)
- [ ] `types/user.ts` 수정 — `SignupPayload` 3필드 축소, `AuthResult { accessToken, refreshToken }`
- [ ] `api/auth.ts` 수정 — Mock 제거, 실 API 호출
  ```ts
  signup(payload)  → POST /api/users/signup
  login(payload)   → POST /api/users/login
  logout()         → 로컬 토큰 제거만 (백엔드 엔드포인트 없음)
  ```
- [ ] `hooks/useAuth.ts` — 응답에서 토큰 저장 + username 저장
- [ ] `pages/LoginPage.tsx` SignupScreen 8필드 → 3필드로 축소
  - 제거: email, name, storeName, storeAddress, openTime, closeTime, orderDay, businessType
  - 유지: username, password, ownerName
- [ ] 로그인 성공 시 라우팅
  - 신규 회원가입 → `/onboard`
  - 기존 로그인 → `/main`

### Phase C — 온보딩 (OnboardPage 신규) (2h)
- [ ] `pages/OnboardPage.tsx` 신규
- [ ] `api/users.ts` 신규
  ```ts
  onboard(categories: string[]) → POST /api/users/onboard
  ```
- [ ] `hooks/useOnboard.ts` 신규
- [ ] `routes.tsx` `/onboard` 추가
- [ ] 디자인: AppShell variant="auth" 재사용
  - 카테고리 6개 가정: `KOREAN`, `WESTERN`, `CHINESE`, `JAPANESE`, `CAFE`, `BAKERY` (백엔드 enum 확인 후 보정)
  - 다중 선택 가능
  - "시작하기" 클릭 → onboard 호출 → 응답의 createdMenus/createdBom/newIngredients 토스트 표시 → `/main` 이동

### Phase D — 메인 (MainPage) (0.5h)
- [ ] `AppShell.tsx` Sidebar — `localStorage["username"]` 가져와 표시
- [ ] 로그아웃 버튼 → `authStorage.clear()` → `/`
- [ ] (조회 API 호출 없음, 카드 클릭 → 다른 페이지로 이동만)

### Phase E — 재고 화면 (LowestPricePage) (2h)
- [ ] `pages/LowestPricePage.tsx` 수정
- [ ] **재료 추가/삭제 UI 숨김** (가정 2)
- [ ] 카드에 표시할 데이터를 `/prices/lowest-top` 응답으로 교체
  - 또는 단순 재료 목록 API가 있다면 그쪽 사용 (확인 필요)
- [ ] `types/ingredient.ts` — `Ingredient` 재정의
  ```ts
  interface Ingredient {
    ingredientId: number;
    name: string;
    weekAvg: number;
    monthAvg: number;
    todayPrice: number;
    dropRatePct: number;
    externalLinks: { source: string; url: string }[];
  }
  ```
- [ ] 카드 UI 갱신
  - 기존: `8,500원 / kg | 월 평균 8,750원 ↓2.9%`
  - 변경: `오늘 18,500원 | 월평균 24,350원 [↓ 24%]`
- [ ] `api/ingredients.ts` → `api/prices.ts` 의 `listLowestTop()` 사용으로 변경
- [ ] `hooks/useIngredients.ts` → `useLowestTop()` 로 변경

### Phase F — 최저가 상세 (LowestPriceDetailPage) (1h)
- [ ] `api/prices.ts` 의 `getPriceDetail(id)` Mock 풀기 (sim 가이드 §1)
  ```ts
  export async function getPriceDetail(id: number): Promise<ProductData> {
    return apiClient.get<ProductData>(`/prices/${id}`);
  }
  ```
  - `token` 인자는 apiClient가 자동 주입하므로 별도 인자 불필요 (Phase A 완성 후)
- [ ] `types/ingredient.ts` 보너스 필드 추가
  ```ts
  ProductData.unit?: string;
  PriceSource.isLowest?: boolean;
  ```
- [ ] `LowestPriceDetailPage.tsx`
  - `isLowest` 활용 — 최저가 뱃지를 그 필드 기준으로 표시 (기존: 클라이언트에서 min 계산)
  - `unit` 활용 — KAMIS 카드의 "/ kg" 부분을 응답값으로 표기
- [ ] 가격 추이 차트는 `priceHistory` 그대로 사용 (sim이 1:1 매핑 보장)
- [ ] 또는 별도로 `/prices/{id}/trend` 호출 (API_GUIDE에 명시) — 가정: `priceHistory`가 `getPriceDetail` 응답에 포함되므로 별도 호출 불필요

### Phase G — 발주 화면 (OrderPage) (3h)

#### G-1. 재고 부족 TOP (LowStockItem)
- [ ] `types/order.ts` — `LowStockItem` 필드 확장
  ```ts
  interface LowStockItem {
    ingredientId: number;         // ★ id → ingredientId
    ingredientName: string;       // ★ name → ingredientName
    currentStock: number;
    baseUnit: string;             // ★ unit → baseUnit
    dailyAvgSales: number;        // ★ 신규
    nextOrderDayDistance: number; // ★ 신규
    stockRatio: number;           // ★ 신규 (0.25 = 25%)
    grade: "SAFE" | "WARNING" | "DANGER";  // ★ 신규
    estimatedDepletionDate: string;        // ★ 신규
    orderAlert: boolean;                   // ★ 신규
  }
  ```
- [ ] `api/orders.ts` → `getLowStock()` 경로 `/ingredients/low-stock?limit=5`
- [ ] `hooks/useLowStock.ts` 응답 매핑 변경
- [ ] OrderPage StockCard UI 갱신
  - `grade` (DANGER/WARNING/SAFE) → 기존 urgencyLevel UI 그대로 매핑
  - "재고율 X%" → `stockRatio * 100` 활용
  - "마지막 발주 N일 전" 제거 → **"N일 후 발주 권장"** (nextOrderDayDistance) + **"예상 소진일 YYYY-MM-DD"** (estimatedDepletionDate)
  - "일평균 X kg" 추가 표시 (dailyAvgSales)

#### G-2. 발주 기록 (PurchaseOrder)
- [ ] `types/order.ts` — `OrderRecord` 삭제, `PurchaseOrder` 신규
  ```ts
  interface PurchaseOrder {
    id: number;
    ingredientId: number;
    ingredientName: string;
    orderedAt: string;            // "2026-06-04"
    quantity: number;
    baseUnit: string;
    unitPrice: number;
    totalAmount: number;
    supplier: string;
    memo?: string;
    status: "PENDING" | "CONFIRMED" | "CANCELLED";
    createdAt: string;
  }
  ```
- [ ] `api/order-history.ts` → `api/purchase-orders.ts` 로 리네임
  ```ts
  listPurchaseOrders({ from, to, page=0, size=20 })  → GET /purchase-orders
  createPurchaseOrder(payload)                       → POST /purchase-orders
  ```
- [ ] 페이지네이션 응답 처리
  ```ts
  interface PageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
  }
  ```
- [ ] `hooks/useOrderHistory.ts` → `hooks/usePurchaseOrders.ts`
  - 기본 필터: 최근 30일 자동 계산 (오늘 기준 `to`, 30일 전 `from`)
- [ ] OrderPage 발주 기록 탭
  - 응답 페이지네이션 → "더 보기" 버튼 (단순)
  - 카드 표시 변경: supplier, totalAmount 추가
- [ ] localStorage 발주 기록 마이그레이션 → **하지 않음** (시연용 Mock 데이터라 무시)

#### G-3. 발주 모달 (LowestPriceDetailPage)
- [ ] 발주 확인 모달에 필드 추가
  - **수량** (기존 유지)
  - **공급자** (`supplier`) — 외부 사이트 방문 플랫폼명을 기본값으로 채움
  - **메모** (`memo`) — 선택 입력 (textarea)
- [ ] "발주 추가" 클릭 시:
  ```ts
  createPurchaseOrder({
    ingredientId, orderedAt: today, quantity, baseUnit: "kg",
    unitPrice, supplier, memo
  })
  ```
- [ ] 성공 시 `/order?tab=history` 이동

#### G-4. buy-signal (옵션)
- [ ] `hooks/useBuySignalAlert.ts` 신규
- [ ] OrderPage 진입 시 재고 부족 TOP 1 의 `/prices/{id}/trend` 호출
- [ ] `currentBuySignal: true` 면 기존 더미 모달을 실데이터로 교체
- [ ] `signalReason` 텍스트를 모달 본문에 표시

### Phase H — 검증 (1h)
- [ ] 백엔드 기동 확인
- [ ] 데모 로그인 → 사이드바 "demo" 표시
- [ ] `/lowest-price` → 카드에 실 가격 표시
- [ ] 재료 클릭 → 상세 화면 (KAMIS + 3채널 + 30일 차트) 정상 표시
- [ ] 외부 사이트 → 복귀 → 발주 모달 → 발주 추가
- [ ] `/order?tab=history` → 방금 발주 항목 표시
- [ ] `/order` 재고 부족 카드에 grade·dailyAvgSales 표시

---

## 5. 총 작업 시간

| Phase | 작업 | 시간 |
|---|---|---|
| A | 인프라 | 1h |
| B | LoginPage | 2h |
| C | OnboardPage | 2h |
| D | MainPage | 0.5h |
| E | LowestPricePage | 2h |
| F | LowestPriceDetailPage | 1h |
| G | OrderPage | 3h |
| H | 검증 | 1h |
| **합계** |  | **12.5h** |

---

## 6. Git commit 권장

```
A: feat(api): connect to backend via env baseURL + token auto-injection
B: refactor(auth): switch to real /api/users API (3-field signup)
C: feat(onboard): add category selection after signup
D: refactor(main): show username from auth storage in sidebar
E: refactor(stock): switch LowestPricePage to /prices/lowest-top
F: refactor(price-detail): wire /prices/{id} (Mock 풀기)
G: refactor(order): switch to /ingredients/low-stock + /purchase-orders + supplier/memo
H: chore: verification with demo account
```

---

## 7. 백엔드에 추가 확인 요청할 사항 (작업 중 발생 시)

작업 진행하면서 다음이 막히면 sim 또는 백엔드 담당자에게 즉시 문의:

1. 카테고리 enum 정확한 값 — `KOREAN`, `WESTERN` 외에 무엇이 있나?
2. `/prices/lowest-top` 응답 필드 — `image`, `category` 포함되나? 시안 카드에 카테고리 뱃지 표시 필요
3. `POST /purchase-orders` 의 `baseUnit` 검증 — 백엔드가 ingredient의 baseUnit과 매칭 검사하는지?
4. `/api/users/me` 추후 추가 가능성 — 매장명·ownerName 표시 필요
5. CORS 5173 외 추가 포트 (5174 등) 필요 시
6. 데모 계정 데이터 상태 — `/ingredients/low-stock` 호출 시 데이터 채워져 있는지

---

## 8. 범위 밖 (별도 plan)

### plan_kim_0604_03 (예정)
- 메뉴 도메인 (`MenuPage`, `/menus` CRUD)
- 판매(POS) 도메인 (`SalesPage`, `POST /orders`)
- FCM 푸시 (Firebase SDK + `/api/users/fcm-token`)
- 발주서 Excel 다운로드 (`GET /purchase-orders/export`)
- Refresh token 재발급 흐름

---

## 9. 리뷰 메모 공간

_수정 사항 있으면 적어주세요._

-
-
-
