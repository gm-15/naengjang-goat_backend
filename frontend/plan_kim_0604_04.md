# plan_kim_0604_04 — 발주 도메인 완전 정상화 + 회원가입 후 가게 세팅

작성일: 2026-06-04
선행: `plan_kim_0604_03.md` (재고 관리 페이지 완료)
이번 범위: 계획 A — 발주 페이지 안정화 + 회원가입 흐름에 가게 세팅 추가

---

## 0. 확정된 결정사항

| 항목 | 결정 |
|---|---|
| 회원가입 흐름 | `회원가입 → 온보딩(카테고리) → /settings(가게 세팅) → /main` |
| 설정 페이지 | 신규 `/settings` — 강제 진입(가입 직후) + 사이드바 수정 진입 모두 가능 |
| 백엔드 LowStockService 403 버그 | 트랜잭션 어노테이션 수정 |
| 발주 집계 표시 | 메인 페이지 카드 상단에 위젯 |
| 발주 상태 변경 | 발주 기록 카드에 PENDING→CONFIRMED 버튼 |
| 범위 밖 | 메뉴/판매 도메인 (계획 B), 발주 필터/재발주 (계획 C 일부) |

---

## 1. 백엔드 활용 API

### 매장 설정 (Store Settings)
- `GET /settings` — 현재 매장 설정 조회
- `PUT /settings` — 설정 저장
- Request:
  ```json
  {
    "openTime":     "11:00",
    "closeTime":    "22:00",
    "orderDay":     "MON",
    "inventoryDay": "SUN"
  }
  ```
- `DayOfWeekType`: `MON / TUE / WED / THU / FRI / SAT / SUN`

### 발주 집계
- `GET /purchase-orders/summary?from=YYYY-MM-DD&to=YYYY-MM-DD`
- Response:
  ```json
  {
    "totalCount":  8,
    "totalAmount": 120000,
    "byIngredient": [
      { "ingredientName": "배추", "count": 3, "totalAmount": 45000 }
    ]
  }
  ```

### 발주 상태 변경 (확인 필요)
- 현재 가이드에 명시 안 됨. 추정 엔드포인트: `PATCH /purchase-orders/{id}/status?status=CONFIRMED`
- 작업 중 확인. 없으면 백엔드 추가 요청 또는 이번 작업 범위 밖.

---

## 2. 파일 변경

### 백엔드 (1파일)
- `LowStockService.java` — `@Transactional(readOnly = true)` 안의 try/catch 처리 수정

### 프론트엔드 신규
- `src/app/types/settings.ts`
- `src/app/api/settings.ts`
- `src/app/hooks/useStoreSettings.ts`
- `src/app/pages/SettingsPage.tsx`
- `src/app/components/common/PurchaseSummaryWidget.tsx`
- `src/app/api/purchase-summary.ts` (또는 기존 `purchase-orders.ts`에 추가)
- `src/app/hooks/usePurchaseSummary.ts`

### 프론트엔드 수정
- `src/app/routes.tsx` — `/settings` 추가
- `src/app/pages/OnboardPage.tsx` — 완료 후 `/settings` 자동 이동
- `src/app/pages/MainPage.tsx` — PurchaseSummaryWidget 추가
- `src/app/pages/OrderPage.tsx` — 발주 기록 카드에 상태 변경 버튼
- `src/app/components/common/AppShell.tsx` — 사이드바에 "설정" 항목

---

## 3. 상세 설계

### 3-1. SettingsPage (`/settings`)

**라우트 진입 경로**:
- (A) 가입 직후 자동: `OnboardPage` "시작하기" 성공 후 `/settings` 자동 이동
- (B) 메인 사이드바 → 톱니바퀴 아이콘
- (C) 헤더에서 수정 (메인에서 진입 가능)

**디자인** (모바일/데스크탑 반응형):
```
┌──────────────────────────────────────┐
│  매장 운영 설정                       │
│  발주 알림과 재고 계산에 사용됩니다     │
├──────────────────────────────────────┤
│  영업 시간                            │
│  오픈  [11:00]   마감  [22:00]        │
│                                       │
│  발주 요일                            │
│  [월 화 수 목 금 토 일]               │
│  ※ 정기 발주 알림이 발송되는 요일      │
│                                       │
│  재고 실사 요일                       │
│  [월 화 수 목 금 토 일]               │
│  ※ 재고 점검 알림이 발송되는 요일      │
│                                       │
│  [저장 후 시작하기]                   │
└──────────────────────────────────────┘
```

- `openTime`/`closeTime` — `<input type="time">`
- `orderDay`/`inventoryDay` — 요일 7개 버튼(택1, 토글)
- 저장 후 컨텍스트에 따라:
  - 가입 직후 첫 진입 → `/main` 이동
  - 일반 수정 → "저장되었습니다" 토스트 + 그 자리 머무름

**기본값**:
- `openTime`: "11:00"
- `closeTime`: "22:00"
- `orderDay`: "MON"
- `inventoryDay`: "SUN"

### 3-2. 흐름 분기 (가입 직후 vs 일반)

URL 쿼리 파라미터로 구분:
- `/settings?initial=1` — 가입 직후 강제 진입 (저장 후 `/main` 이동, 사이드바 안 보임)
- `/settings` — 일반 진입 (사이드바 보임, 저장 후 머무름)

OnboardPage 수정:
```ts
// 기존: navigate("/main")
navigate("/settings?initial=1")
```

### 3-3. PurchaseSummaryWidget

메인 페이지 상단(인사말 영역 아래)에 카드 1개:
```
┌─────────────────────────────────────┐
│ 📊 이번 달 발주                      │
│                                     │
│ 총 8건 · 120,000원                  │
│                                     │
│ 인기 재료 TOP 3                     │
│   배추   3건  45,000원              │
│   양파   2건  30,000원              │
│   마늘   2건  25,000원              │
│                                     │
│           [발주 페이지로 →]          │
└─────────────────────────────────────┘
```

- `usePurchaseSummary({ from: thisMonthStart, to: today })`
- 빈 경우 "이번 달 발주 기록이 없습니다" 표시

### 3-4. 발주 상태 변경

발주 기록 카드에 작은 액션 버튼:
- `status === "PENDING"` → "확정" 버튼 표시 → 클릭 시 `PATCH /purchase-orders/{id}/status?status=CONFIRMED`
- `status === "CONFIRMED"` → "취소" 버튼 표시 (관리용)
- 변경 후 invalidate

⚠️ 백엔드 엔드포인트 확인 후 실제 경로 확정. 없으면 해당 기능만 임시 제외 후 진행.

### 3-5. 백엔드 트랜잭션 수정

`LowStockService.java`:
```java
@Transactional(readOnly = true)  // 그대로
public List<LowStockItemDto> getTopLowStock(Long userId, int limit) {
    ...
    return ingredientIds.stream()
        .map(id -> {
            try {
                return depletionCalculatorService.calculate(...);
            } catch (Exception e) {
                log.warn(...);
                return null;
            }
        })
```

수정안 (가장 단순):
```java
// Transactional 어노테이션 자체 제거
public List<LowStockItemDto> getTopLowStock(Long userId, int limit) {
```

또는 `DepletionCalculatorService.calculate()` 에 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 추가해서 부분 실패 격리.

전자가 간단. 후자가 정석. 시연용으로 전자 선택.

---

## 4. 타입 정의

```ts
// types/settings.ts
export type DayOfWeek = "MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN";

export interface StoreSettings {
  openTime: string;        // "HH:mm:ss" 또는 "HH:mm"
  closeTime: string;
  orderDay: DayOfWeek;
  inventoryDay: DayOfWeek;
}

export interface UpdateStoreSettingsPayload {
  openTime: string;        // "HH:mm"
  closeTime: string;
  orderDay: DayOfWeek;
  inventoryDay: DayOfWeek;
}
```

---

## 5. 작업 체크리스트

### Phase A — 백엔드 패치 (5분)
- [ ] `LowStockService.java` `@Transactional(readOnly = true)` 제거 또는 try/catch 분리
- [ ] 백엔드 재기동
- [ ] `GET /ingredients/low-stock` 200 응답 확인

### Phase B — Settings 인프라 (20분)
- [ ] `types/settings.ts`
- [ ] `api/settings.ts` — `getStoreSettings()`, `updateStoreSettings(payload)`
- [ ] `hooks/useStoreSettings.ts` — `useStoreSettings()`, `useUpdateStoreSettings()`

### Phase C — SettingsPage UI (40분)
- [ ] `pages/SettingsPage.tsx` 신규
- [ ] AppShell variant="auth" (initial=1) vs variant="main" (일반) 분기
- [ ] 영업시간 input + 요일 토글 UI
- [ ] 저장 후 분기 이동

### Phase D — 흐름 연결 (10분)
- [ ] `routes.tsx` `/settings` 추가
- [ ] `OnboardPage.tsx` — 성공 후 `navigate("/settings?initial=1")`
- [ ] AppShell Sidebar에 "설정" 항목 추가

### Phase E — 발주 집계 위젯 (25분)
- [ ] `api/purchase-orders.ts` 에 `getSummary({ from, to })` 추가
- [ ] `hooks/usePurchaseSummary.ts`
- [ ] `components/common/PurchaseSummaryWidget.tsx`
- [ ] `MainPage.tsx` 에 위젯 추가

### Phase F — 발주 상태 변경 (15분, 백엔드 엔드포인트 확인 후)
- [ ] 백엔드 컨트롤러 확인 → 엔드포인트 존재 시 진행, 없으면 스킵
- [ ] `api/purchase-orders.ts` 에 `updateStatus(id, status)` 추가
- [ ] `hooks/usePurchaseOrders.ts` 에 mutation 추가
- [ ] OrderPage HistoryTab 카드에 상태 변경 버튼

### Phase G — 검증 (10분)
- [ ] 신규 가입 → 카테고리 → /settings(initial) → 영업시간/요일 입력 → /main
- [ ] /order 재고 부족 탭 200 (빈 배열이라도)
- [ ] 메인 페이지 위젯 표시
- [ ] 사이드바에서 설정 진입 → 저장 → 머무름

---

## 6. 총 작업 시간

| Phase | 시간 |
|---|---|
| A. 백엔드 트랜잭션 패치 | 5분 |
| B. Settings 타입/API/훅 | 20분 |
| C. SettingsPage UI | 40분 |
| D. 흐름 연결 | 10분 |
| E. 발주 집계 위젯 | 25분 |
| F. 발주 상태 변경 | 15분 |
| G. 검증 | 10분 |
| **합계** | **약 2시간 5분** |

---

## 7. 검증 시나리오

### 시나리오 1 — 신규 가입 풀 플로우
1. 회원가입 (kimtest02 / pwd / 김건우2)
2. → /onboard 자동 진입 → 한식·양식 선택 → "시작하기"
3. → **/settings?initial=1 자동 진입** ⭐
4. 영업시간 (10:00 ~ 23:00), 발주 요일 (월), 재고 실사 (일)
5. "저장 후 시작하기" → /main 이동
6. **메인 페이지에 빈 발주 집계 위젯 표시**

### 시나리오 2 — 기존 demo 계정
1. 로그인 (demo / demo1234)
2. /main 메인 페이지 표시
3. **발주 집계 위젯에 이전 발주 1건 표시** ("총 1건 0원" — 단가 0원이라 0원)
4. 사이드바 "설정" 클릭 → /settings (수정 모드)
5. 영업시간 수정 → "저장" → "저장되었습니다" 토스트 + 페이지 유지

### 시나리오 3 — 재고 부족 탭 회복
1. /order 진입 → 재고 부족 탭 → ⭐ **에러 없이 빈 상태 표시** (이전 403 → 200)
2. 가격 추세 차트는 그대로 정상

### 시나리오 4 — 발주 상태 변경 (F 작업 가능 시)
1. /order → 발주 기록 탭
2. PENDING 발주 → "확정" 버튼 클릭
3. 카드의 status가 CONFIRMED로 바뀜
4. 백엔드 DB의 `purchase_orders.status` 변경 확인

---

## 8. 리스크 & 확인 포인트

- **백엔드 트랜잭션 수정의 부작용**: `@Transactional` 제거 시 N+1 쿼리 가능성. 시연용 OK, 운영은 정석 패치 필요.
- **발주 상태 변경 엔드포인트 부재 시**: 해당 기능만 임시 비활성화 (버튼 회색 처리). 작업 중 백엔드 확인 후 결정.
- **`/settings` 초기 진입에서 사이드바 안 보임**: AppShell `variant="auth"` 사용 → 로그인 화면과 같은 풀스크린. 사용자 혼동 방지를 위해 "건너뛰기" 옵션 추가 (`/main` 직행).
- **시간 형식**: 백엔드는 `LocalTime` → JSON에서 `"HH:mm:ss"` 또는 `"HH:mm"` 가능. 프론트 `<input type="time">`은 `"HH:mm"` 출력 → 백엔드가 `"HH:mm"` 받는지 확인 필요. 안 받으면 `":00"` 추가.

---

## 9. 리뷰 메모 공간

_수정 사항 있으면 적어주세요. 없으면 "구현 시작해"_

-
-
-
