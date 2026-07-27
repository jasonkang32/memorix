# Memorix Android Native

Memorix의 현재 Google Play 출시 대상은 **Android Native Kotlin/Jetpack Compose 앱**입니다.

## 제품 방향

Memorix는 사진·영상·문서를 Work와 Personal로 나눠 조용히 보관하고, 나중에 태그·메모·날짜·OCR 맥락으로 찾는 로컬 우선 기록함입니다.

- 광고 없음
- 로컬 우선 저장
- Work / Personal 분리
- 사진·영상·문서 가져오기
- 태그/검색/상세 편집
- 숨긴 보관함 및 앱 잠금
- 백업/복원
- Pro Google Drive 동기화
- 한국어/영어/일본어 언어 선택 기반

## 활성 작업 루트

```bash
/home/mebon/project/memorix/android-native
```

## 패키지 정보

- namespace: `com.mebonsoft.memorix`
- applicationId: `com.mebonsoft.memorix`
- app label: `Memorix`
- minSdk: 26
- targetSdk: 35

## 개발 검증 명령

```bash
cd /home/mebon/project/memorix/android-native
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:compileDebugKotlin --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

Telegram 테스트용 APK는 ABI split 때문에 보통 아래 파일을 전달합니다.

```bash
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

## Google Play 릴리즈 명령

Play Console 업로드용은 APK가 아니라 AAB입니다.

```bash
cd /home/mebon/project/memorix/android-native
./gradlew :app:bundleRelease --no-daemon
ls -lh app/build/outputs/bundle/release/*.aab
```

현재 release signing 설정은 별도 upload keystore로 고정되어 있지 않으므로, Play 업로드 전 `keystore.properties` 또는 환경변수 기반 signing 설정을 추가해야 합니다.

## 수익 모델 방향

초기 출시 모델:

- Free: 기본 보관/검색/태그 체험
- Pro Lifetime: 광고 없는 평생 구매
- 권장 상품 ID: `memorix_pro_lifetime`
- 권장 한국 가격: `₩14,900`

Pro+ 구독은 자체 S3/R2 기반 Memorix Cloud, 서버 비용이 드는 AI 기능을 붙일 때만 검토합니다.

## Google Drive 개인 연결

Phase 1 클라우드 동기화는 사용자 본인의 Google Drive `appDataFolder`에 백업 ZIP을 저장하는 방식입니다. 설정값과 테스트 절차는 아래 문서에 있습니다.

- `docs/google-drive-phase1-setup.md`

## 출시 계획 문서

- `docs/plans/2026-07-20-google-play-release-plan.md`
