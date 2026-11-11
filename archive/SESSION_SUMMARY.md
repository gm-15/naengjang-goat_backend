# 냉장GOAT 작업 세션 정리

## 구현 완료 목록

### Plan A — KAMIS 품목 코드 매칭
**문제**: 재료 이름 exact match 방식이라 "배추" vs "봄배추" 불일치 발생
**해결**:
- `ingredient.kamis_item_code` 컬럼 추가 (Flyway V007)
- `KamisPriceProcessor`: item_code 우선 조회 → 없으면 이름 fallback

---

### Plan B — 가격 단위 정규화
**문제**: KAMIS unit 필드가 "20kg", "500g", "" 혼재 → 단위 다른 가격 비교 불가
**해결**:
- `KamisPriceCalculator.toPricePerKg()` 신규 구현
- 정규식으로 unit 필드 파싱 → 전부 원/kg 단위로 통일

---

### Plan C — Excel 발주서 자동 생성
**구현**:
- `PurchaseOrderExcelService` (Apache POI)
- `GET /purchase-orders/export?from=&to=` → .xlsx 파일 다운로드
- 컬럼: 발주일 / 재료명 / 거래처 / 수량 / 단위 / 단가 / 합계 / 상태 / 메모
- 합계 행 자동 삽입, 숫자 천 단위 서식

---

### Plan D — FCM 푸시 알림 인프라
**구현**:
- `FirebaseConfig`, `FcmService` 신규 생성
- `users.fcm_token` 컬럼 추가 (Flyway V008)
- `PATCH /api/users/fcm-token` — 기기 토큰 등록/갱신 API
- `BuySignalNotifyTasklet` — 배치 완료 후 사용자별 buy-signal 재료 집계 → FCM 발송
- Spring Batch 2단계: `kamisPriceStep` → `buySignalNotifyStep`
- Firebase 서비스 계정 JSON 설정 → 빌드 성공 확인

---

### README 전면 재작성
- 디자인 규칙 v3.0 적용 (면접관용 문구 제거, 섹션 구조 재편)
- 배경 섹션 교체: KFDA 62조 시장 규모 + 경쟁사 분석 추가
- 데이터 플로우 다이어그램 이미지 삽입 (`docs/data-flow.png`)
- 2-테이블 분리 아키텍처 정정 (기존 단일 price_records 오류 수정)
- Flyway 버전 V001~V008 반영, UC-SUP-8 구현 완료 반영

---

### README.en.md 영문 번역본
- 한글 README 기준으로 전체 번역 신규 작성

---

### Swagger UI 추가
- `springdoc-openapi-starter-webmvc-ui:2.3.0` 의존성 추가
- 별도 설정 없이 컨트롤러 자동 스캔
- 접속: `http://localhost:8080/swagger-ui/index.html`

---

### CORS 설정
- `CorsConfig.java` 신규 생성
- 허용 Origin: localhost:3000 / 5173 / 5174
- `SecurityConfig`에 CORS + Swagger URL permitAll 적용

---

### API 가이드 문서
- `docs/API_GUIDE.md` 작성
- 전체 활성 엔드포인트 13개 요청/응답 예시 포함
- 프론트 연동용

---

## 회의 캡처 포인트

### 1. Swagger UI
```
http://localhost:8080/swagger-ui/index.html
```
- 전체 API 목록이 자동 생성된 화면
- 우측 "Try it out" → 실제 API 호출 가능

---

### 2. buy-signal 실 검증 (Postman 또는 Swagger)
```
1. POST /api/users/login  →  accessToken 복사
2. GET  /prices/1/trend?days=30  (Bearer 토큰 첨부)
```
- 응답에서 `"currentBuySignal": true` 확인
- `"signalReason"`: 도매가 15,725 < 월평균 20,698의 83% (24.0% 하락)
- 배추 기준 8일 연속 buy-signal

---

### 3. BuySignalNotifyTasklet 배치 실행 로그
앱 기동 시 콘솔에 출력됨:
```
Executing step: [buySignalNotifyStep]
[BUY-SIGNAL-NOTIFY] buySignal 재료 보유 사용자 수: 1
Step: [buySignalNotifyStep] executed in 239ms
```
- FCM 토큰 등록 후 실행하면 실제 알림 발송 로그까지 확인 가능

---

### 4. Flyway 자동 마이그레이션 로그
앱 기동 시 콘솔:
```
Successfully validated 8 migrations
Current version of schema naengjang_goat_db: 008
Schema is up to date. No migration necessary.
```

---

### 5. Excel 발주서 다운로드 (Swagger에서 직접)
```
GET /purchase-orders/export?from=2026-05-01&to=2026-05-31
```
- Swagger에서 Execute → 파일 다운로드
- 열어보면 발주 내역 + 합계 행 자동 생성된 .xlsx

---

### 6. GitHub README 화면
```
https://github.com/gm-15/naengjang-goat_backend
```
- 데이터 플로우 다이어그램 이미지
- 4종 락 비교 표
- 6년 threshold 표
- 3축 vs 현재 코드 상태 정직 표기
