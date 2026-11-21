# Memorix

**Memorix**는 사진·영상·문서를 **Work(업무)**와 **Personal(개인)**로 나눠 조용히 보관하고, 나중에 태그·메모·날짜·위치·OCR 맥락으로 빠르게 다시 찾는 **Android Native(Kotlin / Jetpack Compose) 로컬 우선 기록함**입니다.

> "기억은 빠르게, 보관은 조용하게."

---

## 프로젝트 구조 안내

- **`android-native/` (활성 개발 루트 & Google Play 출시 대상)**
  - Kotlin, Jetpack Compose, Material 3, Hilt, Room(SQLite + FTS4), Coil, Media3, ML Kit Korean OCR, Google Drive API, Google Play Billing 기반 네이티브 앱
  - 패키지명: `com.mebonsoft.memorix` (`minSdk 26`, `targetSdk 35`)
- **`android-native/docs/DEVELOPMENT_LOG.md`**
  - 최신 변경 이력, 아키텍처/보안 설계 결정, 빌드 환경 및 남은 작업 기록
- **`docs/`**
  - 제품 디자인 가이드(`DESIGN.md`), Google Play 출시 계획, 수익화 설계, 스토어 등록 초안 및 개인정보처리방침
- **`lib/`, `test/`, `android/`, `ios/` (레거시/참고용)**
  - 초기 Flutter 구현체로 현재는 UX/기능 레퍼런스 용도로만 보존되어 있습니다.

---

## 주요 기능

1. **Work / Personal 공간 분리 및 그룹 등록**
   - 업무와 개인 미디어를 분리하여 타임라인 및 그리드 형태로 관리
   - 여러 사진·영상·문서를 한 번에 묶어(`batchGroupId`) 하나의 기록 카드로 등록 및 상세 편집
   - 외부 앱에서 **공유하기(Share to Memorix)**로 바로 업무/개인 기록 등록
2. **빠른 가져오기 및 원본 정리**
   - 갤러리 선택, 카메라 촬영, 문서(PDF 등) 가져오기, 날짜/기간 지정 일괄 가져오기 지원
   - 앱 내부 저장소로 안전하게 복사 보존 후 기기 갤러리의 원본 파일 정리(삭제) 지원
3. **맥락 검색 및 온디바이스 한국어 OCR**
   - SQLite FTS4 기반 전문 검색 (제목, 메모, 태그, 국가/지역, 추출된 OCR 텍스트)
   - ML Kit 기반 온디바이스 한국어 텍스트 인식
4. **3단계 미디어 보호 (일반 / 잠금 / 비밀)**
   - **일반**: 목록 표시 및 즉시 열람
   - **잠금 (`isLocked`)**: 목록에는 *"잠긴 기록"*으로 표시(미리보기·메모·태그 가림), 열람 시 생체인증/PIN 요구, 파일 암호화(`.mrxsecret`)
   - **비밀 (`isSecret`)**: 일반 목록과 검색에서 완전히 숨김, `설정 > 비밀 보관함`에서만 접근, 파일 암호화(`.mrxsecret`)
5. **백업 및 복원 (로컬 ZIP & Google Drive)**
   - SAF 기반 로컬 백업 ZIP 내보내기 및 복원 (암호화 파일 호환)
   - Google Drive `appDataFolder` 연동 수동/자동 백업(전체 백업 및 원본 제외 빠른 백업 `_quick`)
6. **광고 없는 Free / Pro 평생 이용권 모델**
   - Free: 최대 300개 등록 그룹까지 무료 사용
   - Pro Lifetime (`memorix_pro_lifetime`): 무제한 등록 및 고급 기능 잠금 해제

---

## 빌드 및 테스트 방법 (`android-native/`)

Windows 환경에서는 Android Studio 번들 JBR(JDK 17/21)을 `JAVA_HOME`으로 지정해 실행합니다.

```bash
cd android-native

# 단위 테스트 실행
./gradlew :app:testDebugUnitTest --no-daemon

# Debug APK 빌드 (출력: app/build/outputs/apk/debug/app-arm64-v8a-debug.apk)
./gradlew :app:assembleDebug --no-daemon

# Google Play 업로드용 Release AAB 빌드 (출력: app/build/outputs/bundle/release/app-release.aab)
./gradlew :app:bundleRelease --no-daemon
```

상세한 개발 기록과 설정 방법은 [`android-native/README.md`](android-native/README.md) 및 [`android-native/docs/DEVELOPMENT_LOG.md`](android-native/docs/DEVELOPMENT_LOG.md)를 참고하세요.
