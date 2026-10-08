# 모바일 푸시 알림(FCM) 테스트 가이드

브랜치 `feat/integrated-owner-workflow` 기준. 다른 PC·다른 폰에서 앱 실행부터 푸시 수신까지 확인하는 순서.
앱 전체 연동 테스트는 [통합 실행 안내](../docs/integration/RUNBOOK.md) 참고.

---

## 0. 한눈에 보기

| 하고 싶은 것 | 방법 | 푸시 |
|---|---|---|
| 화면·API만 빠르게 확인 | Expo Go (`npx expo start --go`) | ❌ 안 됨 |
| 푸시까지 개발하면서 확인 | **개발 빌드** APK + `npx expo start --dev-client` | ✅ |
| 시연·배포용 (아이콘 누르면 바로 실행) | **preview 빌드** APK | ✅ |

> Expo Go는 원격 푸시를 지원하지 않는다. 푸시 테스트는 반드시 개발 빌드나 preview 빌드로.

---

## 1. 코드 받기

```bash
git fetch
git checkout feat/integrated-owner-workflow
git pull
cd mobile
npm ci
```

검증한 버전: Node 24, JDK 21(백엔드용), Docker(MySQL·Redis용).

---

## 2. 백엔드 켜기 + 내 PC 정보 확인

```bash
# 저장소 루트에서 실행 (backend 폴더로 이동하지 않음)
docker compose up -d mysql redis
./gradlew bootRun            # Windows: gradlew.bat bootRun
```

**PC IP 확인** — Windows `ipconfig` → **Wi-Fi(또는 이더넷) 어댑터의 IPv4 주소**
- `vEthernet (WSL)` 항목은 무시
- 공유기에 따라 `192.168.x.x` 또는 `172.30.1.x` 형태

**방화벽 열기** (Windows, 관리자 cmd에서 한 번만)
```cmd
netsh advfirewall firewall add rule name="Spring 8080" dir=in action=allow protocol=TCP localport=8080
netsh advfirewall firewall add rule name="Metro 8081" dir=in action=allow protocol=TCP localport=8081
```

**확인** — 폰(같은 Wi-Fi) 브라우저에서 `http://<PC IP>:8080/swagger-ui/index.html` 이 열리면 OK.

---

## 3. 앱이 붙을 서버 주소 정하기

우선순위: **앱에서 직접 입력한 값 > `mobile/.env` > 코드 기본값(`192.168.0.10`)**

| 실행 방식 | 서버 주소를 읽는 곳 |
|---|---|
| `expo start` (Expo Go·개발 빌드) | **내 PC의 `mobile/.env`** |
| preview/development **빌드 APK 자체**에 박히는 기본값 | `mobile/eas.json`의 `env` (빌드한 사람 PC IP로 되어 있음) |

`eas.json` 값은 빌드할 때만 쓰인다. 각자 PC에서는 `.env`를 만들거나 앱에서 직접 바꾼다.

```cmd
cd mobile
echo EXPO_PUBLIC_API_BASE_URL=http://<내 PC IP>:8080> .env
```

`.env`는 `.gitignore` 대상. 커밋하지 않는다.
`.env`를 만들었거나 바꿨으면 `npx expo start --dev-client -c`(캐시 삭제)로 재시작.

**가장 간단한 방법:** 앱 로그인 화면 **오른쪽 위 서버 주소** → `http://<내 PC IP>:8080` → **연결 테스트** → 저장. 앱에 저장되어 계속 유지된다.

---

## 4. 개발 빌드 설치

### 4.1 이미 빌드된 APK 받기 (권장)

같은 Expo 프로젝트 멤버로 초대받았다면 expo.dev → **Projects → naengjang-goat → Builds** → 최신 **Development / Android** → **Install** QR을 폰으로 스캔해 설치.

### 4.2 직접 빌드

```bash
cd mobile
npx eas-cli@latest login
npx eas-cli@latest build -p android --profile development
```

- 처음이면 EAS 프로젝트 생성·Keystore 생성 질문에 모두 **Y**
- 마지막 **"Install and run on an Android emulator?" → N**
  (Y면 Android Studio 없는 PC에서 `spawn adb ENOENT` 에러. 빌드 자체는 성공한 것)
- 10~20분 뒤 expo.dev 빌드 페이지의 **Install** QR로 설치

> 개발 빌드는 **네이티브 설정이 바뀔 때만** 다시 만든다(패키지 추가, `app.json` 플러그인·Firebase 설정 변경 등). JS·화면 수정은 `git pull` 후 expo 터미널에서 `r`이면 반영.

---

## 5. 실행 → 토큰 등록

```bash
cd mobile
npx expo start --dev-client
```

1. 폰에서 설치한 **냉장 G.O.A.T** 앱 실행 → 개발 서버 목록에서 `http://<PC IP>:8081` 선택 (안 보이면 **Scan QR code**로 터미널 QR 스캔)
2. 로그인 화면 서버 주소 확인 → `demo / demo1234` 로그인
3. **알림 권한 팝업 → 허용**
4. expo 터미널에 `[FCM] token xxxx` 출력
5. DB 확인
   ```sql
   SELECT username, fcm_token FROM users WHERE username = 'demo';
   ```
   `xxxx:APA91b...` 형태의 긴 문자열이면 등록 성공

토큰은 **로그인할 때**, **앱 시작할 때(로그인 상태)**, **토큰이 바뀔 때** 자동으로 `PATCH /api/users/fcm-token`에 등록된다.
앱을 재설치하면 토큰이 바뀐다.

---

## 6. 수신 테스트 (Firebase 콘솔)

Firebase 프로젝트 `naengjanggoat` 멤버 권한 필요(없으면 멤버에게 토큰을 전달해 대신 발송 요청).

1. console.firebase.google.com → **naengjanggoat**
2. **실행 → Messaging** → 새 캠페인 → **Firebase 알림 메시지**
3. 제목·텍스트 입력 → 오른쪽 **테스트 메시지 전송**
4. **FCM 등록 토큰 추가**에 5번 토큰 붙여넣기 → **+** → **테스트**

캠페인을 게시할 필요 없음. 테스트 버튼만 누르면 해당 토큰으로만 간다.

| 보낼 때 앱 상태 | 기대 결과 |
|---|---|
| 앱 켜놓고 보는 중 | 상단 배너 + expo 터미널에 `[FCM] received <제목>` |
| 홈으로 나간 상태 | 알림창에 표시 → 탭하면 **발주 탭** |
| 최근 앱에서 완전 종료 | 알림창에 표시 → 탭하면 앱 실행 후 **발주 탭** |

콘솔 테스트 메시지에는 `notificationId`가 없어서 발주 탭으로 가는 게 정상.

---

## 7. 서버가 보내는 실제 알림

원본 백엔드 `gm-15/naengjang-goat_backend` 최신(`docs/handoff/API_CONTRACT.md` §5) 계약 기준.

| type | 언제 |
|---|---|
| `UPLOAD` | POS 판매 엑셀 신규 반영 직후 |
| `OPENING` | 영업 시작 3시간 전 |

FCM data:
```json
{"notificationId":"77","type":"OPENING","businessDate":"2026-10-08","route":"closing"}
```

앱 동작:
- 알림 탭 → `/notification/[id]` 상세 화면 → `GET /closing/notifications/{id}` 로 제목·본문·발송 상태·발송 당시 판단(재고 부족·매수 신호·권장 발주량) 표시
- 같은 `notificationId`의 포그라운드 배너는 현재 서버·계정 기준으로 중복 방어. 동시에 중복 전달된 탭 이벤트는 한 번만 이동하며, 나중에 같은 알림을 다시 열 수 있음.
- 로그아웃 상태에서 탭하면 로그인 후 원래 알림 상세로 이동. 서버 주소를 바꾸면 다른 서버의 로그인·알림 기록을 정리함.
- 로그아웃할 때 해당 앱이 등록한 FCM 기기 연결을 조건부로 해제하며, 다른 기기의 최신 연결은 유지함.

서버에서 실발송하려면 (백엔드 담당):
```bash
# 서비스 계정 JSON은 절대 커밋 금지. 레포 밖 경로에 둔다
export FIREBASE_CONFIG_PATH=file:/절대경로/firebase-service-account.json
export SPRING_PROFILES_ACTIVE=demo
./gradlew bootRun
# 영업 전 알림 수동 발생 (demo 계정 토큰 필요)
curl -X POST "http://localhost:8080/demo/opening-trigger?businessDate=2026-10-08" -H "Authorization: Bearer <accessToken>"
```

> 이 저장소 루트의 최신 백엔드에 `/closing/notifications/{id}`·`/demo/opening-trigger`가 구현되어 있습니다. `google-services.json`은 앱 설정이고, 서버 실발송에는 기존 Firebase 프로젝트의 **서비스 계정**을 `FIREBASE_CONFIG_PATH`로 별도 연결해야 합니다.

---

## 8. 문제 해결

| 증상 | 원인 / 해결 |
|---|---|
| 앱에서 "서버에 연결할 수 없음 192.168.0.10:8080" | `.env` 없음 → 3번처럼 `.env` 생성 후 `-c` 재시작, 또는 앱에서 서버 주소 직접 입력 |
| 폰 브라우저에서도 8080 안 열림 | 백엔드 꺼짐 / 방화벽 / 다른 Wi-Fi |
| 개발 서버 목록에 PC가 안 뜸 | 8081 방화벽, 같은 Wi-Fi 확인, QR 스캔으로 접속 |
| `spawn adb ENOENT` | 빌드는 성공. 에뮬레이터 설치 단계만 실패 → expo.dev에서 Install QR로 설치 |
| `fcm_token`이 NULL | 알림 권한 거부 → 폰 설정 → 앱 → 알림 허용 후 로그아웃·재로그인 / expo 터미널 `[FCM]` 에러 확인 |
| 토큰은 있는데 알림이 안 옴 | ① 토큰 전체가 복사됐는지(150자 내외, 공백 없음) ② 콘솔 프로젝트가 `naengjanggoat`인지 ③ 프로젝트 설정 → 클라우드 메시징 → FCM API(V1) 사용 설정 ④ 폰 알림 채널·방해 금지·절전 예외 |
| 포그라운드 로그는 찍히는데 배너 안 뜸 | 폰 설정 → 앱 → 알림 → 채널(기본 알림/기타) 켜기 |
| 상세 화면 404 | 다른 계정의 알림이거나, 백엔드가 원본 최신이 아님(7번 참고) |

---

## 9. 관련 파일

| 파일 | 역할 |
|---|---|
| `src/lib/notifications.ts` | 권한·채널·FCM 토큰 등록, 수신 처리, 중복 방어, 탭 이동 |
| `src/hooks/useAuth.ts` | 로그인·회원가입 성공 시 토큰 등록 |
| `src/app/_layout.tsx` | 앱 시작 시 토큰 재등록 + 리스너 등록 |
| `src/app/notification/[id].tsx` | 알림 상세 화면 |
| `src/api/closing.ts`, `src/types/notification.ts` | 알림 상세 API·타입 |
| `app.json` | `googleServicesFile`, `expo-notifications` 플러그인 |
| `eas.json` | `development`(개발 빌드) / `preview`(단독 APK) 프로필 |
| `google-services.json` | Firebase Android 클라이언트 설정(비밀 아님). **서비스 계정 키와 다름** |
