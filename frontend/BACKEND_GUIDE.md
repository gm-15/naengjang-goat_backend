# 냉장G.O.A.T — 백엔드 연동 가이드

> 대상: 백엔드 개발자, 프론트엔드 통합 담당자
> 버전: 2026-04-23 기준 (plan_kim_0423_01 + plan_kim_0423_02 반영)
> 프로젝트 루트: `C:\Users\User\Downloads\냉장G.O.A.T (1)\`

---

## 목차
1. [개요](#1-개요)
2. [프론트엔드 아키텍처](#2-프론트엔드-아키텍처)
3. [API 엔드포인트 명세](#3-api-엔드포인트-명세)
4. [데이터 모델 & DB 스키마 제안](#4-데이터-모델--db-스키마-제안)
5. [인증](#5-인증)
6. [외부 연동 (KAMIS / 쇼핑몰)](#6-외부-연동-kamis--쇼핑몰)
7. [Mock → 실제 API 전환 가이드 (프론트 관점)](#7-mock--실제-api-전환-가이드-프론트-관점)
8. [로컬 저장소 마이그레이션](#8-로컬-저장소-마이그레이션)
9. [주의사항 & 엣지케이스](#9-주의사항--엣지케이스)
10. [환경 설정](#10-환경-설정)
11. [인수 테스트 시나리오](#11-인수-테스트-시나리오)
12. [참고 파일 위치](#12-참고-파일-위치)

---

## 1. 개요

### 앱 설명
- **대상**: 소상공인(치킨집·카페·음식점 등)
- **핵심 가치**: KAMIS 공식 시세 + 온라인 쇼핑몰(쿠팡/네이버/컬리) 가격을 한 화면에서 비교, 재고 부족 상품 알림, 발주 기록 관리
- **플랫폼**: 모바일 우선 반응형 웹 (Vite + React 18), 데스크탑도 지원

### 핵심 플로우
1. 사용자 회원가입 (가게 정보 포함) → 로그인
2. 최저가 페이지에서 재료 관리 (추가/삭제)
3. 재료 상세 진입 → KAMIS 시세 + 플랫폼별 가격 확인 → 외부 쇼핑몰 방문 → 복귀 시 "발주 확인" 모달에서 수량 입력 → 발주 기록 저장
4. 발주 페이지에서 재고 부족 TOP5 및 30일 가격 추세 차트 확인, 발주 기록 조회/삭제

---

## 2. 프론트엔드 아키텍처

### 기술 스택
| 영역 | 기술 |
|---|---|
| 번들러 | Vite 6 |
| UI | React 18 + TypeScript (tsconfig 없음, esbuild 트랜스파일) |
| 스타일 | Tailwind CSS v4 + shadcn/ui |
| 라우팅 | react-router v7 |
| 데이터 페칭 | **@tanstack/react-query v5** |
| 차트 | recharts 2.15 |
| 애니메이션 | motion (구 framer-motion) |

### 데이터 흐름 (중요)
```
┌──────────────┐     ┌───────────────┐     ┌──────────────┐
│ Page (.tsx)  │ ──► │ Custom Hook   │ ──► │ API Function │
│              │     │ (React Query) │     │ (추상 레이어)   │
└──────────────┘     └───────────────┘     └──────┬───────┘
                                                  │
                                   ┌──────────────┴──────────────┐
                                   │                              │
                              현재: Mock 데이터            나중에: fetch (apiClient)
```

**원칙**: 페이지 컴포넌트는 `fetch`를 직접 호출하지 않음. 모든 외부 통신은 `src/app/api/*.ts`를 통과.
**확장성**: 백엔드 연동 시 페이지 코드는 **건드리지 않고** `api/*.ts` 내부만 교체.

### 디렉토리 맵 (백엔드가 이해해야 할 영역)
```
src/app/
├── types/          ← 도메인 타입 (백엔드 DB 스키마와 1:1 매칭)
├── api/            ← ★백엔드가 맞춰야 할 계약
├── hooks/          ← React Query 훅 (수정 불필요)
├── lib/
│   ├── mock-data.ts      (배포 시 제거 가능)
│   ├── query-client.ts   (재시도, staleTime 설정)
│   └── price-history.ts  (seeded random, 프론트 전용 Mock 생성)
```

---

## 3. API 엔드포인트 명세

> **공통 규약**
> - 모든 요청: `Content-Type: application/json`
> - 인증 필요 엔드포인트: `Authorization: Bearer <token>` 헤더
> - 응답은 JSON (성공)
> - 실패: 상태 코드 + 에러 메시지 본문 (string 또는 `{ error: string }`)
> - 날짜: ISO 8601 (`YYYY-MM-DD` 또는 `YYYY-MM-DDTHH:mm:ssZ`)
> - 금액: **정수 (원 단위)**. 소수점 없음.

### 3-1. 인증 (Auth)

#### `POST /auth/login` — 로그인
- **인증**: 불필요
- **Request**:
  ```json
  {
    "email": "demo@example.com",
    "password": "****",
    "autoLogin": false
  }
  ```
- **Response 200**:
  ```json
  {
    "user": {
      "id": 1,
      "username": "demo",
      "email": "demo@example.com",
      "name": "홍길동",
      "storeName": "데모 치킨",
      "storeAddress": "서울시 강남구 테헤란로 123",
      "openTime": "09:00",
      "closeTime": "22:00",
      "orderDay": "월",
      "businessType": "음식점"
    },
    "token": "eyJhbGci..."
  }
  ```
- **Response 401**: 잘못된 자격증명

#### `POST /auth/signup` — 회원가입
- **인증**: 불필요
- **Request**: `SignupPayload` (아래 타입 정의 참고)
- **Response 200**: 로그인과 동일 (`{ user, token }`)
- **Response 409**: 이미 존재하는 이메일/username

#### `POST /auth/logout` — 로그아웃
- **인증**: 필요 (현재 토큰 무효화)
- **Request**: 빈 본문
- **Response 204**: No Content
- **주의**: 현재 프론트는 `localStorage.clear()`만 수행. 토큰 저장 로직 추가 시 이 엔드포인트 호출 후 로컬 토큰 제거.

---

### 3-2. 재료 (Ingredients)

#### `GET /ingredients` — 내 재료 목록
- **인증**: 필요
- **Query params**: *(향후 확장 여지)*
  - `category?: string` — 서버 사이드 필터 (현재 프론트는 클라이언트 필터링)
  - `search?: string`
- **Response 200**: `Ingredient[]`
  ```json
  [
    {
      "id": 1,
      "name": "닭고기 (1kg)",
      "category": "육류",
      "price": 8500,
      "unit": "kg",
      "supplier": "A업체",
      "monthlyAvgPrice": 8750
    }
  ]
  ```
- **비고**: 사용자별 재료 목록 (tenant = user_id).

#### `POST /ingredients` — 재료 추가
- **인증**: 필요
- **Request**: `CreateIngredientPayload`
  ```json
  {
    "name": "마늘 (500g)",
    "category": "채소",
    "price": 4800,
    "unit": "g",
    "supplier": "B업체",
    "monthlyAvgPrice": 4650  // optional, 없으면 서버가 price로 초기화 권장
  }
  ```
- **Response 200**: 생성된 `Ingredient` (id 포함)
- **Response 400**: 필수 필드 누락, 가격 음수 등

#### `DELETE /ingredients/:id` — 재료 삭제
- **인증**: 필요
- **Response 204**: No Content
- **Response 404**: 존재하지 않거나 타 사용자의 재료
- **주의**: **Soft delete 권장**. 과거 발주 기록(`OrderRecord`)이 재료 id를 참조하므로, hard delete 시 기록 조회가 깨짐.

---

### 3-3. 가격 (Prices)

#### `GET /prices/:id` — 특정 재료의 시세·플랫폼 가격·가격 히스토리
- **인증**: 필요
- **Response 200**: `ProductData`
  ```json
  {
    "id": 1,
    "name": "닭고기 (1kg)",
    "category": "육류",
    "image": "https://.../chicken.jpg",
    "kamisPrice": 8900,
    "kamisDate": "2026-04-14",
    "sources": [
      { "platform": "쿠팡",   "price": 8500, "url": "https://coupang.com/...", "logo": "🛒" },
      { "platform": "네이버", "price": 8800, "url": "https://naver.com/...",   "logo": "🟢" },
      { "platform": "마켓컬리","price": 9200, "url": "https://kurly.com/...",   "logo": "🥬" }
    ],
    "priceHistory": [
      { "date": "2026-03-25", "price": 9100 },
      { "date": "2026-03-26", "price": 8950 },
      "... 30개"
    ]
  }
  ```
- **주의**:
  - `priceHistory`는 **최근 30일치 일별 가격**. 배열 길이는 정확히 30 권장 (프론트가 주/월 평균 자동 계산).
  - `priceHistory[n-1]`이 가장 최근, `[0]`이 가장 오래됨.
  - `sources.logo`는 이모지 또는 이미지 URL 가능 (프론트는 문자열 그대로 출력).

---

### 3-4. 발주 (Orders)

#### `GET /orders/low-stock` — 재고 부족 상품 (TOP 5)
- **인증**: 필요
- **Response 200**: `LowStockItem[]`
  ```json
  [
    {
      "id": 1,
      "name": "닭고기 (1kg)",
      "category": "육류",
      "currentStock": 5,
      "optimalStock": 50,
      "unit": "kg",
      "urgencyLevel": "high",
      "lastOrdered": "2026-04-10"
    }
  ]
  ```
- **비고**:
  - 정렬: 서버가 `urgencyLevel` 우선으로 정렬하여 반환 권장 (high → medium → low).
  - `id`는 재료 id와 동일해야 함 (카드 클릭 시 `/lowest-price/:id`로 이동).
  - `urgencyLevel` 산정 기준 (제안):
    - `high`: currentStock / optimalStock < 0.3
    - `medium`: 0.3 ~ 0.5
    - `low`: 0.5 ~ 1.0
  - 현재 재고 관리 UI(증감 입력)는 프론트에 **없음**. POS 시스템 연동 또는 별도 재고 입력 화면이 필요할 수 있음 → **백엔드 쪽과 논의 필요**.

#### `POST /orders` — 발주 확정 (현재 프론트 미사용)
- 현재 `confirmOrder(items)` 훅은 정의돼 있으나 UI 연결 없음. 향후 확장 여지.

---

### 3-5. 발주 기록 (Order History) ★**localStorage → 서버 이전 필요**

현재 `localStorage["naengjang_goat__order_history_v1"]`에 저장되는 영역. 다음 엔드포인트로 이전.

#### `GET /orders/history` — 내 발주 기록
- **인증**: 필요
- **Query params**:
  - `from?: YYYY-MM-DD`
  - `to?: YYYY-MM-DD`
  - `limit?: number` (기본 100)
- **Response 200**: `OrderRecord[]` — **최신이 배열 앞**에 오도록 정렬
  ```json
  [
    {
      "id": 1713850800000,
      "orderedAt": "2026-04-23T14:30:00Z",
      "ingredientId": 1,
      "ingredientName": "닭고기 (1kg)",
      "platform": "쿠팡",
      "price": 8500,
      "unit": "kg",
      "quantity": 10
    }
  ]
  ```
- **비고**:
  - `ingredientName`은 **발주 시점의 스냅샷**. 재료가 나중에 이름 변경/삭제되어도 기록에서는 원본 유지.
  - `id`는 현재 Mock에서 `Date.now()` 사용. 실 서버는 bigint/UUID 아무거나 OK (프론트는 단순 key로만 사용).

#### `POST /orders/history` — 발주 기록 저장
- **인증**: 필요
- **Request**: `CreateOrderRecordPayload`
  ```json
  {
    "ingredientId": 1,
    "ingredientName": "닭고기 (1kg)",
    "platform": "쿠팡",
    "price": 8500,
    "unit": "kg",
    "quantity": 10
  }
  ```
- **Response 200**: 생성된 `OrderRecord` (`id`, `orderedAt` 서버 채움)
- **주의**:
  - `orderedAt`은 서버가 `new Date().toISOString()` 으로 채워 반환.
  - **idempotency**: 같은 사용자가 동일 재료·플랫폼·수량을 1초 내 2번 저장해도 모두 기록으로 남김 (모달 더블 클릭 방지는 프론트에서 `disabled` 처리 중).

#### `DELETE /orders/history/:id` — 발주 기록 삭제
- **인증**: 필요
- **Response 204**: No Content
- **주의**: hard delete 또는 soft delete는 백엔드 판단. 프론트는 응답 기준으로만 판별.

---

## 4. 데이터 모델 & DB 스키마 제안

> PostgreSQL 기준 SQL 예시. 다른 DB도 개념은 동일.

### 4-1. `users`
```sql
CREATE TABLE users (
  id             BIGSERIAL PRIMARY KEY,
  username       VARCHAR(64) UNIQUE NOT NULL,
  password_hash  VARCHAR(255) NOT NULL,  -- bcrypt/argon2
  email          VARCHAR(255) UNIQUE NOT NULL,
  name           VARCHAR(64) NOT NULL,
  store_name     VARCHAR(128) NOT NULL,
  store_address  TEXT NOT NULL,
  open_time      TIME NOT NULL,
  close_time     TIME NOT NULL,
  order_day      VARCHAR(2) NOT NULL,    -- '월','화',...
  business_type  VARCHAR(16) NOT NULL,   -- '카페','음식점',...
  created_at     TIMESTAMPTZ DEFAULT NOW(),
  updated_at     TIMESTAMPTZ DEFAULT NOW()
);
```

### 4-2. `ingredients`
```sql
CREATE TABLE ingredients (
  id                   BIGSERIAL PRIMARY KEY,
  user_id              BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name                 VARCHAR(128) NOT NULL,
  category             VARCHAR(16) NOT NULL,   -- '육류','채소','소스/양념','유제품','기타'
  price                INTEGER NOT NULL CHECK (price >= 0),   -- 원
  unit                 VARCHAR(4) NOT NULL,    -- 'kg','g','L','개'
  supplier             VARCHAR(128) NOT NULL,
  monthly_avg_price    INTEGER NOT NULL CHECK (monthly_avg_price >= 0),
  deleted_at           TIMESTAMPTZ,           -- soft delete
  created_at           TIMESTAMPTZ DEFAULT NOW(),
  updated_at           TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_ingredients_user ON ingredients(user_id) WHERE deleted_at IS NULL;
```

### 4-3. `price_history` (시계열)
```sql
CREATE TABLE price_history (
  id              BIGSERIAL PRIMARY KEY,
  ingredient_id   BIGINT NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
  date            DATE NOT NULL,
  price           INTEGER NOT NULL,
  source          VARCHAR(16) NOT NULL,   -- 'kamis', 'coupang', 'naver', 'kurly', ...
  UNIQUE(ingredient_id, date, source)
);
CREATE INDEX idx_price_history_lookup ON price_history(ingredient_id, date DESC);
```
- **주의**: 프론트 `priceHistory`는 단일 시리즈(하나의 가격만). 서버는 플랫폼별 히스토리를 갖되 응답 시 **가장 대표적인 가격(예: KAMIS) 하나만** `priceHistory`로 내려주기.

### 4-4. `kamis_cache` (외부 API 캐시)
```sql
CREATE TABLE kamis_cache (
  product_code   VARCHAR(32) PRIMARY KEY,
  price          INTEGER NOT NULL,
  as_of_date     DATE NOT NULL,
  fetched_at     TIMESTAMPTZ DEFAULT NOW()
);
```

### 4-5. `order_records`
```sql
CREATE TABLE order_records (
  id                BIGSERIAL PRIMARY KEY,
  user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  ingredient_id     BIGINT REFERENCES ingredients(id) ON DELETE SET NULL,
  ingredient_name   VARCHAR(128) NOT NULL,   -- 스냅샷
  platform          VARCHAR(32) NOT NULL,
  price             INTEGER NOT NULL,        -- 발주 당시 단가
  unit              VARCHAR(4) NOT NULL,
  quantity          INTEGER NOT NULL CHECK (quantity > 0),
  ordered_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_orders_user_date ON order_records(user_id, ordered_at DESC);
```
- **ingredient_id는 ON DELETE SET NULL** — 재료가 삭제돼도 기록은 보존.
- 프론트는 `orderedAt` 문자열로 받음. DB는 TIMESTAMPTZ로 저장 → ISO 문자열 변환 후 응답.

### 4-6. 재고 관련 (확장용, 현재 API는 모의 값)
실제 재고 관리 기능이 필요하면:
```sql
CREATE TABLE stock_snapshots (
  id              BIGSERIAL PRIMARY KEY,
  ingredient_id   BIGINT NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
  current_stock   NUMERIC(10,2) NOT NULL,
  optimal_stock   NUMERIC(10,2) NOT NULL,
  recorded_at     TIMESTAMPTZ DEFAULT NOW()
);
```
- `GET /orders/low-stock`은 각 ingredient의 최신 stock_snapshot 기준으로 계산.

---

## 5. 인증

### 현재 프론트 상태
- 로그인/회원가입 훅(`useLogin`, `useSignup`)은 응답에서 `{ user, token }`을 받지만 **토큰을 저장하는 로직은 아직 없음**.
- `apiClient` 모듈(`src/app/api/client.ts`)은 `Authorization: Bearer <token>` 헤더를 지원하지만, 현재는 호출부에서 token을 넘기지 않음.

### BE 구현자 관점
- **JWT 권장**. `exp` 포함. Refresh token은 필요 시 별도 논의.
- CORS: dev 환경에서 `Access-Control-Allow-Origin: http://localhost:5173` 허용 필요.
- HTTPS: prod 필수.

### FE 측 추가 작업 (백엔드 연동 시 함께 수행)
1. `src/app/providers/AppProviders.tsx` 또는 새 `AuthContext`에서 토큰 상태 관리.
2. `useLogin` / `useSignup` 성공 시 token을 `localStorage["auth_token"]` 또는 `sessionStorage`에 저장.
3. `apiClient` 호출 시 저장된 토큰 자동 주입 (interceptor 역할).
4. 401 응답 시 자동 로그아웃 → `navigate("/")`.

---

## 6. 외부 연동 (KAMIS / 쇼핑몰)

### KAMIS (한국농수산식품유통공사)
- **공식 API 존재** — https://www.kamis.or.kr (Open API 신청 필요)
- API 키 발급 후 `kamis_cache` 테이블에 1일 1회 배치로 수집 권장.
- 재료명 ↔ KAMIS 품목 코드 매핑 테이블이 별도로 필요.

### 쿠팡 / 네이버 쇼핑 / 마켓컬리
- **공식 파트너 API는 일반 사용자에게 제공되지 않음**.
- 옵션:
  - (a) 쿠팡 파트너스 API (커미션 프로그램 가입자)
  - (b) 네이버 쇼핑 검색 API (제한적)
  - (c) 크롤링 — **법적·약관 이슈 있음**. 프로젝트 정책으로 결정 필요.
- `sources[].url` 은 각 플랫폼의 상품 상세 URL. 프론트는 새 탭으로 여는 것만 수행.
- **주의**: 현재 프론트 Mock은 쿠팡 홈(`https://www.coupang.com`)으로만 이동. 실제로는 딥링크된 상품 페이지 URL이 와야 함.

### 이미지 (`sources[].logo`, `ProductData.image`)
- 현재 Mock은 Unsplash/이모지 사용.
- 실 서비스는 CDN에 로고 이미지 호스팅 또는 이모지 문자열 유지.

---

## 7. Mock → 실제 API 전환 가이드 (프론트 관점)

### 7-1. 환경 변수 설정
`.env.local` 생성:
```
VITE_API_BASE_URL=https://api.naengjang-goat.example.com
```

### 7-2. 각 `src/app/api/*.ts` 전환
모든 API 파일은 **두 섹션**으로 구성됨:
- 현재: `sleep()` + Mock 데이터 반환
- 주석: `// return apiClient.xxx(...)` — 실 API 호출 라인

예) `src/app/api/ingredients.ts`
```ts
// 현재 (Mock):
export async function listIngredients(): Promise<Ingredient[]> {
  await sleep(200);
  return [...store];
  // return apiClient.get<Ingredient[]>("/ingredients");
}

// 전환 후:
export async function listIngredients(): Promise<Ingredient[]> {
  return apiClient.get<Ingredient[]>("/ingredients");
}
```

### 7-3. 전환 작업 목록
- [ ] `api/auth.ts` — login, signup, logout
- [ ] `api/ingredients.ts` — list, create, delete
- [ ] `api/prices.ts` — getPriceDetail
- [ ] `api/orders.ts` — getLowStock, confirmOrder
- [ ] `api/order-history.ts` — list, save, delete
- [ ] `api/client.ts` — 토큰 자동 주입 로직 추가
- [ ] `lib/mock-data.ts` — 배포 시 제거 (import 0개 확인 후)
- [ ] `lib/price-history.ts` — `generatePriceHistory` 호출부(`api/prices.ts`) 삭제 후 이 파일도 제거 가능

### 7-4. 페이지 코드는 전혀 건드리지 않음
React Query 훅이 페이지와 API 사이의 경계를 담당하므로, 페이지는 **수정 불필요**.

---

## 8. 로컬 저장소 마이그레이션

### 현재 상태
- 발주 기록이 `localStorage["naengjang_goat__order_history_v1"]` 에 저장됨.
- 키 접미사 `_v1` — 스키마 변경 대비.

### 서버 이전 시 권장 플로우
1. 사용자 로그인 성공 시점에 localStorage 확인:
   ```ts
   const local = JSON.parse(localStorage.getItem("naengjang_goat__order_history_v1") || "[]");
   if (local.length > 0) {
     await apiClient.post("/orders/history/bulk-import", { records: local });
     localStorage.removeItem("naengjang_goat__order_history_v1");
   }
   ```
2. 서버는 `POST /orders/history/bulk-import` 엔드포인트를 제공 (idempotent 권장 — 동일 `id` 무시 또는 덮어쓰기).
3. 일단 이전되면 그 뒤론 모든 CRUD는 서버 API로.

### 백엔드에 추가 엔드포인트 제안
#### `POST /orders/history/bulk-import`
- **Request**: `{ records: OrderRecord[] }`
- **Response**: `{ imported: number, skipped: number }`

---

## 9. 주의사항 & 엣지케이스

### 9-1. 낙관적 업데이트 (Optimistic Update)
- `useAddIngredient`, `useDeleteIngredient`, `useDeleteOrderRecord` 훅은 **응답을 기다리지 않고 UI를 먼저 갱신**.
- 서버 에러 시 자동 롤백.
- **백엔드 주의**: 409/500 에러를 확실히 반환해야 롤백 트리거 됨. 200을 반환하면서 본문에만 에러 넣으면 안 됨.

### 9-2. 날짜·타임존
- 프론트는 `new Date().toISOString()` 사용 → **UTC** 전송.
- 서버도 UTC 저장, 응답 시 UTC ISO 8601 문자열.
- 프론트 표시는 로컬 타임존으로 자동 변환됨 (`new Date(iso).getHours()`).
- **주의**: KAMIS 날짜(`kamisDate`)와 `lastOrdered`는 시간 없이 `YYYY-MM-DD` 문자열.

### 9-3. 금액 정밀도
- 모두 **정수(원)**. `8500.5원` 같은 값이 오면 안 됨.
- `Math.round` 후 저장.

### 9-4. 빈 상태 / 대용량
- `GET /ingredients` 빈 배열 정상 처리 (프론트: EmptyState 표시).
- 발주 기록이 수천 개 쌓일 수 있음 → 페이지네이션 대비 쿼리 파라미터(`limit`, `cursor`) 지원 권장. 현재 프론트는 전체 조회 기준.

### 9-5. 동시성
- 같은 사용자가 두 탭에서 동시 발주 시: `POST /orders/history` 두 번 호출되어 기록 2개 남음. **의도된 동작**. 중복 방지는 하지 않음.

### 9-6. 에러 응답 포맷
- 프론트 `apiClient` (`src/app/api/client.ts`)는:
  ```ts
  if (!res.ok) {
    const message = await res.text().catch(() => res.statusText);
    throw new Error(`API ${res.status}: ${message}`);
  }
  ```
- 즉, **응답 본문이 text()든 JSON이든 OK**. JSON이면 `{ "error": "메시지" }` 권장.

### 9-7. React Query 기본 설정
`src/app/lib/query-client.ts`:
```ts
staleTime: 60_000,           // 1분간 캐시 신선
retry: 1,                     // 실패 시 1회 재시도
refetchOnWindowFocus: true,   // 탭 전환 시 자동 리페치
```
- 백엔드 부하 고려. 한 사용자가 탭 포커스 할 때마다 핵심 엔드포인트(`/ingredients`, `/orders/low-stock`)가 재호출됨.

### 9-8. Soft Delete
- `ingredients` 테이블은 soft delete 권장. hard delete 시 과거 `order_records`의 조인이 깨짐.
- `deleted_at IS NOT NULL` 인 재료는 `GET /ingredients`에서 제외.

### 9-9. 재료 카테고리·단위 Enum
- `category`: 정확히 `"육류" | "채소" | "소스/양념" | "유제품" | "기타"`
- `unit`: 정확히 `"kg" | "g" | "L" | "개"`
- 백엔드가 다른 값을 저장하면 프론트 필터가 동작하지 않음. DB CHECK 제약 권장.

---

## 10. 환경 설정

### Dev (로컬)
```
# .env.local
VITE_API_BASE_URL=http://localhost:8000
```
백엔드는 `8000` 포트 예시. CORS에 `http://localhost:5173` 허용.

### Staging / Prod
```
VITE_API_BASE_URL=https://api.naengjang-goat.example.com
```
- HTTPS 필수
- SameSite=Lax 쿠키(Refresh token 용) 고려 시 백엔드 쿠키 도메인 정책 수립

### 빌드
```bash
npm run build    # vite build → dist/
```
결과물 `dist/`는 CDN/정적 호스팅(S3+CloudFront, Vercel, Netlify 등)에 배포.

---

## 11. 인수 테스트 시나리오

### 시나리오 A — 기본 플로우
1. 회원가입 → 자동 로그인 → `/main` 진입
2. `/lowest-price` → 재료 목록 조회 (서버에서 가져옴)
3. "재료 추가" → 신규 재료 POST → 낙관적으로 카드 즉시 표시 → 응답 수신 후 id 확정
4. 특정 재료 카드 클릭 → `/lowest-price/:id` → 가격 상세 + KAMIS + 플랫폼 조회
5. "쿠팡 구매하러 가기" → 새 탭에서 쿠팡 열림 → 탭 복귀
6. "발주 확인" 모달 자동 노출 → 수량 10 입력 → "발주 추가"
7. `/order?tab=history` 자동 이동 → 방금 항목이 "오늘" 그룹에 표시 → 서버 저장 확인

### 시나리오 B — 에러 처리
1. 네트워크 끊김 상태에서 "재료 추가" → 낙관적으로 UI 추가 → 서버 에러 → **롤백되어 카드 사라짐**
2. 만료된 토큰으로 `GET /ingredients` → 401 → 프론트가 401 핸들링(자동 로그아웃) — 이 로직은 토큰 연동 시점에 추가 필요

### 시나리오 C — 데이터 일관성
1. A 탭에서 재료 삭제 → B 탭에서 새로고침 시 해당 재료 없음
2. 발주 기록 휴지통 → 확인 팝업 → 삭제 → 서버 DELETE → 목록에서 제거

### 시나리오 D — 차트
1. 발주 페이지 진입 → 상단 드롭다운에서 재료 선택 → 30일 가격 추세 차트 렌더
2. 차트에 `priceHistory` 배열이 정확히 30일치로 반영되는지 확인
3. 월평균/주평균 라인이 실제 값과 일치하는지 (프론트가 계산, 서버는 raw 데이터만 제공)

---

## 12. 참고 파일 위치

### 백엔드 개발자가 먼저 읽어야 할 파일
| 우선순위 | 파일 | 내용 |
|---|---|---|
| 1 | `src/app/types/ingredient.ts` | `Ingredient`, `ProductData`, `PricePoint` 타입 |
| 1 | `src/app/types/order.ts` | `LowStockItem`, `OrderRecord`, `CreateOrderRecordPayload` |
| 1 | `src/app/types/user.ts` | `User`, `SignupPayload`, `LoginPayload` |
| 2 | `src/app/api/*.ts` | 각 엔드포인트의 Mock 구현 — 실제 계약 동일 |
| 3 | `src/app/lib/query-client.ts` | React Query 기본 옵션 |
| 3 | `src/app/api/client.ts` | fetch wrapper, 인증 토큰 주입 규약 |

### 프론트 개발자가 연동 시 수정할 파일
- `src/app/api/*.ts` (6개) — Mock 제거 + `apiClient.xxx` 활성화
- `src/app/providers/AppProviders.tsx` — AuthContext 추가 시
- `src/app/api/client.ts` — 토큰 자동 주입 interceptor

### 이전 구현 히스토리
- `plan_kim_0423_01.md` — 초기 아키텍처 (API 추상 레이어, React Query, 반응형)
- `plan_kim_0423_02.md` — 가격 추세 차트 + 발주기록 + PDF 제거

---

## 부록 A. TypeScript 타입 원본 (복붙용)

### Enum 타입
```ts
type IngredientCategory = "육류" | "채소" | "소스/양념" | "유제품" | "기타";
type IngredientUnit = "kg" | "g" | "L" | "개";
type UrgencyLevel = "high" | "medium" | "low";
type BusinessType = "카페" | "음식점" | "베이커리" | "편의점" | "마트" | "기타";
type Weekday = "월" | "화" | "수" | "목" | "금" | "토" | "일";
```

### 엔티티 타입
```ts
interface User {
  id: number;
  username: string;
  email: string;
  name: string;
  storeName: string;
  storeAddress: string;
  openTime: string;       // "HH:mm"
  closeTime: string;      // "HH:mm"
  orderDay: Weekday;
  businessType: BusinessType;
}

interface Ingredient {
  id: number;
  name: string;
  category: IngredientCategory;
  price: number;          // 정수(원)
  unit: IngredientUnit;
  supplier: string;
  monthlyAvgPrice: number;
}

interface PriceSource {
  platform: string;
  price: number;
  url: string;
  logo: string;           // 이모지 또는 이미지 URL
}

interface PricePoint {
  date: string;           // "YYYY-MM-DD"
  price: number;
}

interface ProductData {
  id: number;
  name: string;
  category: IngredientCategory;
  image: string;
  kamisPrice: number;
  kamisDate: string;      // "YYYY-MM-DD"
  sources: PriceSource[];
  priceHistory: PricePoint[];   // 30개 (최근이 맨 뒤)
}

interface LowStockItem {
  id: number;             // 재료 id와 동일
  name: string;
  category: IngredientCategory;
  currentStock: number;
  optimalStock: number;
  unit: IngredientUnit;
  urgencyLevel: UrgencyLevel;
  lastOrdered: string;    // "YYYY-MM-DD"
}

interface OrderRecord {
  id: number;
  orderedAt: string;      // ISO 8601 (UTC)
  ingredientId: number;
  ingredientName: string; // 스냅샷
  platform: string;
  price: number;          // 당시 단가
  unit: string;
  quantity: number;
}
```

### Payload 타입 (요청 본문)
```ts
interface LoginPayload {
  email: string;
  password: string;
  autoLogin?: boolean;
}

interface SignupPayload {
  username: string;
  password: string;
  email: string;
  name: string;
  storeName: string;
  storeAddress: string;
  openTime: string;
  closeTime: string;
  orderDay: Weekday;
  businessType: BusinessType;
}

interface CreateIngredientPayload {
  name: string;
  category: IngredientCategory;
  price: number;
  unit: IngredientUnit;
  supplier: string;
  monthlyAvgPrice?: number;
}

interface CreateOrderRecordPayload {
  ingredientId: number;
  ingredientName: string;
  platform: string;
  price: number;
  unit: string;
  quantity: number;
}
```

---

## 부록 B. 체크리스트 (백엔드 킥오프 시)

### Phase 1 — 최소 동작 (auth + ingredients + history)
- [ ] DB 스키마 마이그레이션 (`users`, `ingredients`, `order_records`)
- [ ] JWT 인증 미들웨어
- [ ] `POST /auth/signup`, `POST /auth/login`
- [ ] `GET /ingredients`, `POST /ingredients`, `DELETE /ingredients/:id`
- [ ] `GET /orders/history`, `POST /orders/history`, `DELETE /orders/history/:id`
- [ ] CORS 설정
- [ ] 프론트 `src/app/api/*.ts` 전환

### Phase 2 — 가격/재고 (실 데이터)
- [ ] KAMIS Open API 키 발급 + 배치 수집 파이프라인
- [ ] `price_history` 테이블
- [ ] `GET /prices/:id` (KAMIS + 플랫폼 가격 취합)
- [ ] `GET /orders/low-stock` (재고 관리 UI 확정 후)

### Phase 3 — 선택
- [ ] Refresh token
- [ ] `POST /orders/history/bulk-import` (마이그레이션용)
- [ ] 페이지네이션
- [ ] 감사 로그 (audit trail)

---

**문의 / 수정**: plan_kim_XXXX_XX.md 를 새로 추가하여 변경을 제안.
