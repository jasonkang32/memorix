# Memorix Phase 1: Google Drive 개인 연결 설정

Memorix Phase 1 클라우드 동기화는 사용자가 자신의 Google Drive를 직접 연결하는 방식이다. 앱은 Google Drive의 일반 파일 목록이 아니라 앱 전용 숨김 공간(`appDataFolder`)만 사용한다.

## 제품 범위

- Pro 기능: `Google Drive 동기화`
- 사용자 흐름: 설정 → Google Drive 연결 → 계정 선택 → 허용
- 백업: 현재 Memorix 전체 백업 ZIP을 Google Drive `appDataFolder`에 업로드
- 복구: Google Drive의 최신 `Memorix_Cloud_*.zip` 백업으로 복구
- 포함 데이터: DB, originals/thumbs, 비밀 보관함 `.mrxsecret`, 다른 기기 복구용 portable secret key

## Google Cloud Console 설정

### 1. API 활성화

Google Cloud Console에서 프로젝트를 만들고 아래 API를 활성화한다.

- Google Drive API

### 2. OAuth 동의 화면

- 앱 이름: `Memorix`
- 사용자 지원 이메일: Mebonsoft/JK 계정
- 개발자 연락처 이메일: Mebonsoft/JK 계정
- 테스트 단계에서는 JK Google 계정을 Test user로 추가한다.

### 3. Android OAuth Client 생성

Credentials → Create Credentials → OAuth client ID → Android

입력값:

- Package name: `com.mebonsoft.memorix`
- Debug SHA-1:
  - `7D:C6:D5:EB:31:37:EE:C4:2F:7D:1A:86:0B:62:4E:BE:40:E2:1F:88`

현재 debug keystore:

- Store: `/home/mebon/.android/debug.keystore`
- Alias: `AndroidDebugKey`
- SHA-256: `F5:E8:27:18:EA:D5:85:82:E5:41:E9:88:B2:B2:94:D5:6A:02:15:DB:67:C0:9B:29:83:34:E4:00:FF:A4:52:E6`

릴리즈 전에는 Play App Signing 또는 upload/release keystore SHA-1을 추가로 등록해야 한다.

## 앱 권한/Scope

앱은 최소 권한만 요청한다.

- Android permission: `INTERNET`
- Google Drive scope: `https://www.googleapis.com/auth/drive.appdata`

`drive.appdata`는 사용자의 전체 Drive 파일을 읽는 권한이 아니라 앱 전용 숨김 데이터 공간 접근 권한이다.

## 테스트 절차

1. Google Cloud Console에 debug OAuth client를 등록한다.
2. 테스트폰에 debug APK를 설치한다.
3. 설정 → Google Drive 동기화 → Google Drive 연결을 누른다.
4. Google 계정을 선택하고 권한을 허용한다.
5. 사진/문서 몇 개를 등록한다.
6. `지금 동기화`를 눌러 클라우드 백업을 만든다.
7. 앱 데이터를 초기화하거나 새 기기에 설치한다.
8. 같은 Google 계정 연결 후 `최신 복구`를 누른다.
9. DB, 파일, 숨김 보관함 항목까지 복구되는지 확인한다.

## 검증 명령

```bash
cd /home/mebon/project/memorix/android-native
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
/home/mebon/Android/Sdk/build-tools/35.0.0/aapt dump badging app/build/outputs/apk/debug/app-arm64-v8a-debug.apk | grep "package: name"
```

## 운영 판단

초기에는 S3/R2 같은 자체 저장소를 제공하지 않는다. 사용자 본인의 Google Drive를 사용하면 서버 저장비와 개인정보 보관 책임을 줄이면서도 새 폰 복구 가치를 Pro 기능으로 제공할 수 있다.
