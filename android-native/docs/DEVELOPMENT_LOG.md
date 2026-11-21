# Memorix Android Native 개발 기록

다른 개발자·에이전트(Claude Code, Antigravity, Codex 등)가 이어서 작업할 수 있도록 지금까지의 변경과 결정 이유를 남긴다.
최신 항목이 위에 온다. 코드와 이 문서가 다르면 코드가 기준이다.

- 활성 개발 루트: `android-native/` (Flutter `lib/`는 참고용 과거 구현)
- 작업 브랜치: `feat/android-source-sync-20261022` (기본 브랜치 `master`)
- 마지막 커밋: `1c48973 feat: Google Drive 자동 백업 보완과 Android 소스 동기화`
- 2026-10-31 기준 아래 "미커밋 변경" 항목은 아직 커밋되지 않았다.

---

## 빌드·검증

```bash
cd android-native
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Windows(Git Bash) 개발 PC:

- `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` (시스템 JDK 22로는 빌드 실패)
- `local.properties`: `sdk.dir=C:/Users/stormdevman/AppData/Local/Android/Sdk` — 백슬래시를 쓰면 이스케이프로 깨진다
- 경로 비교 테스트는 `File.invariantSeparatorsPath`를 쓴다(Windows 구분자 차이)

APK 전달 규칙: debug APK(`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`)를
`memorix-<versionName>_yyMMdd.apk` 이름으로 `G:\내 드라이브\MEBON\develop\memorix`에 복사한다.
versionName이 그대로면 같은 날 다시 만들 때 덮어쓴다.

---

## 2026-11-08 (미커밋 변경)

### 업무·개인 맥락 검색과 날짜 범위
- BOSSKIT과 연결하지 않고 Memorix 자체의 사진 검색 흐름을 개선.
- 업무·개인 검색에 제목·국가·지역·원본 표시 파일명을 포함하고, 공백으로 구분한 키워드를 필드와 순서에 관계없이 모두 만족하도록 변경. `#태그` 입력 지원.
- 기존 잠금·비밀 목록 정책은 유지. 사진 원본 저장·Drive 백업 구조와 DB 스키마는 변경하지 않음.
- 전용 검색 화면에 촬영/이벤트 날짜 범위 추가. 기기 현지 날짜 기준, 양 끝 날짜 포함, 한쪽만 지정 가능, 잘못된 범위 안내. 검색어가 없어도 앨범·유형·기간 필터 결과를 표시.
- 상세 범위·후속 후보: `2026-11-08-context-search.md`.
- 검증: `:app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=1` (JBR, heap 768MB) 통과. JVM/Robolectric 테스트 167개, 실패 0개.
- APK: `G:\내 드라이브\MEBON\develop\memorix\memorix-0.1.4_261108.apk` 복사 및 SHA-256 일치 확인.
- 연결된 Android 기기가 없어 실기기 화면, Drive 인증·복원은 이번에 검증하지 못함.

## 2026-11-03 (미커밋 변경)

### 업무·개인 상단 필터 통일
- 업무탭 기준으로 필터 대화상자, 선택 조건 요약/개별 해제, 정렬 버튼을 `feature/common/MediaFilters.kt`로 공통화.
- 개인탭의 상시 유형 칩을 상단 필터 버튼으로 변경하고 활성 표시, 유형·태그 선택, 적용/초기화를 업무탭과 동일하게 연결.
- 개인 태그 필터는 검색어·미디어 유형과 함께 적용되며, 같은 태그를 다시 선택하면 해제된다. 자주 쓴 태그는 개인 공간에서 최대 8개를 조회.
- 개인 ViewModel 테스트에 태그·유형·검색 조합, 태그 해제, 개인 공간 태그 조회 검증 추가.
- 검증: `:app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=1` (Gradle heap 768MB) 통과.
- APK: `G:\내 드라이브\MEBON\develop\memorix\memorix-0.1.4_261103.apk` 복사 및 SHA-256 일치 확인.

## 2026-11-01 (미커밋 변경)

### 타임라인 카드 여백 조정
- 업무·개인 공통 `TimelineCard`의 상단/하단 영역 세로 padding을 10dp → 14dp로 늘려 카드 높이를 총 16dp 확대.
- `WorkTimeline`의 카드 사이 간격을 12dp → 8dp로 축소.
- 검증: `:app:testDebugUnitTest :app:assembleDebug` 통과. PC 메모리 부족으로 기본 실행이 중단되어 `--max-workers=1` 및 Gradle heap 768MB 설정으로 재실행.
- APK: `G:\내 드라이브\MEBON\develop\memorix\memorix-0.1.4_261101.apk` 복사 및 SHA-256 일치 확인.

## 2026-10-31 (미커밋 변경)

### 입력부 순서 변경
- 등록 화면(`feature/work/compose/MediaComposeScreen.kt`): 이벤트 날짜 → **위치(국가·지역) → 메모 → 태그**
- 상세 화면(`feature/detail/MediaDetailScreen.kt`): 미디어 정보 → 위치 → 메모 → 태그 → 보호 → 업무/개인 이동 → 날짜 → OCR → 삭제

### 잠금 / 비밀 분리 (DB 7 → 8)
요구: 비밀로 저장해도 목록에는 보이고 화면은 가리며, 누르면 생체인증(우선) 또는 PIN을 요구. 완전히 숨기는 비밀은 설정 화면에서만 진입.

| 보호 단계 | 필드 | 목록 | 열기 | 파일 |
|---|---|---|---|---|
| 일반 | — | 보임 | 바로 | 평문 |
| 잠금 | `isLocked` | 보임(미리보기·메모·태그 가림) | 생체인증 → 없으면 PIN | 암호화 |
| 비밀 | `isSecret` | 숨김 | 설정 > 비밀 보관함 | 암호화 |

- `MediaItemEntity.isLocked` 추가. `MIGRATION_7_8`에서 기존 `isSecret=1` 항목은 모두 잠금으로 옮긴다(파일은 이미 암호화 상태라 그대로).
- `core/security/MediaProtection.kt`: 일반/잠금/비밀 enum, `withProtection()`. 상세 화면 "보호" 선택이 이것을 쓴다.
- `core/security/ProtectedListPolicy.kt`: 비밀은 목록 제외, 잠금은 검색어가 있으면 제외(메모 내용으로 존재가 드러나지 않게).
- `core/security/SecretMediaStorageManager.kt`: 잠금 또는 비밀(`isProtected`)이면 파일 암호화.
- 인증
  - `feature/auth/ProtectedMediaGate.kt`: 상세 화면 진입 시 인증 화면. `AuthRepository.hasEnrolledBiometric()`(BIOMETRIC_STRONG)이 참이면 생체인증 프롬프트, 그 프롬프트의 "PIN 입력" 또는 오류 시 PIN 대화상자. 둘 다 없으면 그대로 연다.
  - `core/security/ProtectedMediaSession.kt`: 한 번 인증하면 앱 사용 중 유지. 백그라운드 60초 이상이면 재잠금(공유·저장 위치 선택 왕복마다 재인증하지 않기 위해). 비밀 보관함도 같은 세션을 쓴다.
  - 상세 화면에서 일반 상태로 연 뒤 잠근 경우는 그 화면에서 다시 막지 않는다.
- 목록
  - 업무·개인 타임라인(`feature/work/timeline/*`): 잠금 그룹은 "잠긴 기록" 블록으로 표시. 카드 자물쇠 = 잠금 설정(이미 잠긴 카드는 열기). 해제는 상세 화면에서만.
  - 홈 그리드(`MediaThumbnailCard`): 잠금이면 자물쇠 타일.
  - 앨범 표지(`AlbumDao`): 잠금·비밀 항목은 표지로 쓰지 않는다.
  - PIN도 생체정보도 없으면 잠금·비밀을 설정할 수 없다.
- 업무 화면의 예전 "비밀 업무 보기" 대화상자(`SecretUnlockDialog`)와 WorkViewModel의 unlock 상태는 제거했다(상세 화면 게이트로 통일).

### 잠금·비밀 미리보기가 안 되던 버그
- 원인: 암호화 파일(`.mrxsecret`)을 화면에서 복호화하는 코드가 없었다.
- `core/security/SecretMediaCrypto.kt`: 암복호화를 매니저에서 분리. `plainFile()`은 재생·공유·저장용으로 `cacheDir/secret-view/`에 복호화(다음 앱 시작 때 삭제).
- `core/security/SecretFileFetcher.kt`: Coil Fetcher. `MemorixApplication`이 `ImageLoaderFactory`로 등록. 이미지는 메모리에서 복호화.
- 상세 화면의 영상 재생, 공유, 저장, ZIP/PDF 내보내기, 외부 앱 열기는 `plainFile()`을 쓴다.
- 알려진 한계: 복호화가 파일 전체를 메모리에 올린다(큰 영상은 OOM 가능). 영상 공유 시 복호화가 메인 스레드에서 돈다.

### 세로 사진이 가로로 보이던 버그
- 원인: `MediaMetadataReader.createPhotoThumbnail`이 `BitmapFactory`로 디코드해 EXIF 회전을 무시. 상세 화면은 원본을 Coil로 열어 정상이었다.
- `core/media/ExifOrientationSupport.kt`: EXIF 회전/반전 적용, 90/270도면 width/height 교환.
- `core/media/ThumbnailOrientationRepairRunner.kt`: 앱 시작 시 1회 기존 썸네일 재생성(SharedPreferences `memorix_media_repair` / `thumbnail_orientation_v2`). 암호화 항목은 임시 복호화 후 다시 암호화.

### 국가 선택
- `core/locale/CountryCatalog.kt`: 주요 국가 57개 + 전체 ISO 국가(가나다순). 한국어/영어/코드 검색. 기본값 대한민국.
- `feature/common/CountryPickerField.kt`: 검색 대화상자. 목록에 없는 값은 "직접 입력"으로 저장.
- DB 저장값은 기존과 같은 **한국어 국가명**(Geocoder `countryName`, 기존 데이터와 호환). 타임라인 국기는 이름→코드로 변환해 표시.
- 등록 화면: 국가 기본값이 대한민국이어도 사진 위치정보가 있으면 덮어쓴다(사용자가 바꾼 경우 제외). 기본값만 있는 상태는 "작성 중"으로 보지 않는다.
- 지역은 직접 입력 그대로.

### 설정 화면 Pro 표시
- "Memorix Pro 활성화됨" 카드 제거, 제목 옆 `PRO`/`FREE` 배지. 누르면 Free일 때 구매·구매 복원 대화상자(설정의 유일한 구매 진입점이라 유지).
- debug 빌드는 `UNLOCK_PRO_FOR_DEV=true`라 항상 PRO.

### 그 밖에
- `app/build.gradle.kts`: `local.properties`의 `memorix.debugKeystore`(+선택 `memorix.debugKeyAlias`)로 공용 debug keystore 지정 가능. PC마다 SHA-1이 달라 Drive 연결이 실패하는 문제 대응.
- `.gitignore`: `**/client_secret*.json`.

---

## 2026-10-30 (커밋 `1c48973`)

### 소스 동기화
- `memorix-android-source.zip` 내용을 반영(자동 백업 Worker/스케줄러, `DriveTokenProvider`, `CloudSyncPreferences` 등).

### Google Drive 연동 점검·보완
자세한 설정은 `android-native/docs/google-drive-phase1-setup.md`.

- 빠른 백업(originals 제외)은 파일명 `_quick` 접미사. 보관 개수(7개) 정리 시 **최신 전체 백업 1개는 항상 보존**.
- 복구는 최신 전체 백업 우선, 없을 때만 빠른 백업(원본 미포함 안내).
- 재시도: 네트워크(IOException)·408/429/5xx·Play 서비스 7/12502만 Worker에서 최대 5회. 판단은 `DriveSyncException.transient` 필드로(문구 매칭 제거).
- 401 재시도가 다시 실패해도 `DriveSyncException`으로 매핑. 업로드·자동 백업·복구는 `Mutex`로 직렬화.
- 사용하지 않던 `DriveAutoBackupScheduler.runNow()` 제거.

### Google Cloud Console 상태 (2026-10-30 확인)
- 프로젝트: SyncKey (`synckey-65207`)
- 동의 화면: 프로덕션 단계, 외부
- Android OAuth 클라이언트 "Memorix debug": 패키지 `com.mebonsoft.memorix`, SHA-1 `08:B2:48:AB:E3:75:B2:EA:F9:C0:AB:69:A5:47:96:11:23:79:0D:03`(Windows PC debug keystore)
- Google Drive API 사용 설정됨. 데이터 액세스 scope는 미등록(`drive.appdata`는 비민감 scope라 없어도 동작)
- 문서에 있는 Linux SHA-1(`7D:C6:...:1F:88`) 클라이언트는 이 프로젝트에 없다. Linux에서 빌드하려면 클라이언트를 추가하거나 `memorix.debugKeystore`로 Windows keystore를 공유한다.
- 출시 전: Play App Signing SHA-1로 Android 클라이언트를 따로 등록해야 한다. release 빌드에 signingConfig가 아직 없다.

---

## 남은 일 / 결정 필요

- [ ] 실기기 검증: Drive 연결·백업·복구, 잠금 인증 흐름, DB 7→8 마이그레이션 후 목록, 썸네일 회전 복구
- [ ] "지금 동기화"(수동 백업)가 자동 백업 범위 설정(전체/빠른)을 따라간다 — 수동은 항상 전체로 할지 결정
- [ ] Drive 백업 보관 개수(7개) 설정 UI 없음
- [ ] 큰 영상 복호화 시 메모리 사용(스트리밍 복호화로 바꿀지)
- [ ] release signingConfig, Play App Signing SHA-1 등록
- [ ] versionName/versionCode 갱신 규칙(지금은 0.1.4 / 5 고정)
- [x] 루트 `AGENTS.md`의 Flutter 중심 설명 정리 (및 `README.md`, `TODO.md`, `CLAUDE.md`, `.claude/commands/build.md`를 `android-native` 중심으로 업데이트)
