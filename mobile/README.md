# 냉장 G.O.A.T — Android 앱 (React Native / Expo)

> 🔔 푸시 알림(FCM) 실행·테스트 방법은 [`PUSH_TESTING.md`](PUSH_TESTING.md)

최신 통합 실행 방법은 [통합 실행 안내](../docs/integration/RUNBOOK.md)를 참고하세요. 이 저장소의 백엔드는 `backend/` 폴더가 아닌 **저장소 루트**에 있습니다.

운영 탭에서 메뉴별 실제 레시피 설정·POS 양식 다운로드·판매 파일 업로드·일일 리포트를 사용합니다. 발주 탭에서는 최신 판단과 구매·배송 기록을 확인하고, 재료를 받은 뒤 입고합니다. 구매 기록만으로 재고가 늘어나지 않습니다.

`expo-document-picker`가 추가되어 **기존 APK를 새 코드로 한 번 다시 빌드해야 합니다.** 이후 JS 화면 변경은 개발 빌드에서 갱신할 수 있습니다. Node 24와 `npm ci`를 사용하며 `npm run typecheck`, `npm run test:unit`으로 검사합니다.

`frontend/`(웹)와 같은 백엔드 API를 쓰는 모바일 앱입니다.
화면 8개(로그인·회원가입, 온보딩, 매장 설정, 메인, 최저가 목록/상세, 재고, 발주, 설정)를 네이티브로 구현했습니다.

| 항목 | 내용 |
|---|---|
| 프레임워크 | Expo SDK 57 · React Native 0.86 · Expo Router(파일 기반 라우팅) |
| 데이터 | TanStack React Query v5 — `src/api`, `src/hooks`, `src/types` 는 웹 코드를 그대로 재사용 |
| 토큰 저장 | expo-secure-store (암호화 저장소) |
| 차트 | react-native-svg 로 직접 그린 30일 가격 차트 (recharts 대체) |
| 엑셀 내보내기 | expo-file-system 다운로드 → expo-sharing 공유 시트 |

## 폴더 구조

```
src/
├── app/                 화면(라우트)
│   ├── _layout.tsx      QueryClient · SecureStore 로딩
│   ├── login.tsx        로그인 / 회원가입 (+ 서버 주소 설정)
│   ├── onboard.tsx      매장 카테고리 선택
│   ├── store-setup.tsx  가입 직후 매장 운영 설정
│   └── (tabs)/          하단 탭: 메인 · 최저가 · 재고 · 발주 · 설정
├── api/  hooks/  types/ 백엔드 연동 (웹과 동일)
├── components/          공통 UI (카드, 버튼, 바텀시트, 날짜 선택, 차트)
└── lib/                 저장소 · 서버 주소 · 날짜 유틸
```

## 1. 개발 실행 (폰에서 바로 확인)

```bash
cd mobile
npm install
npx expo start          # QR 코드를 Expo Go 앱으로 스캔
```

## 2. 백엔드 연결

폰은 `localhost` 로 PC에 접속할 수 없습니다. **PC와 폰을 같은 와이파이**에 두고 PC IP를 씁니다.

1. PC에서 `ipconfig` → IPv4 주소 확인 (예: `192.168.0.12`)
2. 백엔드 실행 (`backend/` 에서 `docker compose up -d mysql redis` → `./gradlew bootRun`)
3. Windows 방화벽에서 **8080 포트 인바운드 허용**
4. 앱 로그인 화면 오른쪽 위(또는 설정 탭) **서버 주소** → `http://192.168.0.12:8080` 입력 → 연결 테스트 → 저장

에뮬레이터는 `http://10.0.2.2:8080`.
HTTP(비 HTTPS) 통신을 위해 `app.json` 에서 `usesCleartextTraffic` 을 켜 두었습니다 (배포 시 HTTPS 로 전환 후 끄기).

## 3. APK 빌드

### A. EAS 클라우드 빌드 (권장 — Android Studio 불필요)

```bash
cd mobile
npm install
npx eas-cli@latest login               # Expo 계정 (무료)
npx eas-cli@latest build -p android --profile preview
```

- 처음 실행 시 프로젝트 연결·서명 키 생성을 물어보면 모두 **Y**
- 10~20분 뒤 터미널에 뜨는 링크/QR 에서 `.apk` 다운로드 → 폰에 설치
- 기본 서버 주소는 `eas.json` 의 `EXPO_PUBLIC_API_BASE_URL` (앱 안에서도 변경 가능)

### B. 로컬 빌드 (Android Studio + JDK 17 설치된 경우)

```bash
cd mobile
npx expo prebuild -p android --clean
cd android
gradlew assembleRelease                 # → android/app/build/outputs/apk/release/app-release.apk
```

## 검사

```bash
npm run typecheck
```
