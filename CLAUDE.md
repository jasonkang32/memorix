# CLAUDE.md

This file provides guidance when working with the Memorix repository.

## 프로젝트 개요

**Memorix** — Android Native Kotlin/Jetpack Compose 기반 로컬 우선 개인 기록함.  
"기억은 빠르게, 보관은 조용하게."

사진·영상·문서를 Work와 Personal로 나눠 보관하고, 태그·메모·날짜·OCR 맥락으로 나중에 찾는 앱입니다.

> Flutter 코드는 레퍼런스/과거 구현입니다. 현재 Google Play 출시 대상과 활성 개발 루트는 Android Native입니다.

## 활성 개발 루트

```bash
/home/mebon/project/memorix/android-native
```

## 빌드 및 검증 명령

```bash
cd /home/mebon/project/memorix/android-native

# 단위 테스트
./gradlew :app:testDebugUnitTest --no-daemon

# Kotlin 컴파일
./gradlew :app:compileDebugKotlin --no-daemon

# Telegram/실기기 테스트용 debug APK
./gradlew :app:assembleDebug --no-daemon

# Google Play 업로드용 release AAB
./gradlew :app:bundleRelease --no-daemon
```

테스트용 APK 기본 경로:

```bash
/home/mebon/project/memorix/android-native/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Play 업로드용 AAB 기본 경로:

```bash
/home/mebon/project/memorix/android-native/app/build/outputs/bundle/release/app-release.aab
```

## 현재 Android 패키지

- namespace: `com.mebonsoft.memorix`
- applicationId: `com.mebonsoft.memorix`
- app label: `Memorix`
- minSdk: 26
- targetSdk: 35

## 기술 스택

- Kotlin
- Jetpack Compose + Material 3
- Navigation Compose
- Hilt
- Room + SQLite/FTS
- DataStore
- Android MediaStore/FileProvider
- Coil
- Media3
- ML Kit Korean Text Recognition
- AndroidX Biometric / Security Crypto
- WorkManager

## 제품 방향

Memorix는 갤러리 대체 앱이 아니라 **업무와 개인 기록을 나눠 보관하는 조용한 로컬 기록함**입니다.

핵심 가치:

- Work / Personal 분리
- 사진·영상·문서 로컬 보관
- 원본 파일 앱 내부 복사 보존
- 태그/메모/날짜/OCR 기반 검색
- 숨긴 보관함 및 앱 잠금
- 백업/복원
- 광고 없음
- 한국어/영어/일본어 언어 선택

## 수익 모델 방향

초기 Google Play 출시 모델:

- Free: 기본 체험형
- Pro Lifetime: 평생 구매
- 권장 상품 ID: `memorix_pro_lifetime`
- 권장 한국 가격: `₩14,900`

Pro 후보 기능:

- 항목 무제한
- 문서/PDF 등록
- OCR 검색/실행
- 백업/복원
- 숨긴 보관함
- 태그 관리
- 날짜별 일괄 가져오기
- 고급 필터

광고 모델은 사용하지 않습니다.

## Google Play 출시 계획

출시 준비 계획 문서:

```bash
docs/plans/2026-07-20-google-play-release-plan.md
```

릴리즈 게이트:

- Debug test/build 통과
- Release AAB 생성 통과
- Release signing/upload key 설정
- Free/Pro entitlement + Play Billing 검증
- 개인정보처리방침 작성
- Data Safety 답변 정리
- Store listing/screenshot 준비
- Internal testing track 실기기 검증

## 개발 원칙

- 현재 활성 코드는 Android Native 기준으로 판단합니다.
- Flutter 구현은 UX/기능 참고용으로만 확인합니다.
- 모바일 변경 후에는 최소 `:app:testDebugUnitTest`와 `:app:assembleDebug`를 실행합니다.
- Play 배포 관련 변경 후에는 `:app:bundleRelease`도 실행합니다.
- 비밀키, keystore, Play credentials는 절대 커밋하지 않습니다.
- 사용자-facing 문구는 기본 한국어, 다국어 확장 시 `AppLanguage` / `MemorixStrings` 구조를 사용합니다.
