## 작업 목적

점주가 영업 종료 후 POS 엑셀을 업로드하면 메뉴별 레시피로 재고를 차감하고 일일 판매 결과를 확인할 수 있도록 연결했습니다. 외부 사이트에서 구매한 주문은 배송 대기로 기록하고, 물건을 받은 뒤 입고 등록할 때 재고를 늘리도록 분리했습니다. 판매 반영 직후와 영업 시작 3시간 전에 각각 발주 판단 알림을 처리합니다.

## 변경 사항

- POS 판매 마감: `POST /pos/uploads` — xlsx 검증·메뉴 매핑·단위 환산·재료 차감·당시 판매/소비 기록 저장
- 메뉴 연결: `GET /pos/menu-mappings`, `PUT /pos/menu-mappings/{code}` — POS 메뉴코드와 점주 메뉴 연결
- 주문 기록·배송: `POST /purchase-orders` — 기록 시 재고 유지, `deliveryStatus=WAITING`; 목록에 배송 상태 필터 추가
- 수령 후 입고: `POST /purchase-orders/{id}/receive` — 전체 수령 시 재고 증가·RECEIVED 변경, 중복 입고 방지
- 일일 리포트: `GET /reports/daily` — 판매 수량·판매금액·메뉴별 판매·재료 소비량·현재 재고·반영/조회 시각 제공
- 발주 판단: `GET /closing/recommendations` — POS 소비 이력·사용 가능한 재고로 부족 예상/권장량 계산, 가격 신호·근거 부족 상태 제공
- 알림: 업로드 성공 후/영업 시작 3시간 전 별도 이벤트, DB 커밋 후 FCM 발송·중복 방지·실패 재시도·상태/상세 조회
- 기존 API 보완: 인증 실패 401·중복 가입 409, 가격 응답 `isLowest`/`isDiscount`, 최저가 응답 `unit`, 단위 그룹 검증
- 데이터 반영 보호: 점주별 DB 잠금과 전체 트랜잭션, 같은 파일 재요청은 기존 결과 반환, 같은 영업일의 다른 파일은 409
- DB: V009 신규 workflow 스키마, 빈 DB용 beforeMigrate bootstrap callback 추가; V001~V008 checksum 유지
- 시연 준비: demo 프로필 전용 합성 메뉴/레시피/과거 판매/가격/초기 재고, 오늘 날짜 POS 샘플, 영업 전 수동 트리거, HTTP 검증 스크립트
- 전달 문서: API 기준·팀원별 남은 작업·발표 시나리오·실행 방법·완료 범위 정리

## 검증 결과

- Java 21 / MySQL 8 / Redis 7에서 `gradlew clean build` 성공
- 전체 테스트 25개 통과: 새 workflow 14개, 기존 동시성 4개, Redis 장애 2개, 주문 원자성/동시성 2개, 단위 변환 2개, 앱 기동 1개
- 빈 DB: bootstrap callback + V001~V009 적용 및 앱 기동 확인
- 모의 기존 V008 DB: 기존 checksum 유지, V009 업그레이드 확인
- 실제 HTTP: 로그인 → POS 샘플 → 업로드/중복 업로드 → 리포트/판단 → 주문 기록 → 입고 → 영업 전 이벤트/중복 트리거 확인
- 시연 숫자: 배추국 8개, 매출 64,000원, 배추 소비 4,000g, 재고 6,000→2,000→주문 기록 후 2,000→입고 후 4,000g
- 발주 판단: 30일 일평균 1,100g, 3일 뒤 발주 기준 권장 1,300g, 합성 가격 데이터의 구매 유리 신호 확인
- 서로 다른 새 전용 DB에서 시연 데이터를 다시 생성해 같은 흐름/숫자 재현

## 연동 후 확인할 사항

- Firebase 서비스 계정 미설정으로 실제 HTTP 검증의 발송 상태는 `NOT_CONFIGURED`. 발송 성공·재시도 경로는 테스트 대체 객체로 검증했으며 실기기 수신·알림 이동은 미검증
- 실제 KAMIS/EKAPE/온라인 가격의 의미·단위·관측일·최신성 검증 및 실제 레시피/상품 데이터 적용 필요
- 팀원 프론트·모바일에서 파일 선택, 외부 사이트 복귀, 배송 대기/입고, 리포트, 푸시 연결 후 전체 E2E 확인 필요
- 시연 재시작은 새 전용 DB에 재생성하는 방식. 기존 DB를 비우는 초기화 버튼/자동 reset은 미구현
- 실제 로컬 DB는 migration 이력·수동 컬럼과 대조 후 적용. 자동 repair/기존 자료 삭제는 수행하지 않음
- 취소·환불, 주문 대행, 부분 배송, AI, 일반 알림함, 실제 POS별 자동 변환은 이번 범위 제외

## 전달 자료

- [API 계약](https://github.com/gm-15/naengjang-goat_backend/blob/feat/pos-closing-handoff/docs/handoff/API_CONTRACT.md)
- [팀원별 작업](https://github.com/gm-15/naengjang-goat_backend/blob/feat/pos-closing-handoff/docs/handoff/TEAM_TASKS.md)
- [시연 및 실행](https://github.com/gm-15/naengjang-goat_backend/blob/feat/pos-closing-handoff/docs/handoff/DEMO_RUNBOOK.md)
- [범위별 완료 상태](https://github.com/gm-15/naengjang-goat_backend/blob/feat/pos-closing-handoff/docs/handoff/SCOPE_STATUS.md)
