# CLAUDE.md

This file provides guidance when working with the Memorix repository.

## 프로젝트 개요

**Memorix** — Android Native Kotlin/Jetpack Compose 기반 로컬 우선 개인 기록함.  
"기억은 빠르게, 보관은 조용하게."

사진·영상·문서를 Work와 Personal로 나눠 보관하고, 태그·메모·날짜·위치·OCR 맥락으로 나중에 찾는 앱입니다.

> Flutter 코드(`lib/`, `test/`)는 과거 레퍼런스 구현입니다. 현재 Google Play 출시 대상과 활성 개발 루트는 **`android-native/`**입니다.  
> 최근 변경 이력과 설계 결정은 반드시 `android-native/docs/DEVELOPMENT_LOG.md`를 먼저 확인하세요.

## 활성 개발 루트

```bash
cd android-native
```

## 빌드 및 검증 명령

Windows에서 실행할 때는 시스템 JDK 22 대신 Android Studio 번들 JBR(`JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`)을 사용합니다.

```bash
cd android-native

# 단위 테스트
./gradlew :app:testDebugUnitTest --no-daemon

# Kotlin 컴파일
./gradlew :app:compileDebugKotlin --no-daemon

# 실기기 테스트용 debug APK
./gradlew :app:assembleDebug --no-daemon

# Google Play 업로드용 release AAB
./gradlew :app:bundleRelease --no-daemon
```

테스트용 APK 기본 경로 및 전달 규칙:

- 생성 경로: `android-native/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
- 전달 규칙: `memorix-<versionName>_yyMMdd.apk` 이름으로 `G:\내 드라이브\MEBON\develop\memorix`에 복사 (같은 날 같은 버전이면 덮어쓰기)

Play 업로드용 AAB 기본 경로:

- `android-native/app/build/outputs/bundle/release/app-release.aab`

## 현재 Android 패키지

- namespace: `com.mebonsoft.memorix`
- applicationId: `com.mebonsoft.memorix`
- app label: `Memorix`
- minSdk: 26 / targetSdk: 35
- Room DB version: `8`

## 기술 스택

- Kotlin + Jetpack Compose + Material 3
- Navigation Compose + Hilt
- Room + SQLite/FTS4 (`MemorixDatabase` v8)
- DataStore Preferences
- Android MediaStore / FileProvider / ExifInterface
- Coil (암호화 파일용 `SecretFileFetcher` 포함)
- Media3 ExoPlayer
- ML Kit Korean Text Recognition
- AndroidX Biometric / Security Crypto + 자체 `.mrxsecret` 암복호화 (`SecretMediaCrypto`)
- Google Drive API v3 (`appDataFolder` 동기화) + WorkManager (`DriveAutoBackupWorker`)
- Google Play Billing (`billing-ktx`)

## 제품 방향

Memorix는 갤러리 대체 앱이 아니라 **업무와 개인 기록을 나눠 보관하는 조용한 로컬 기록함**입니다.

핵심 가치:

- Work / Personal 분리 및 배치 그룹(`batchGroupId`) 단위 기록
- 사진·영상·문서 로컬 보관 및 원본 파일 앱 내부 복사 보존
- 태그/메모/날짜/국가·지역/OCR 기반 검색
- 3단계 보호: **일반** / **잠금**(`isLocked`, 목록 노출·열람 시 생체인증/PIN) / **비밀**(`isSecret`, 목록 숨김·설정 비밀 보관함 전용)
- 로컬 ZIP 백업/복원 및 Google Drive 자동/수동 백업
- 광고 없음
- 한국어/영어/일본어 언어 선택

## 수익 모델 방향

초기 Google Play 출시 모델:

- Free: 기본 체험형 (최대 300개 등록 그룹)
- Pro Lifetime: 평생 구매 (`memorix_pro_lifetime`, 권장가 `₩14,900`)
- Debug 빌드는 `BuildConfig.UNLOCK_PRO_FOR_DEV = true`로 Pro가 기본 활성화됩니다.

## 주요 문서

- 개발 로그 (필독): `android-native/docs/DEVELOPMENT_LOG.md`
- Google Drive 설정: `android-native/docs/google-drive-phase1-setup.md`
- 출시 준비 계획: `docs/plans/2026-07-20-google-play-release-plan.md`

## 개발 원칙

- 현재 활성 코드는 `android-native/` 기준으로 판단합니다.
- 모바일 변경 후에는 최소 `:app:testDebugUnitTest`와 `:app:assembleDebug`를 실행합니다.
- 경로 비교 테스트는 Windows/Linux 호환을 위해 `File.invariantSeparatorsPath`를 사용합니다.
- Play 배포 관련 변경 후에는 `:app:bundleRelease`도 실행합니다.
- 비밀키, keystore, `local.properties`, `client_secret*.json`, Play credentials는 절대 커밋하지 않습니다.
- 사용자-facing 문구는 기본 한국어, 다국어 확장 시 `AppLanguage` / `MemorixStrings` 구조를 사용합니다.
