# 냉장GOAT 백엔드 API 가이드

> 프론트엔드 연동용. 서버 기동 후 Swagger에서 직접 테스트 가능.
>
> **Swagger UI**: http://localhost:8080/swagger-ui/index.html

---

## 기본 정보

| 항목 | 값 |
|---|---|
| Base URL | `http://localhost:8080` |
| Content-Type | `application/json` |
| 인증 방식 | JWT Bearer Token |
| 토큰 만료 | Access 1시간 / Refresh 7일 |

---

## 인증 흐름

```
1. POST /api/users/login  →  accessToken, refreshToken 수령
2. 이후 모든 요청 헤더에 포함:
   Authorization: Bearer <accessToken>
3. accessToken 만료 시 refreshToken으로 재발급 (미구현, 추후 협의)
```

**데모 계정**
```
username: demo
password: demo1234
```

---

## 공통 에러 응답

| HTTP 상태 | 의미 |
|---|---|
| 400 | 요청 형식 오류 (필드 누락, 타입 불일치) |
| 401 | 인증 실패 (토큰 없음 또는 만료) |
| 403 | 권한 없음 |
| 404 | 리소스 없음 |
| 500 | 서버 오류 |

---

## 1. 인증 (Auth)

### 회원가입
```
POST /api/users/signup
인증: 불필요
```
**Request Body**
```json
{
  "username": "string",
  "password": "string",
  "ownerName": "string"
}
```
**Response** `200 OK`
```json
"회원가입이 성공적으로 완료되었습니다."
```

---

### 로그인
```
POST /api/users/login
인증: 불필요
```
**Request Body**
```json
{
  "username": "string",
  "password": "string"
}
```
**Response** `200 OK`
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci..."
}
```

---

### 온보딩 (카테고리 선택)
```
POST /api/users/onboard
인증: 필요
```
> 회원가입 직후 매장 카테고리 선택. 해당 카테고리 레시피 템플릿을 메뉴로 자동 복사.

**Request Body**
```json
{
  "categories": ["KOREAN", "WESTERN"]
}
```
**Response** `200 OK`
```json
{
  "createdMenus": 41,
  "createdBom": 850,
  "newIngredients": ["배추", "양파"]
}
```
> `newIngredients`: 기존 재료와 매칭 못 해 새로 생성된 재료명. requiredQuantity=1(placeholder)이므로 직접 수정 필요.

---

### FCM 토큰 등록/갱신
```
PATCH /api/users/fcm-token
인증: 필요
```
> 앱 실행 시 호출. Firebase에서 받은 기기 토큰을 서버에 등록해야 buy-signal 푸시 알림 수신 가능.

**Request Body**
```json
{
  "token": "FCM_DEVICE_TOKEN"
}
```
**Response** `204 No Content`

---

## 2. 재료 (Ingredient)

### 재고 부족 Top N 조회
```
GET /ingredients/low-stock?limit=5
인증: 필요
```
**Query Params**

| 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| limit | int | 5 | 조회 개수 |

**Response** `200 OK`
```json
[
  {
    "ingredientId": 1,
    "ingredientName": "배추",
    "currentStock": 2.5,
    "baseUnit": "kg",
    "dailyAvgSales": 1.2,
    "nextOrderDayDistance": 3,
    "stockRatio": 0.25,
    "grade": "WARNING",
    "estimatedDepletionDate": "2026-06-05",
    "orderAlert": true
  }
]
```
> `grade`: `SAFE` / `WARNING` / `DANGER`

---

### 재료 KAMIS 카테고리 수정
```
PATCH /ingredients/{id}/category?category=VEGETABLES
인증: 필요
```
**Response** `204 No Content`

---

## 3. 가격 (Price)

### 최저가 재료 Top N 조회
```
GET /prices/lowest-top?limit=5
인증: 필요
```
**Response** `200 OK`
```json
[
  {
    "ingredientId": 1,
    "name": "배추",
    "weekAvg": 19710,
    "monthAvg": 24350,
    "todayPrice": 18500,
    "dropRatePct": 6.14,
    "trend": { ... },
    "externalLinks": [
      { "source": "NAVER", "url": "https://..." },
      { "source": "SIKJAJAEWANG", "url": "https://..." }
    ]
  }
]
```

---

### 재료 가격 상세 조회
```
GET /prices/{ingredientId}
인증: 필요
```
**Response** `200 OK` — KAMIS 도소매가 + 온라인 최저가 병합 응답

---

### 재료 가격 추이 + buy-signal 조회
```
GET /prices/{ingredientId}/trend?days=30
인증: 불필요
```
**Query Params**

| 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| days | int | 30 | 조회 기간 (일) |

**Response** `200 OK`
```json
{
  "ingredientId": 1,
  "ingredientName": "배추",
  "currentBuySignal": true,
  "signalReason": "도매가 15,725원/kg — 30일 평균(20,698)의 83% (24.0% 하락)",
  "dataCoverage": 30,
  "points": [
    { "date": "2026-05-01", "price": 20000 },
    { "date": "2026-05-02", "price": 19500 }
  ]
}
```
> `currentBuySignal: true` = "지금 사세요" 신호

---

## 4. 발주 이력 (Purchase Order)

### 발주 등록
```
POST /purchase-orders
인증: 필요
```
**Request Body**
```json
{
  "ingredientId": 1,
  "orderedAt": "2026-06-02",
  "quantity": 10.0,
  "baseUnit": "kg",
  "unitPrice": 18500,
  "supplier": "농협유통",
  "memo": "급배송 요청"
}
```
**Response** `200 OK`
```json
{
  "id": 1,
  "ingredientId": 1,
  "ingredientName": "배추",
  "orderedAt": "2026-06-02",
  "quantity": 10.0,
  "baseUnit": "kg",
  "unitPrice": 18500,
  "totalAmount": 185000,
  "supplier": "농협유통",
  "memo": "급배송 요청",
  "status": "PENDING",
  "createdAt": "2026-06-02T10:00:00"
}
```
> `status`: `PENDING` / `CONFIRMED` / `CANCELLED`

---

### 발주 목록 조회
```
GET /purchase-orders
인증: 필요
```
**Query Params**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| from | LocalDate | 예 | 시작일 (yyyy-MM-dd) |
| to | LocalDate | 예 | 종료일 (yyyy-MM-dd) |
| ingredientId | Long | 아니오 | 재료 필터 |
| status | String | 아니오 | PENDING / CONFIRMED / CANCELLED |
| page | int | 아니오 | 기본값 0 |
| size | int | 아니오 | 기본값 20 |

**Response** `200 OK` — Page\<PurchaseOrderResponse\>

---

### 발주 기간 집계
```
GET /purchase-orders/summary?from=2026-05-01&to=2026-05-31
인증: 필요
```
**Response** `200 OK` — 기간 내 총 금액 + 재료별 집계

---

### 발주서 Excel 다운로드
```
GET /purchase-orders/export?from=2026-05-01&to=2026-05-31
인증: 필요
```
**Response** `200 OK`
```
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename="purchase_orders_2026-05-01_2026-05-31.xlsx"
```
> Blob으로 받아 파일 저장 처리 필요

---

## 5. 주문/판매 (Order)

### 판매 등록 (POS)
```
POST /orders
인증: 필요
```
**Request Body**
```json
{
  "menuId": 1,
  "quantity": 2,
  "channelType": "DELIVERY"
}
```
> `channelType`: `DELIVERY` / `POS` / `KIOSK`

**Response** `200 OK`
```json
{
  "orderId": 1,
  "channelType": "DELIVERY",
  "orderStatus": "COMPLETED",
  "totalAmount": 34000,
  "createdAt": "2026-06-02T10:00:00",
  "deductedBatches": [ ... ]
}
```
> 판매 시 재고 자동 차감 (FIFO, 분산 락 적용)

---

### 주문 목록 조회
```
GET /orders
인증: 필요
```
**Response** `200 OK` — List\<OrderResponse\>

---

## 6. 메뉴 (Menu)

### 메뉴 목록 조회
```
GET /menus
인증: 필요
```
**Response** `200 OK` — List\<MenuResponse\>

---

## 참고

- **비활성화 엔드포인트**: `/inventory/**`, `/recipes/**`, `/analysis/**` — 현재 미사용
- **Swagger 전체 명세**: http://localhost:8080/swagger-ui/index.html
- **CORS 허용 Origin**: localhost:3000 / 5173 / 5174
- **운영 도메인 추가 필요 시**: 백엔드 `CorsConfig.java` 수정 요청
