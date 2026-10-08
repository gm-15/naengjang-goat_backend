# 연동 API 계약

시간은 `Asia/Seoul`. 인증은 `Authorization: Bearer <accessToken>`. 날짜 `YYYY-MM-DD`, 시간 `HH:mm:ss`. 기존 API 목록은 [기존 가이드](../API_GUIDE.md); 판매 마감·배송/입고는 이 문서 우선.

## 1. 메뉴 연결 및 POS 업로드

| 호출 | 용도 |
|---|---|
| `GET /menus` | 점주의 메뉴 ID·이름·가격·BOM 조회 |
| `POST /menus` | 실제 메뉴와 1개 판매당 레시피 등록 |
| `PUT /menus/{id}` | 기존 메뉴명·가격·실제 레시피 수정 |
| `GET /pos/menu-mappings` | POS 코드↔메뉴 연결 조회 |
| `PUT /pos/menu-mappings/DEMO001` | `{"menuId":123}` 코드 연결/변경 |
| `GET /pos/template` | 인증된 점주의 빈 xlsx 판매 양식 다운로드 |
| `POST /pos/uploads` | `multipart/form-data`, 필드명 `file`에 xlsx |

메뉴 등록/수정 요청:

```json
{"name":"배추국","price":8000,"recipe":[{"ingredientId":456,"requiredQuantity":0.5,"unit":"kg"}]}
```

- 메뉴명은 앞뒤 공백을 제거하며 1~100자, 같은 매장 안에서 중복 불가(409). 가격은 0 이상의 정수. 레시피는 1~100개 재료, 재료 ID 중복 불가.
- 소모량은 메뉴 **한 개** 판매당 양수이며 소수 최대 3자리. 본인 매장 재료만 사용(타인 재료 403). g/kg, ml/L, 개 호환 단위만 허용하고 재료 기준 단위로 환산해 저장. 환산 후 수량 범위는 0.001~9,999,999.999.
- GET/등록/수정 응답은 기존 `menuId`, `name`, `price`에 `recipe:[{ingredientId,ingredientName,baseUnit,requiredQuantity,unit}]` 추가. 예시 kg 입력은 재료 기준 g로 `requiredQuantity:500`, `unit:"g"` 반환. 아직 레시피가 없는 기존 메뉴도 GET 목록에 표시하며 `recipe:[]`.
- 카테고리 가입으로 복사된 소모량 1은 임시값이므로 점주가 실제 1인분 레시피로 수정한 뒤 POS 파일을 반영. 수정은 다음 POS 반영에 적용하며 이미 반영한 판매·소비 리포트 스냅샷은 변경하지 않음. 메뉴 삭제는 미지원.
- 카테고리 가입을 다시 요청하면 이미 있는 메뉴명은 건너뛰고 신규 메뉴만 추가. 점주가 수정한 기존 메뉴 가격·레시피를 임시값으로 덮어쓰지 않음.
- 타인 메뉴 수정/없는 메뉴는 404. 메뉴 수정과 POS 반영은 같은 매장 잠금으로 직렬 처리. `GET /ingredients/{id}/batches`도 본인 재료만 조회 가능하며 타인/없는 재료는 404.

파일 규칙:

- 양식 다운로드는 `판매내역`의 헤더만 포함하고 실제 판매 행은 만들지 않음. `작성안내`와 본인의 메뉴명·연결 코드·판매가를 담은 `등록메뉴` 시트를 함께 제공. 빈 양식을 그대로 업로드하면 400이며, 실제 하루 판매 자료를 작성한 뒤 반영.

- 최대 5,000,000바이트, `.xlsx`, `판매내역` 시트, 헤더 순서 `영업일 / 메뉴코드 / 메뉴명 / 판매수량 / 판매금액`.
- 영업일 하나, 최대 1,000행, 한 메뉴는 하루 합산 한 행. 미래 영업일 불가.
- 판매수량은 양의 정수(최대 100,000). 판매금액은 그 행 전체 매출(0 이상, 최대 소수 2자리). 수량과 다시 곱하지 않음. 취소·환불 행 미지원.
- 메뉴코드가 있으면 사전 연결 필수. 코드가 비어 있으면 점주의 고유한 메뉴명으로 연결. 두 경우 모두 파일 메뉴명이 등록 이름과 일치하고 레시피가 있어야 함.
- 수식/오류 셀은 거절. 원본 POS 형식이 다르면 이 양식으로 변환. 범용 POS 호환이라고 설명하지 않음.
- 보유 배치 중 영업일에 입고돼 있고 유통기한이 지나지 않은 배치를 유통기한 빠른 순으로 차감. 부족하면 전체 미반영 409.
- 동일 파일(점주+SHA256) 재업로드: 200, `duplicate:true`, 추가 차감·알림 없음.
- 이미 반영한 영업일의 다른 파일: 409, 덮어쓰기·누적 없음. 정정/취소 API는 이번 범위 제외.
- 날짜가 지난 파일도 해당 날짜의 재고 조건으로 반영하므로 순서대로 업로드. 뒤늦은 파일은 당시 재고를 완전히 복원하는 기능이 아님.

예시 응답(ID·시각은 실제 값):

```json
{
  "uploadId": 1,
  "businessDate": "2026-10-07",
  "duplicate": false,
  "salesQuantity": 8,
  "salesAmount": 64000,
  "menuSales": [{"menuId": 123, "menuName": "배추국", "posCode": "DEMO001", "quantity": 8, "salesAmount": 64000}],
  "ingredientConsumption": [{"ingredientId": 456, "ingredientName": "시연용 배추", "baseUnit": "g", "quantity": 4000}],
  "reflectedAt": "2026-10-07T22:00:00"
}
```

프론트: 파일 선택→즉시 업로드→서버 성공 후 재고/리포트/판단 조회 갱신. 전송 중 버튼 잠금. `{status,message}` 오류를 점주에게 표시. `duplicate:true`는 이미 반영됐다는 안내. 별도 수동 판매 입력·최종 확정 화면은 추가하지 않음.

## 2. 발주 기록 → 배송 대기 → 입고

`POST /purchase-orders`는 실제 외부 주문을 기록하며 재고를 늘리지 않음.

```json
{
  "ingredientId": 456,
  "orderedAt": "2026-10-07",
  "productName": "배추 2kg",
  "sourceUrl": "https://example.com/product",
  "quantity": 2,
  "baseUnit": "kg",
  "unitPrice": 5000,
  "supplier": "네이버",
  "memo": "외부 결제 완료"
}
```

- `quantity`는 구매 단위 수량, `unitPrice`는 해당 구매 단위 한 단위당 가격. 예시: 2kg × 5,000원/kg = 10,000원, 재고 예상 2,000g.
- 상품이 2kg 한 포장 10,000원이면 `quantity:1`, `baseUnit:"포장"`, `unitPrice:10000`, `packageSize:2`, `packageUnit:"kg"`. 포장 개수는 정수. `packageSize`는 포장 한 개의 크기.
- g/kg, ml/L, 개만 호환 환산. g↔ml·개 환산 불가. 판매 사이트 가격의 포장 기준을 임의로 g 단가라고 보내지 말 것.
- 상품명은 클릭 상품명을 프론트에서 미리 채우고 수정 가능. 서버에서 필수 재료 ID의 점주 소유권 확인.
- 구매 수량·가격·상품 링크 검증. 미래 주문일 등록 불가.

추가 응답 필드:

```json
{
  "id": 1,
  "status": "CONFIRMED",
  "deliveryStatus": "WAITING",
  "expectedQuantityBase": 2000,
  "inventoryUnit": "g",
  "totalAmount": 10000,
  "receivedAt": null,
  "receivedBatchId": null
}
```

`status`는 기존 구매 기록 상태, `deliveryStatus`는 배송/입고 상태. 기존 데이터는 `LEGACY`: 과거 입고 여부를 추측하지 않으며 이 API로 재입고 불가.

- `GET /purchase-orders?deliveryStatus=WAITING` 배송 대기 목록.
- `GET /purchase-orders?deliveryStatus=RECEIVED` 입고 완료 목록.
- 기존 기간·재료·상태·page/size 필터 유지. 기본 기간 최근 30일, 최대 365일. 배송 대기가 오래된 경우 `from` 범위도 설정. size 최대 100.

받은 뒤 `POST /purchase-orders/{id}/receive`:

```json
{"receivedQuantity":2000,"inboundDate":"2026-10-08","expirationDate":"2026-10-18"}
```

- 수량은 응답의 `inventoryUnit` 기준이며 `expectedQuantityBase`와 같아야 함. 이번 버전은 전체 수령만 지원.
- `inboundDate` 생략 시 오늘. 주문일 이후~오늘, 유통기한은 오늘 이상.
- 성공 시 배치 생성+`RECEIVED` 변경을 한 트랜잭션으로 반영. 중복 호출 409. 다른 점주의 주문 404.
- 프론트는 성공 후 목록·재고를 갱신. 실패하면 배송 대기를 유지하고 오류 안내.
- 기존 상품 상세 화면의 구매 성공 후 `POST /inventory/batches` 자동 호출은 제거. 주문과 연결되지 않은 별도 입고 API는 기존 수기 입고용으로만 사용.

## 3. 운영 리포트

`GET /reports/daily?businessDate=2026-10-07`

- `uploaded`: 그 영업일 판매 반영 여부. 미업로드는 false 및 판매/소비 0·빈 배열. 화면에 **미반영** 표시.
- `salesQuantity`: 메뉴 판매 수량 합계. 영수증/주문 건수가 아님.
- `salesAmount`: 업로드된 메뉴별 판매금액 합계. 순이익/원가/매입액과 다름.
- `menuSales`, `ingredientConsumption`: 반영 당시 이름·수량·금액·재료 소비 스냅샷. 이후 레시피/가격 수정과 무관.
- `currentInventory`: 조회 시점 재고. 과거 마감 재고라는 제목 사용 금지.
- `inventoryAsOf`: 재고 조회 시각. `reflectedAt`: 선택 영업일 판매 반영 시각. `lastUploadedBusinessDate`: 점주 마지막 반영 영업일.

## 4. 재고·가격 판단

`GET /closing/recommendations?businessDate=2026-10-07`

- `generatedAt`, `lastUploadedBusinessDate`, `lastReflectedAt`, `settingsConfigured`, `items` 반환.
- items: `ingredientId`, `ingredientName`, `baseUnit`, `currentStock`, `dailyAvgSales`, `nextOrderDayDistance`, `recommendedQuantity`, `estimatedDepletionDate`, `stockAlert`, `buySignal`, `priceDataCoverage`, `priceReason`, `reason`. `recommendedQuantity`와 `estimatedDepletionDate`는 소비 이력이 부족하면 null이므로 0/안전으로 취급하지 않음.
- 일평균 = 최근 30일 POS 소비 합계 ÷ 30. 미업로드 날짜도 분모에 포함. 충분히 쌓인 판매 이력으로 시연하고 반영 날짜를 함께 표시.
- 제안 수량 = max(일평균 × 다음 발주 요일까지 일수 − 현재 재고, 0). 기준 요일과 같은 날이면 다음 주까지 7일. 설정 없으면 7일 기본값 + `settingsConfigured:false` 안내.
- `businessDate`는 발주 요일까지 일수를 계산하는 기준 날짜. 재고·판매 평균·가격은 요청 시점의 최신 상태이며 과거 시점 복원 기능이 아님. 소진 예정일은 요청 시점 오늘 기준.
- 최근 POS 자료가 없거나 소비 이력이 없으면 수량 null/근거 부족 표시. 최신 자료가 한 달 범위 안에 있어도 업로드 날짜를 감추지 않음.
- 판단의 `currentStock`은 지금 사용 가능한 재고(입고일≤오늘, 유통기한≥오늘). 리포트 `currentInventory`는 물리적 잔량 합계이므로 기한 지난 잔량이 있으면 서로 다를 수 있음. 자동 폐기/재고 삭제는 하지 않음.
- 가격 판단은 기존 시세 추이 서비스를 재사용. 실제 KAMIS/EKAPE 데이터의 도매/소매 의미·날짜·동일 단위·중복 및 최신성 검증은 데이터 담당 작업. 합성 시연 통과로 실제 수집 품질을 보장하지 않음.
- 새 POS 이력이 존재하는 점주는 기존 건별 `/orders` 소비를 평균에 중복 합산하지 않음. 같은 판매를 양 API로 이중 등록하지 말 것.

## 5. 두 시점 알림과 모바일 연결

- POS 신규 반영 성공 후: `type:UPLOAD`, 업로드 ID별 한 번. DB 커밋 후 발송.
- 영업 시작 3시간 전: `type:OPENING`, 점주+영업일별 한 번. 매분 검사, 짧은 서버 중단 시 영업 시작 전까지 보충 실행. 예: 02:00 영업이면 전날 23:00.
- 기존 종료 3시간 전 알림은 기본 비활성화(`alerts.legacy-enabled`를 켜지 말 것). 가격 수집 job 직후 별도 알림 발송 제거.
- 업로드 알림을 보냈어도 영업 전 알림은 별개로 보냄.

기존 `PATCH /api/users/fcm-token`, body `{"token":"FCM_DEVICE_TOKEN"}` 사용. Expo Push Token을 Firebase Admin에 보내지 말 것. 팀원이 실제 Firebase native 기기 토큰을 얻을 수 있도록 앱/빌드/권한 설정 필요.

- 로그인·앱 실행 시 현재 native 토큰 등록. 같은 토큰을 다른 계정에 등록하면 이전 계정의 연결을 해제하고 새 계정에 연결. 계정별 한 토큰만 지원하며 토큰 문자열은 대소문자를 구별.
- 로그아웃 시 인증 정보를 지우기 **전에** `{"token":null,"expectedToken":"이 기기에서 마지막으로 등록한 토큰"}`로 현재 기기 연결 해제. 현재 계정에 다른 기기의 토큰이 더 최근에 등록됐다면 변경 없이 204 반환. `expectedToken`을 생략한 null 해제는 현재 계정의 연결 전체를 해제하므로 구형 클라이언트 호환용으로만 사용. 성공 응답은 204이며 기기 토큰을 반환하지 않음. null 해제는 다른 계정의 연결을 변경하지 않음.
- `token` 필드는 필수이며 값은 null 또는 1~255자 공백 없는 native 토큰. 빈 문자열/누락/잘못된 형식은 400. 동시 등록 충돌은 409이며 짧은 간격으로 재시도할 수 있음. 이전 연결 해제와 새 연결 등록은 한 트랜잭션이므로 실패 시 둘 다 롤백.
- V010은 기존 중복 토큰 중 사용자 ID가 가장 큰 행만 유지하고 나머지는 null로 정리. 과거 등록 시각이 없어 가장 최근 로그인 계정을 복원하지는 못함. 업데이트 후 앱에서 다시 로그인하면 현재 계정으로 연결됨.

FCM data:

```json
{"notificationId":"77","type":"OPENING","businessDate":"2026-10-08","route":"closing"}
```

알림을 누르면 `GET /closing/notifications/77`로 소유한 알림의 title/body/상태 및 당시 판단 스냅샷을 받아 상세 화면 표시. 지금 재고를 확인하려면 판단 API를 다시 호출.

- `deliveryStatus`: `PENDING`, `SENT`, `NOT_CONFIGURED`, `WAITING_FOR_TOKEN`, `FAILED`, `EXPIRED`.
- `SENT`: Firebase 서버가 요청 수락. 사용자 휴대폰에 실제 표시됐다는 보장은 별도 실기기 검증.
- Firebase 미설정/토큰 미등록도 POS 데이터 반영은 성공. 성공으로 위장하지 않고 상태 유지.
- FAILED는 5분 간격 최대 3회 시도, 미설정/토큰 대기는 매분 재검사, 미발송 후 24시간이 지나면 만료. 서버는 최소 한 번 전달 방식을 사용하므로 앱은 `notificationId`로 수신 중복을 방어.
- 서버에는 발송 기록·상세 조회만 구현. 일반 알림함 목록/읽음 처리 UI는 이번 필수 작업 아님.

## 6. demo 전용

`SPRING_PROFILES_ACTIVE=demo`, `demo` 계정만 허용:

- `POST /demo/setup`: 합성 fixture 최초 생성. 기존 자료 덮어쓰기/초기화 없음.
- `GET /demo/pos-sample`: 오늘 날짜 xlsx.
- `POST /demo/opening-trigger?businessDate=2026-10-08`: 오늘/내일 영업 전 이벤트 수동 실행. 동일 날짜 중복 방지. 응답이 PENDING일 수 있으므로 상세 API로 실제 상태 조회.

## 오류 표시

검증 실패 400, 인증 없음/로그인 실패 401, 다른 점주 메뉴 연결/재료 사용 403, 소유하지 않은 주문/알림 404, 중복 날짜·재고 부족·중복 입고 409. 새 흐름 오류 본문 `{"status":409,"message":"..."}`. 네트워크 오류 재시도에도 서버의 중복 방지 기준을 유지.
