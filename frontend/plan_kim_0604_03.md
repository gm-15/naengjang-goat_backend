# plan_kim_0604_03 — 재고 관리 페이지 신규 추가

작성일: 2026-06-04
선행: `plan_kim_0604_02.md` (4개 화면 한정 실연동 완료 후)
이번 범위: **재고 관리 페이지 신규** — InventoryBatch 입고/조회

---

## 0. 확정된 결정사항

| 항목 | 결정 |
|---|---|
| 라우트 | `/inventory` 신규 |
| 사이드바 | "재고 관리" 메뉴 항목 추가 (재료 아이콘) |
| 핵심 기능 | 재료별 현재 재고 잔량 표시 + 입고 등록 |
| 백엔드 API | `POST /inventory/batches`, `GET /ingredients/{id}/batches` |
| 범위 밖 | Excel 일괄 업로드, 유통기한 임박 알림, 배치 삭제, FIFO 사용 처리 |

---

## 1. 백엔드 활용 API

### `GET /ingredients/{id}/batches` (인증 필요)
재료별 잔여 배치 목록 (FIFO 정렬, quantity > 0).
응답:
```json
[
  { "batchId": 1, "quantity": 5.0, "expiresAt": "2026-07-01" }
]
```

### `POST /inventory/batches` (인증 필요)
신규 배치 입고. Request:
```json
{
  "ingredientId": 1,
  "quantity": 10.0,
  "costPerUnit": 5500,
  "inboundDate": "2026-06-04",
  "expirationDate": "2026-07-01"
}
```

### 재료 목록 출처
별도 `GET /ingredients` 없음 → **`/prices/lowest-top?limit=50`** 응답의 `LowestTopItem[]`을 재료 목록으로 활용 (이미 `useLowestTop` 훅 있음).

---

## 2. 파일 변경

### 신규
- `src/app/types/inventory.ts` — `InventoryBatch`, `CreateBatchPayload`
- `src/app/api/inventory.ts` — `listBatches(ingredientId)`, `createBatch(payload)`
- `src/app/hooks/useInventoryBatches.ts` — useQuery + useMutation
- `src/app/pages/InventoryPage.tsx` — 메인 UI

### 수정
- `src/app/routes.tsx` — `/inventory` 라우트 추가
- `src/app/components/common/AppShell.tsx` — Sidebar에 "재고 관리" 항목 추가
- `src/app/pages/MainPage.tsx` — 메뉴 카드 추가 ("재고 관리")

---

## 3. UI 설계

### `/inventory` 메인 레이아웃
```
┌─────────────────────────────────────────────┐
│ 재고 관리                                   │
│ 재료별 현재 재고 + 입고 등록                │
├─────────────────────────────────────────────┤
│                            [+ 재고 입고]    │  ← 상단 우측 버튼
├─────────────────────────────────────────────┤
│ ┌─────────────┐  ┌─────────────┐            │
│ │ 🥬 배추     │  │ 🧄 마늘     │   카드 그리드│
│ │ 12.5 kg     │  │ 2.3 kg      │   md:2열   │
│ │ ─────────   │  │ ─────────   │   lg:3열   │
│ │ 배치 3건    │  │ 배치 1건    │             │
│ │ 임박: 6/15  │  │ 임박: 7/01  │             │
│ └─────────────┘  └─────────────┘            │
└─────────────────────────────────────────────┘
```

- 각 카드는 재료 1개. 클릭 시 모달 → 배치별 상세
- 카드 표시: 재료명, 총 수량(배치 합), 배치 개수, 가장 가까운 유통기한
- 총 수량 = `batches.reduce((sum, b) => sum + b.quantity, 0)`

### 입고 모달 (상단 "재고 입고" 버튼)
```
┌─────────────────────┐
│  📦 재고 입고       │
│                     │
│ 재료    [▼ 배추 ]  │  ← LowestTopItem 목록 드롭다운
│ 수량    [    10 ] kg│
│ 단가    [   5500] 원│  (선택)
│ 입고일  [2026-06-04]│
│ 유통기한[2026-07-01]│
│                     │
│ [취소]  [입고 등록] │
└─────────────────────┘
```

성공 시:
1. 해당 재료의 `["batches", id]` 쿼리 invalidate
2. 토스트 또는 모달 자동 닫힘

### 카드 상세 모달 (재료 카드 클릭)
```
┌──────────────────────────┐
│ 🥬 배추 — 배치별         │
│                          │
│ #1  5.0 kg  유통 6/15    │  ← FIFO 순
│ #2  4.0 kg  유통 6/20    │
│ #3  3.5 kg  유통 7/01    │
│                          │
│ 합계 12.5 kg / 3건       │
│                          │
│ [닫기]                   │
└──────────────────────────┘
```

---

## 4. 타입 정의

```ts
// types/inventory.ts
export interface InventoryBatch {
  batchId: number;
  quantity: number;
  expiresAt: string;        // "YYYY-MM-DD"
}

export interface CreateBatchPayload {
  ingredientId: number;
  quantity: number;
  costPerUnit?: number;
  inboundDate?: string;     // null 시 백엔드가 오늘로 채움
  expirationDate: string;   // 필수
}
```

---

## 5. 작업 체크리스트

- [ ] `types/inventory.ts` 신규
- [ ] `api/inventory.ts` 신규 (listBatches, createBatch)
- [ ] `hooks/useInventoryBatches.ts` 신규 (`useBatches(id)`, `useCreateBatch()`)
- [ ] `pages/InventoryPage.tsx` 신규
  - 상단 헤더 + "재고 입고" 버튼
  - 재료 카드 그리드 (`useLowestTop` 으로 재료 목록 → 각 카드가 자체 `useBatches`)
  - 입고 모달 (재료 선택 / 수량 / 단가 / 유통기한)
  - 카드 클릭 시 배치 상세 모달
- [ ] `routes.tsx` `/inventory` 추가
- [ ] `AppShell.tsx` Sidebar 항목 추가 (`<TagIcon>` 또는 새 아이콘)
- [ ] `MainPage.tsx` 메뉴 카드 1개 추가

---

## 6. 시간 추정

| Phase | 작업 | 시간 |
|---|---|---|
| A | 타입 + API + 훅 | 20분 |
| B | InventoryPage UI + 모달 | 40분 |
| C | 라우트 + 사이드바 + 메인 카드 | 10분 |
| D | 검증 (브라우저 + 백엔드 호출) | 10분 |
| **합계** |  | **약 1시간 20분** |

---

## 7. 검증 시나리오

1. 데모 로그인 → 사이드바 "재고 관리" 클릭 → `/inventory`
2. 재료 카드 그리드 표시 (배추/양파/.../닭고기 16개)
3. **첫 진입 시 모든 카드 "총 0 kg" 표시** (현재 inventory_batch 빈 상태)
4. "재고 입고" 버튼 → 모달 → 재료=배추, 수량=10, 단가=3000, 유통기한=2026-07-01 → "입고 등록"
5. 모달 닫힘 + 배추 카드의 "총 수량" 10kg로 갱신
6. 배추 카드 클릭 → 배치 상세 모달 → batchId 1, 10kg, 유통 7/01 표시
7. 한 번 더 입고 → 같은 재료에 배치 2개 → 합계 갱신

---

## 8. 시연 효과 (보너스)

이 페이지 만들면 **"우리는 단순 가격 비교가 아니라 재고 입출고 관리까지 일괄"** 메시지를 IR에서 강화 가능. 백엔드 트랜잭션 버그(`/ingredients/low-stock` 403)는 이 페이지와 무관하므로 영향 없음.

또한 입고를 1~2개 등록하면 `inventory_batch` 테이블이 채워지므로, **`/ingredients/low-stock` 403 이슈도 우회 해결**될 가능성 있음 (DepletionCalculator의 NPE 회피).

---

## 9. 리뷰 메모 공간

_수정 사항 있으면 적어주세요. 없으면 "구현 시작해"_

-
-
-
