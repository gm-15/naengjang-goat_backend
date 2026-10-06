# 실행 및 최종 발표 시연

## 환경

- Java 21 JDK, MySQL 8, Redis 7. Windows는 `gradlew.bat`, Linux/macOS는 `bash gradlew`.
- 기존 DB에 바로 적용하기 전에 백업. 새 전용 DB로 먼저 검증 권장.
- 일반 실행에서는 데모 계정·fixture 자동 생성 없음. 데모는 `SPRING_PROFILES_ACTIVE=demo`를 명시.

새 DB 생성 예(SQL, 사용 중인 기존 DB에 DROP 금지):

```sql
CREATE DATABASE goat_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE naengjang_goat_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

환경변수:

| 변수 | 설정 |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/goat_demo?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8&allowPublicKeyRetrieval=true` |
| `DB_USERNAME`, `DB_PASSWORD` | 로컬 DB 계정/암호 |
| `JWT_SECRET` | 배포용 충분히 긴 별도 비밀키 |
| `SPRING_PROFILES_ACTIVE` | 시연 시 `demo` |
| `FIREBASE_CONFIG_PATH` | 실푸시 사용 시 `file:/절대경로/firebase-service-account.json` |
| `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD` | 별도 테스트 DB 접속 정보 |

Firebase 서비스 계정은 Git에 넣지 않음. 없으면 앱은 기동하지만 알림 상태 `NOT_CONFIGURED`. Android/iOS 앱 Firebase 프로젝트 및 native FCM 토큰은 팀원과 맞춰 실기기로 검증.

```bash
bash gradlew build
bash gradlew bootRun
```

테스트는 전용 DB 사용. 기존 스트레스 테스트가 테이블을 비우므로 개발/운영 DB로 `TEST_DB_URL`을 설정하지 말 것. Redis 장애 테스트는 `redis-test` Docker 컨테이너를 stop/start하므로 테스트 전용 Redis 사용.

## DB 마이그레이션

- 새 DB: beforeMigrate bootstrap callback → 기존 V001~V008 → V009 workflow 순서, 이후 기존 JPA `ddl-auto=update`가 나머지 테이블 생성.
- 기존 V008 DB: callback은 이미 존재하는 테이블을 변경하지 않고 V009만 적용. 낮은 버전의 신규 migration을 추가하면 Flyway 검증 오류가 발생하므로 bootstrap은 versioned migration 대신 beforeMigrate callback으로 구현. 모의 V008 DB에서 업그레이드 성공 확인.
- 기존 migration 파일의 checksum 변경 없음. 새 DB 성공은 모든 기존 환경 업그레이드 성공을 뜻하지 않음.
- 기존 테이블은 있는데 Flyway 이력이 없으면 DB 백업·스키마 대조 후 V009 이전 버전으로 명시적 baseline을 검토. 임의 `baseline-on-migrate`/`repair`로 실패 기록을 덮지 않음.
- V009 적용 전에 이미 같은 신규 컬럼/테이블이 수동 생성된 DB라면 충돌 확인 필요. 통합 시 실제 로컬 DB 이력과 대조.
- 과거 발주 `deliveryStatus=LEGACY`. 과거 기록에 입고를 추측해 재고를 추가하지 않음.

## 재현 가능한 서버 시연

새 전용 DB로 demo 프로필 서버 실행 후:

```bash
python3 scripts/demo-smoke.py --base-url http://localhost:8080 --output-dir /tmp/goat-demo
```

Windows 예:

```powershell
python scripts/demo-smoke.py --base-url http://localhost:8080 --output-dir .\demo-results
```

- demo/demo1234 로그인, 합성 자료 최초 생성, 오늘 날짜 POS 샘플 저장, 실제 HTTP API 흐름과 숫자 검증.
- 스크립트는 새 시연 DB에서 1회 사용. 이미 초기화된 DB에서는 중단하며 기존 자료를 삭제하지 않음. 다시 처음부터 시연하려면 새 DB 이름으로 서버 실행.
- 샘플 다운로드·setup·영업 전 수동 trigger는 demo 프로필 및 demo 계정에서만 접근.
- 앱 시연에서는 `/demo/setup`까지 준비하고 샘플만 받아놓은 뒤 POS 업로드 이후 단계를 화면에서 진행. smoke 전체를 먼저 실행하면 그날 데이터가 이미 반영된 상태임.
- 스크립트 결과 `results.json`에 token/암호 저장 없음.

합성 fixture:

| 항목 | 값 |
|---|---|
| 메뉴 | 배추국 8,000원, `DEMO001` |
| 레시피 | 1개 판매당 시연용 배추 500g |
| 시작 재고 | 6,000g (과거 소비 후 기초 잔량) |
| 과거 판매 | 전날까지 29일 × 2개 = 매일 1,000g 소비 기록 |
| 오늘 POS | 8개, 매출 64,000원, 소비 4,000g |
| 업로드 후 | 재고 2,000g, 30일 소비 33,000g ÷ 30 = 일평균 1,100g |
| 다음 발주일 | 오늘로부터 3일 뒤, 제안 max(1,100×3−2,000,0)=1,300g |
| 시세 fixture | `source=DEMO`, 29일 10,000원/kg + 오늘 8,000원/kg, 가격 유리 신호 |
| 새 주문 기록 | 2kg × 5,000원/kg, 재고 여전히 2,000g |
| 전체 입고 | 2,000g 증가, 재고 4,000g |

과거 판매 fixture는 실제 엑셀 반영 이력이 아니라 테스트용 스냅샷이며, 초기 배치는 이미 그 과거 소비가 반영된 기초 잔량이다. 가격도 실제 KAMIS/EKAPE 원본으로 소개하지 않는다.

## 3분: 필요성·기능 소개

| 시간 | 내용 |
|---|---|
| 0:00~1:00 | 인터뷰에서 확인한 눈으로 재고 확인·발주 시간·품절 문제. 현재 POS 사용을 유지하면서 업무 부담을 줄인다는 목표. |
| 1:00~2:20 | 점주 하루 흐름: 영업 전 판단 확인/구매/입고 → 운영 중 기존 POS → 마감 판매 엑셀 반영/리포트 → 다음 영업 전 알림. 화면 기능 목록을 따로 나열하지 않음. |
| 2:20~3:00 | 이번 완성 범위와 구현 근거. 주문 대행/실시간 POS 연동/순이익/AI를 구현했다고 말하지 않음. 실제 푸시·실자료 검증 범위 명시 후 시연으로 이동. |

## 4분: 불편함 → 해결 과정을 잇는 시나리오

| 시간 | 점주의 상황과 행동 | 보여줄 결과 |
|---|---|---|
| 0:00~0:20 | “장사를 끝냈는데 내일 배추가 얼마나 남을지 모르겠습니다.” 초기 재고 6,000g 확인. | 마지막 판매 반영일 표시, 눈으로 확인하던 불편 설명. |
| 0:20~1:00 | 기존 POS에서 받은 오늘 판매 엑셀을 업로드. | 배추국 8개 → 배추 4,000g 차감 → 재고 2,000g. 별도 수동 판매 입력 없음. |
| 1:00~1:35 | 오늘 결과와 반영 직후 알림 확인. | 매출 64,000원, 소비 4,000g, 최신 재고/반영 시각. 알림 누르면 판단 근거. |
| 1:35~2:15 | “다음 발주일까지 부족할 수 있고 지금 가격도 유리하군요.” 외부 상품으로 이동·구매 후 앱 복귀. | 1,300g 발주 제안/가격 근거. 클릭 상품명으로 주문 기록, 배송 대기. 재고는 2,000g 유지. |
| 2:15~3:05 | “다음 날 물건을 받았습니다.” 배송 대기에서 입고 버튼. | 2kg 전체 수령 → 재고 4,000g, 입고 완료. 마감 매출/소비 기록은 그대로. |
| 3:05~3:40 | “영업 전에 한 번 더 확인합니다.” demo 영업 전 트리거 실행. | 업로드 알림과 다른 OPENING 알림, 현재 재고로 다시 판단. 수동 트리거임을 명시. |
| 3:40~4:00 | 점주 이점 정리. | 마감 판매 한 번 업로드, 근거 있는 발주 판단, 구매와 수령 구분으로 재고 오차 감소. |

영업 전 시연은 시간을 기다리는 대신 자동 스케줄과 같은 서버 로직을 수동 실행한 것임을 밝힌다. 실제 푸시 검증 전에는 서버 발송 상태를 휴대폰 수신 성공으로 표현하지 않는다. 실제 알림 영상은 Firebase/앱 연동 후 준비.

## 확인된 검증과 남은 검증

- MySQL 빈 DB: bootstrap callback + V001~V009 적용 및 앱 기동 확인. 모의 기존 V008 DB: V009 업그레이드 확인.
- 전체 자동 테스트: 기존 전략별 동시성·Redis 장애, POS/리포트/입고/알림 검증 포함. 최신 실행 개수와 결과는 PR에 기록.
- 실제 HTTP: 로그인→샘플→POS→중복 업로드→리포트→판단→발주→입고→영업 전 이벤트 확인.
- 아직 필요: 실제 기기 native 파일 업로드, 두 알림 실제 수신·화면 이동, 실제 외부 가격/데이터 검증, 팀원 앱 전체 E2E.
