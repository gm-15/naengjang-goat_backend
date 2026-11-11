# 프로젝트 마무리 — 브랜치/로컬에만 있는 완료 작업을 main 에 수렴

작성: park, 2026-11-11. 목적은 신규 기능 추가가 아니라 **이미 끝난 작업을 main 에 반영해 저장소를 최종 상태로 만드는 것**이다.

## 현황

`origin/main` 은 `58911b6`(2026-10-30)에 멈춰 있다. 이후 작업이 반영되지 않았다.

## A. main 에 넣을 것 (검증 완료)

| # | 내용 | 현재 위치 | 검증 |
|---|---|---|---|
| A1 | Flyway 재활성화 + EKAPE 인증키 평문 기본값 제거 (`0a4e052`) | `origin/fix/flyway-and-ekape-key` | 빈 DB 에서 V001~V011 전부 적용(1.3초) 확인 |
| A2 | 발주 판정 회귀 테스트 11건 (`921bd5d`) | 로컬 worktree 만 | main 소스 기준 컴파일·11건 통과 확인 |
| A3 | FirebaseConfig 중복 초기화 가드 | 메인 체크아웃 미커밋 | DevTools 재시작·테스트 컨텍스트 대응 |
| A4 | CSV 시세 시드 스크립트·문서·KAMIS 검증 기록 3파일 | worktree 미커밋 | 시연 데이터 재현 절차 |

A4 대상 파일
- `scripts/seed-market-price-csv.py`
- `docs/integration/CSV_PRICE_SEED.md`
- `docs/integration/evidence/kamis-local-verification-2026-10-19.json`

## B. 판단 필요

**판매 취소 + 재고 복구 + Supplier(거래처) 도메인.**
main 에는 `OrderStatus.CANCELED` enum 과 "재고 복구 필요" 주석만 있고 구현이 없다.
구현은 `origin/feat/sim-order-cancel-supplier`(2026-06-04, `9e43c9d`)에 있는데, 그 브랜치는 6월 기준이라
10월에 다시 작업한 KAMIS 수집·가격 정합성과 충돌한다(미반영 커밋 15개 중 다수가 대체된 작업).

→ **통째 머지 금지.** 포함한다면 `9e43c9d` 단독 체리픽 후 현재 스키마 기준으로 컴파일·테스트를 다시 봐야 한다.

## C. 닫을 브랜치 (이미 반영 또는 대체됨)

| 브랜치 | 사유 |
|---|---|
| `feat/ekape-official-api` (+2) | EKAPE OpenAPI 전환·gzip 디코딩 수정이 `58911b6` 에 동등 반영됨 |
| `feat/prices-view-dto` (+7) | 2026-06 작업. 10월 가격 파이프라인 재작업으로 대체 |
| `feat/sim-kamis-bundle` (+5) | 동일 |
| `integrate/sim-kamis-pipeline` (+23) | 고유 가치였던 11개 테스트는 A2 로 체리픽 완료 |
| `feat/integrated-owner-workflow`, `feat/pos-closing-handoff`, `feat/kamis-price-integrity`, `feat/kamis-price-pipeline` | 이미 main 에 머지됨(+0) |

## D. 범위 밖 (별도 과제)

- `application.properties` 평문 비밀값(`jwt.secret`, `kamis.api.key`, `naver.shopping.client-secret`, `spring.datasource.password`)
  과 `KamisVolatilityAnalysisRunner` 하드코딩 KAMIS 키 → 환경변수화 + 키 재발급. 팀 협조 필요
- 미구현 기능(카카오톡 알림, 앱 내 결제, 실사 보정, 만료 재고 폐기, POS 양식 매핑) → 고도화 영역

## 실행 순서

1. worktree 에서 A4 3파일 커밋
2. worktree 에 A3 적용 후 커밋
3. `fix/flyway-and-ekape-key` 푸시 (A1·A2·A3·A4 포함)
4. main 으로 머지
5. 전체 테스트 1회 (검증용 DB, Redis 필요)
6. C 브랜치 삭제
7. 메인 체크아웃 `git pull` 로 동기화

## 제외

`.claude/launch.json`(로컬 도구 설정), `plan_park_0917_01.md`(미해결 버그 계획서)는 커밋하지 않는다.
