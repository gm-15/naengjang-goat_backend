# 통합 서비스 실행과 점주 시연

## 저장소 구조와 코드 기준

- 루트: `gm-15/naengjang-goat_backend` 최신 main의 Spring 서버.
- `frontend/`: React 웹. `mobile/`: React Native/Expo Android 앱.
- 프론트 출발점: 팀원 `keonU206/smu_capstone_project`, `feat/fcm-push-mobile`, `6727929`.
- 현재 작업 브랜치: `feat/integrated-owner-workflow`. 팀원 저장소의 예전 `backend/`는 가져오지 않았습니다.

## 실행

Java 21, Node 24, MySQL 8, Redis가 필요합니다. 저장소 루트에서 DB/Redis를 먼저 실행하고 Spring을 켭니다. 기존 DB를 사용하면 Flyway V010까지 적용됩니다. V010은 중복 FCM 토큰의 가장 큰 사용자 ID만 연결을 유지하므로 다른 사용자의 앱은 재실행해 토큰을 등록합니다.

```bash
docker compose up -d mysql redis
bash gradlew bootRun
# Windows에서는 gradlew.bat bootRun
```

DB 연결은 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경변수로 정합니다. 기존 정상 설정을 그대로 사용하세요. 테스트는 실제 매장 DB를 사용하지 않으며 `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`로 별도 DB를 지정합니다. 일부 기존 테스트가 테이블을 비웁니다.

**새 DB의 최초 데이터 준비:** Spring을 한 번 실행해 테이블을 만든 뒤, 데이터 담당자가 기존 `crawler/ewangmart/recipes.json`을 템플릿으로 적재합니다. 데이터가 이미 있으면 기존 메뉴 라이브러리를 사용하며 이 작업은 초기 적재 단계입니다. 이 파일은 상품 구성/포장 자료이고 1인분 소비량이 아닙니다.

```bash
python -m pip install PyMySQL
# DB_HOST, DB_PORT, DB_USER, DB_PASSWORD, DB_NAME을 해당 DB에 맞춰 지정한 뒤
python crawler/ewangmart/import_template.py
```

카테고리 온보딩에 사용하는 공용 레시피 테이블이 비어 있으면 메뉴/재료가 생성되지 않습니다. 이 스크립트는 source/recipe ID 기준 기존 템플릿을 덮어쓰지 않습니다. 기본자료 68개 메뉴를 적재한 새 DB로 신규 점주 시나리오도 검증합니다. 실제 매장 메뉴·소모량은 운영 화면에서 반드시 확인합니다.

웹은 별도 터미널에서 실행합니다. 기본 프록시 대상은 백엔드 8080입니다. 다른 포트라면 `frontend/.env`의 `VITE_API_TARGET`을 변경합니다.

```bash
cd frontend
npm ci
npm run dev
```

앱은 로그인 화면 오른쪽 위에서 `http://<백엔드 PC IP>:8080`을 저장합니다. 폰과 PC는 같은 네트워크에서 서버에 도달할 수 있어야 합니다.

```bash
cd mobile
npm ci
npx expo start --dev-client
```

파일 선택 모듈이 새로 추가되었습니다. **기존 개발/preview APK를 다시 빌드·설치**한 뒤 위 명령으로 실행합니다. 기존 Firebase 프로젝트·Android 패키지 설정은 유지합니다.

```bash
npx eas-cli@latest build -p android --profile development
# 발표용 독립 실행 APK
npx eas-cli@latest build -p android --profile preview
```

Expo Go에서는 원격 푸시 검증을 할 수 없습니다. iOS 푸시 연결은 이번 Android 시연 범위에 포함하지 않습니다.

## 점주의 사용 순서

1. 가입·매장 카테고리 선택·운영 시간과 발주 요일 설정.
2. 운영 화면의 메뉴·레시피 설정에서 메뉴명, 판매가, 메뉴 한 개당 실제 재료 소모량 확인. 초기 템플릿 기본값을 실제 조리량으로 수정. POS 메뉴코드가 있으면 연결.
3. 이미 보유한 초기 재고는 재고 화면에서 실제 수량·유통기한으로 입고. 모든 입력은 표시된 기준 단위(g/ml/개)를 확인.
4. 영업 중에는 기존 POS를 사용. 앱에 수동 판매를 입력하지 않음.
5. 영업 후 운영 화면에서 POS 양식 다운로드. `판매내역` 시트의 영업일·메뉴코드·메뉴명·판매수량·판매금액을 프로젝트 양식에 맞춰 채워 업로드. 한 파일은 하루, 한 메뉴는 합산 한 행. 원본 POS 양식이 다르면 변환 필요.
6. 성공 즉시 레시피 × 판매수량 재고 차감. 같은 파일 재업로드는 추가 차감 없음. 형식/연결/부족 오류는 파일 전체 미반영. 같은 영업일 다른 파일은 덮어쓰기 없이409.
7. 일일 리포트에서 판매금액·메뉴 판매수량·메뉴별 판매·재료 소비·조회 시점 현재 재고 확인. 과거 리포트는 이후 레시피 변경으로 재계산하지 않음.
8. 업로드 직후 알림 또는 발주 화면에서 부족 재료·추천 구매량·가격 근거 확인. 소비 이력 부족은 판단 대기로 표시.
9. 실제 구매 사이트로 이동. 외부에서 주문한 경우 돌아와 상품명·수량·구매 단위·해당 단위당 가격 기록. 포장 상품은 포장당 크기까지 확인. 기록만으로 재고는 늘지 않음.
10. 구매·배송 기록의 배송 대기에서 실제 재료를 받았을 때 입고 등록. 전체 예상 기준 수량·입고일·유통기한 입력. 성공 시 한 번 재고 증가. 기존 LEGACY 기록은 재입고 대상으로 처리하지 않음.
11. 다음 영업 시작 3시간 전 별도 알림 확인. 알림 상세는 발송 당시 판단, 발주 화면은 현재 판단.

주문 취소·환불, 부분 수령, 일반 알림함/읽음 처리, AI는 이번 합의 범위에서 제외합니다. 실제 온라인 결제를 대행하지 않습니다.

## 푸시 실발송 설정

앱의 `mobile/google-services.json`과 서버 서비스 계정은 용도가 다릅니다. 기존 Firebase 프로젝트 서비스 계정을 저장소 밖에 두고 서버 실행 전에 경로를 연결합니다. 값을 채팅·Git에 올리지 않습니다.

```bash
export FIREBASE_CONFIG_PATH=file:/absolute/path/firebase-service-account.json
# PowerShell: $env:FIREBASE_CONFIG_PATH='file:C:/secure/firebase-service-account.json'
```

업로드 직후 `UPLOAD`와 영업 시작 3시간 전 `OPENING`을 각각 확인합니다. Firebase 콘솔 테스트 메시지는 백엔드 알림 상세 ID를 포함하지 않으므로 실제 POS/서버 알림 검증과 구분합니다. 서버 `SENT`는 FCM 요청 성공이며 휴대폰 표시/탭까지 별도 확인합니다. 자세한 절차는 [모바일 푸시 안내](../../mobile/PUSH_TESTING.md).

## 검사와 반복 시연

```bash
# 루트: 별도 테스트 DB 지정 후
bash gradlew test bootJar --no-daemon --max-workers=4
# frontend
npm run typecheck
npm run build
# mobile
npm run typecheck
npm run test:unit
npx expo export --platform android --output-dir dist
```

demo 프로필의 합성 시연 자료와 HTTP 기대 숫자는 [기존 시연 안내](../handoff/DEMO_RUNBOOK.md)를 참고합니다. demo setup은 기존 데이터를 초기화하지 않습니다. 동일 영업일 POS를 처음부터 다시 시연하려면 별도 새 시연 DB를 사용합니다.

브라우저 통합 검사는 `scripts/owner-flow-e2e.mjs`를 사용합니다. Playwright 설치 경로·Chromium·서버/웹 주소는 해당 스크립트 환경변수 안내에 맞춥니다. 외부 상품 응답을 대체한 검사와 실제 구매 사이트 데이터 검증은 구분합니다.
