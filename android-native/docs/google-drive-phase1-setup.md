# Memorix Google Drive 연결 · 자동 백업 설정

Memorix의 클라우드 백업은 사용자가 자신의 Google Drive를 직접 연결하는 방식이다. 앱은 Drive의 일반 파일 목록이 아니라 앱 전용 숨김 공간(`appDataFolder`)만 사용한다.

## 제품 범위 (Pro)

- 연결: 설정 → Google Drive 연결 → 계정 선택 → 권한 허용
- 수동 백업: 설정 → 지금 동기화
- 자동 백업: 주기(6/12/24/72시간), Wi-Fi 전용, 전체/빠른 범위, 최근 7개 보관
- 복구: 설정 → 최신 복구 (기기에 로컬 기록이 없을 때만)
- 포함 데이터: DB, originals/thumbs, 비밀 보관함 `.mrxsecret`, portable secret key

## 인증 방식 (2026 기준)

레거시 Google Sign-In(`com.google.android.gms.auth.api.signin.GoogleSignIn`)은 Google이 종료 절차를 진행 중이다. Memorix는 Google 권장 방식인 **AuthorizationClient**(`play-services-auth` 22.0.0)로 승인(Scope)만 처리한다.

- 로그인/승인 분리: 앱은 인증(로그인)이 필요 없고 Drive `appDataFolder` 접근만 필요하므로 승인만 요청한다.
- 토큰: `AuthorizationRequest` → `authorize()`.
  - 이미 승인됨 → 사용자 조작 없이 액세스 토큰 반환(자동 백업에서 그대로 사용).
  - 승인 필요 → `PendingIntent` 반환 → `StartIntentSenderForResult`로 동의 화면 실행.
- 토큰은 저장하지 않는다. Google Play 서비스가 캐시를 관리하며, 401을 받으면 `clearToken` 후 1회 재시도한다.
- 연결 계정 이메일은 Drive `about.user.emailAddress`로 확인한다(`userinfo` scope 불필요).

## Google Cloud Console 설정

### 1. API 활성화

- Google Drive API

### 2. OAuth 동의 화면

- 앱 이름: `Memorix`
- 사용자 지원/개발자 연락처: Mebonsoft/JK 계정
- Scope: `https://www.googleapis.com/auth/drive.appdata` (Drive API 분류상 **Recommended**, 민감/제한 scope 아님)
- **Publishing status는 `In production`으로 둔다.**
  - `Testing` 상태에서는 테스트 사용자 100명 제한과 함께 승인/토큰이 7일마다 만료된다.
  - 그 결과 자동 백업이 일주일마다 "권한 재승인 필요"로 멈춘다(실제로 겪는 "OAuth가 막히는" 대표 원인).
  - `drive.appdata`는 민감 scope가 아니므로 In production 전환에 Google 검증 심사가 필요 없다.

### 3. Android OAuth Client

Credentials → Create Credentials → OAuth client ID → Android

- Package name: `com.mebonsoft.memorix`

debug 빌드는 빌드한 PC의 `~/.android/debug.keystore`로 서명된다. 개발 PC마다 지문이 다르므로 PC별로 Android 클라이언트를 등록한다.

| 개발 환경 | Debug keystore | SHA-1 |
| --- | --- | --- |
| Linux | `/home/mebon/.android/debug.keystore` (alias `AndroidDebugKey`) | `7D:C6:D5:EB:31:37:EE:C4:2F:7D:1A:86:0B:62:4E:BE:40:E2:1F:88` |
| Windows | `C:\Users\stormdevman\.android\debug.keystore` (alias `androiddebugkey`) | `08:B2:48:AB:E3:75:B2:EA:F9:C0:AB:69:A5:47:96:11:23:79:0D:03` |

- Linux SHA-256: `F5:E8:27:18:EA:D5:85:82:E5:41:E9:88:B2:B2:94:D5:6A:02:15:DB:67:C0:9B:29:83:34:E4:00:FF:A4:52:E6`
- Windows SHA-256: `20:FE:2B:C1:51:DA:6C:98:B6:E7:9B:A2:B5:6C:69:FF:B1:68:68:F6:90:A7:D5:A8:10:10:A5:36:F0:65:50:EF`

PC마다 등록하지 않으려면 한 PC(예: Linux)의 `debug.keystore`를 다른 PC에 복사하고, 그 PC의 `local.properties`(커밋 안 됨)에 경로를 지정한다. 지정하지 않으면 기존처럼 `~/.android/debug.keystore`를 쓴다.

```properties
memorix.debugKeystore=C:/Users/stormdevman/.android/memorix-shared-debug.keystore
# alias가 androiddebugkey가 아니면 지정
# memorix.debugKeyAlias=AndroidDebugKey
```

적용 확인: `./gradlew :app:signingReport`의 `Variant: debug` SHA1이 Console에 등록된 값과 같은지 본다.

릴리즈는 Play App Signing(또는 업로드 keystore) SHA-1을 **별도 Android 클라이언트**로 추가해야 한다. SHA-1이 다르면 `ApiException: 10 (DEVELOPER_ERROR)`로 즉시 실패한다.

현재 서명 지문 확인:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias AndroidDebugKey -storepass android -keypass android | grep SHA1
```

## 자동 백업 동작

- 스케줄러: WorkManager unique periodic work (`memorix_drive_auto_backup_periodic`)
- 제약: 네트워크 연결(옵션에 따라 Wi-Fi 전용), 배터리 부족 아님
- 실행 판단(`CloudSyncOperations.runAutoBackup`):
  1. 자동 백업이 켜져 있고 Drive가 연결되어 있는지
  2. 직전 성공 이후 주기의 90%가 지났는지 (WorkManager flex 때문에 일찍 깨는 경우 방지)
  3. 데이터 지문(DB + 미디어 파일명/크기/mtime 해시)이 직전 백업과 다른지
- 파일명: 전체 `Memorix_Cloud_yyyyMMdd_HHmmss.zip`, 빠른(originals 제외) `Memorix_Cloud_yyyyMMdd_HHmmss_quick.zip`. 접미사 없는 이전 백업은 전체로 본다.
- 업로드 후 최근 N개(기본 7개)만 남기고 오래된 백업을 삭제한다. 단, **최신 전체 백업 1개는 개수와 무관하게 남긴다**(빠른 백업만 쌓여 원본이 사라지는 것 방지).
- 복구는 최신 **전체** 백업을 우선한다. 전체 백업이 없을 때만 최신 빠른 백업으로 복구하고, 원본이 없다고 안내한다.
- 수동 백업·자동 백업·복구는 한 번에 하나만 실행된다(Mutex).
- 실패 시 마지막 오류 메시지를 설정 화면에 남긴다. 네트워크(IOException)·408/429/5xx·Play 서비스 7/12502는 backoff 재시도(최대 5회), 권한 취소 같은 영구 오류는 다음 주기까지 기다린다.
- Pro entitlement가 없으면 Worker는 아무 것도 하지 않는다
- 앱 시작 시 설정이 켜져 있으면 스케줄을 다시 건다(앱 업데이트/재부팅 대비)

## 테스트 절차

1. Google Cloud Console에 debug Android OAuth client가 등록되어 있고, 동의 화면이 In production인지 확인한다.
2. debug APK 설치 → 설정 → Google Drive 연결 → 계정 선택 → 허용.
3. 사진/문서 몇 개 등록 → 지금 동기화 → Drive `appDataFolder`에 `Memorix_Cloud_*.zip` 생성 확인.
4. 자동 백업 켜기 → 주기 6시간 선택 → `adb shell dumpsys jobscheduler | grep -A5 memorix`로 작업 등록 확인.
   또는 즉시 검증: `adb shell cmd jobscheduler run -f com.mebonsoft.memorix <jobId>`.
5. 앱 데이터 초기화/새 기기 설치 → 같은 계정 연결 → 최신 복구 → DB/파일/숨김 보관함까지 복구 확인.

## 오류 대응표

| 증상 | 원인 | 조치 |
| --- | --- | --- |
| `ApiException: 10` (DEVELOPER_ERROR) | 패키지명/SHA-1 미등록, 다른 keystore로 서명 | Console에 현재 APK SHA-1 등록 |
| 403 `access_denied` / "확인되지 않은 앱" | 동의 화면 Testing 상태에서 테스트 사용자 아님 | 테스트 사용자 추가 또는 In production 전환 |
| 승인 직후 7일 뒤 "권한 재승인 필요" | Testing 상태의 토큰 만료 정책 | In production 전환 |
| 401 (Drive 호출) | 액세스 토큰 만료/캐시 불일치 | 앱이 `clearToken` 후 1회 자동 재시도 |
| 403 `insufficientPermissions` | 사용자가 Google 계정 설정에서 앱 권한 삭제 | 설정에서 Drive 다시 연결 |
| 12500 / 12501 | Play 서비스 미동작 / 사용자 취소 | Play 서비스 업데이트 후 재시도, 취소는 무시 |

## 검증 명령

```bash
cd /home/mebon/project/memorix/android-native
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
/home/mebon/Android/Sdk/build-tools/35.0.0/aapt dump badging app/build/outputs/apk/debug/app-arm64-v8a-debug.apk | grep "package: name"
```

Windows(Git Bash) — 시스템 JDK 22 대신 Android Studio JBR을 쓴다:

```bash
cd /c/Users/stormdevman/orca/memorix/android-native
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
"$JAVA_HOME/bin/keytool" -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

## 운영 판단

초기에는 자체 저장소(S3/R2)를 두지 않는다. 사용자 본인의 Drive를 쓰면 서버 저장비와 개인정보 보관 책임을 줄이면서 새 폰 복구 가치를 Pro로 제공할 수 있다. 자동 백업은 전체 백업을 기본으로 하되, 모바일 데이터 보호를 위해 Wi-Fi 전용을 기본값으로 둔다.
