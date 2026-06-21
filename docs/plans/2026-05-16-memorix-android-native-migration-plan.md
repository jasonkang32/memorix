# Memorix Android Native 전환 실행 계획

> **For Hermes:** Use this as the execution checklist for the Android Native migration. 기존 Flutter 앱을 한 번에 폐기하지 말고, 기능/데이터 모델을 순차적으로 옮긴다.

**Goal:** 기존 Memorix의 높은 기능 진척도를 최대한 활용하면서, Android Native 앱을 빠르게 부팅하고 로컬 사진 관리 MVP를 안정적으로 완성한다.

**Architecture:** Flutter 코드는 참조용 명세로 유지하고, `android-native/`에 Kotlin + Compose + Room 기반 새 앱을 세운다. 서버/Drive/구독 기능은 제거하고, import → organize → search → view 루프에 집중한다.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, WorkManager, Media3, BiometricPrompt

---

## 마이그레이션 원칙

1. **화면을 옮기기 전에 데이터 모델을 단순화한다.**
2. **복잡한 기능보다 저장 안정성 버그를 먼저 잡는다.**
3. **Flutter 코드는 구현 재사용이 아니라 요구사항 검증 자료다.**
4. **서버/Drive/Pro/Team 관련 코드는 Android MVP에서 제외한다.**
5. **사진/영상 통합 등록 크래시 방지가 최우선이다.**

---

## 현재 Flutter 자산에서 가져갈 것

### 핵심 서비스/도메인
- `lib/core/db/database.dart`
- `lib/core/db/media_dao.dart`
- `lib/core/db/album_dao.dart`
- `lib/core/db/tag_dao.dart`
- `lib/shared/models/media_item.dart`
- `lib/shared/models/album.dart`
- `lib/shared/models/tag.dart`
- `lib/core/services/media_capture_service.dart`
- `lib/core/services/media_save_service.dart`
- `lib/core/services/storage_service.dart`

### 핵심 화면 흐름
- `lib/features/home/screens/home_screen.dart`
- `lib/features/personal/screens/personal_screen.dart`
- `lib/features/personal/screens/album_detail_screen.dart`
- `lib/features/search/screens/search_screen.dart`
- `lib/shared/screens/media_viewer_screen.dart`
- `lib/shared/screens/media_detail_screen.dart`
- `lib/features/settings/screens/settings_screen.dart`

### 우선 버릴 것
- `lib/core/services/drive_service.dart`
- `lib/core/providers/sync_badge_provider.dart`
- `lib/features/work/`
- `lib/features/import/screens/messenger_import_screen.dart`
- `lib/features/work/screens/report_screen.dart`
- `lib/core/services/report_service.dart`

---

## 단계별 실행 계획

## Phase 0 — 프로젝트 골격 생성

### Task 0.1: Android Native 디렉터리 생성
**Objective:** Flutter와 분리된 새 앱 공간 확보

**Files:**
- Create: `android-native/`
- Create: `android-native/settings.gradle.kts`
- Create: `android-native/build.gradle.kts`
- Create: `android-native/gradle.properties`
- Create: `android-native/app/build.gradle.kts`

**Verification:**
- 디렉터리 구조가 저장소에 생성되어 있어야 함
- 앱 패키지명은 `com.jasonkang.memorix`로 고정

### Task 0.2: 기본 Compose 앱 진입점 생성
**Objective:** Android 앱이 최소한 실행 가능한 구조를 갖춤

**Files:**
- Create: `android-native/app/src/main/AndroidManifest.xml`
- Create: `android-native/app/src/main/java/com/jasonkang/memorix/app/MemorixApplication.kt`
- Create: `android-native/app/src/main/java/com/jasonkang/memorix/MainActivity.kt`
- Create: `android-native/app/src/main/java/com/jasonkang/memorix/app/navigation/MemorixNavHost.kt`

**Verification:**
- Home/Albums/Search/Settings 4탭 placeholder가 렌더링될 수 있어야 함

---

## Phase 1 — 로컬 저장소/DB 이식

### Task 1.1: Room 엔티티 정의
**Objective:** Flutter SQLite 스키마를 Android Room 엔티티로 축소 이식

**Files:**
- Create: `android-native/app/src/main/java/com/jasonkang/memorix/core/database/entity/MediaItemEntity.kt`
- Create: `android-native/app/src/main/java/com/jasonkang/memorix/core/database/entity/AlbumEntity.kt`
- Create: `android-native/app/src/main/java/com/jasonkang/memorix/core/database/entity/TagEntity.kt`
- Create: `android-native/app/src/main/java/com/jasonkang/memorix/core/database/entity/MediaTagCrossRef.kt`

**Notes:**
- `space`, `drive_synced`, `drive_file_id`, `country_code`, `region` 제거
- `isFavorite`, `isArchived`, `isTrashed` 추가

### Task 1.2: Room DAO/Database 작성
**Objective:** 목록/앨범/태그/검색의 최소 CRUD 확보

**Files:**
- Create: `android-native/app/src/main/java/com/jasonkang/memorix/core/database/dao/MediaDao.kt`
- Create: `android-native/app/src/main/java/com/jasonkang/memorix/core/database/dao/AlbumDao.kt`
- Create: `android-native/app/src/main/java/com/jasonkang/memorix/core/database/dao/TagDao.kt`
- Create: `android-native/app/src/main/java/com/jasonkang/memorix/core/database/MemorixDatabase.kt`

**Verification:**
- 미디어 추가, 조회, 휴지통 전환, 앨범 지정 쿼리 가능

### Task 1.3: 검색 인덱스 설계
**Objective:** 텍스트/태그/앨범 검색 기반 확보

**Files:**
- Create: `android-native/app/src/main/java/com/jasonkang/memorix/core/database/entity/MediaSearchEntity.kt`
- Modify: `MemorixDatabase.kt`
- Modify: `MediaDao.kt`

**Notes:**
- Room FTS 테이블 활용
- 초기에는 제목/메모 위주, 태그는 조인 문자열로 저장

---

## Phase 2 — 파일 import 파이프라인

### Task 2.1: 저장 경로 관리자 구현
**Objective:** URI를 내부 저장소 구조로 복사하는 단일 진입점 확보

**Files:**
- Create: `android-native/app/src/main/java/com/jasonkang/memorix/core/media/StorageLayout.kt`
- Create: `android-native/app/src/main/java/com/jasonkang/memorix/core/media/MediaImportManager.kt`

**Requirements:**
- `originals/YYYY/MM/uuid.ext`
- `thumbs/YYYY/MM/uuid.jpg`
- 실패 시 중간 파일 정리

### Task 2.2: 이미지/영상 메타데이터 추출
**Objective:** import 시 캡처일/크기/영상 길이 확보

**Files:**
- Create: `android-native/app/src/main/java/com/jasonkang/memorix/core/media/MediaMetadataReader.kt`

**Verification:**
- 사진과 영상이 섞여 있어도 동일 파이프라인으로 처리 가능

### Task 2.3: 혼합 import 안정화
**Objective:** 현재 TODO의 가장 큰 문제인 사진+영상 동시 등록 크래시 방지

**Files:**
- Create: `android-native/app/src/main/java/com/jasonkang/memorix/worker/ImportMediaWorker.kt`
- Modify: `MediaImportManager.kt`

**Verification:**
- 사진 10장 + 영상 3개 동시 import 시 크래시 없이 완료

---

## Phase 3 — UI MVP

### Task 3.1: 홈/앨범/검색/설정 탭 구현
**Objective:** 앱의 뼈대 완성

**Files:**
- Create: `feature/home/HomeScreen.kt`
- Create: `feature/albums/AlbumsScreen.kt`
- Create: `feature/search/SearchScreen.kt`
- Create: `feature/settings/SettingsScreen.kt`

### Task 3.2: 미디어 그리드와 썸네일 카드 구현
**Objective:** 기본 목록 경험 제공

**Files:**
- Create: `feature/home/component/MediaGrid.kt`
- Create: `feature/home/component/MediaThumbnailCard.kt`

### Task 3.3: 미디어 뷰어 구현
**Objective:** 사진 확대/영상 재생/좌우 탐색 제공

**Files:**
- Create: `feature/viewer/ViewerScreen.kt`
- Create: `feature/viewer/component/PhotoViewer.kt`
- Create: `feature/viewer/component/VideoPlayerPane.kt`

### Task 3.4: 상세 편집 화면 구현
**Objective:** 제목/메모/태그/앨범 이동 제공

**Files:**
- Create: `feature/detail/MediaDetailScreen.kt`

---

## Phase 4 — 앨범/태그/검색 완성

### Task 4.1: 앨범 CRUD
**Objective:** 분류 핵심 확보

**Files:**
- Create: `feature/albums/AlbumDetailScreen.kt`
- Create: `feature/albums/AlbumsViewModel.kt`

### Task 4.2: 태그 시스템 구현
**Objective:** 빠른 분류와 검색 정확도 향상

**Files:**
- Create: `feature/detail/component/TagEditorSheet.kt`
- Create: `feature/settings/TagManagementScreen.kt`

### Task 4.3: 검색 필터 구현
**Objective:** 제목/메모/태그/기간/타입 검색 제공

**Files:**
- Modify: `feature/search/SearchScreen.kt`
- Create: `feature/search/SearchViewModel.kt`

---

## Phase 5 — 보안/휴지통/품질

### Task 5.1: 앱 잠금 구현
**Objective:** 로컬 보관함 신뢰 확보

**Files:**
- Create: `core/security/AppLockManager.kt`
- Create: `feature/auth/LockScreen.kt`

### Task 5.2: 휴지통 구현
**Objective:** 실수 삭제 복구 지원

**Files:**
- Create: `feature/trash/TrashScreen.kt`
- Modify: `MediaDao.kt`

### Task 5.3: 날짜 선택 UI 안정화
**Objective:** 기존 TODO의 캘린더 미표시 문제를 Native에서 방지

**Files:**
- Create: `core/common/DatePickerField.kt`

### Task 5.4: 입력 필드 UX 정리
**Objective:** placeholder 표시 버그 방지

**Files:**
- Shared reusable input composables under `core/common/`

---

## 추천 구현 순서 (실전)

1. 프로젝트 골격
2. Room DB
3. import manager
4. Home grid
5. Viewer
6. Detail edit
7. Albums
8. Search
9. App lock
10. Trash

---

## 이번 주 바로 구현할 범위

### 반드시 끝낼 것
- `android-native/` 골격
- 4탭 Compose 앱
- Room DB 초안
- 내부 저장소 경로 규약
- import manager 인터페이스

### 되면 좋은 것
- media entity/dao 완성
- 홈 그리드 placeholder
- 앨범 entity/dao

### 다음 주로 넘길 것
- 실제 import UI
- viewer
- search FTS
- biometrics

---

## 산출물 정의

이번 차수 종료 시 아래가 있어야 한다.
- Android Native 앱 디렉터리
- Compose 기반 기본 앱 실행 구조
- 로컬 DB 스키마 초안
- import/domain 구조 초안
- Flutter → Android 마이그레이션 작업 목록 문서

---

## 리스크와 대응

### 리스크 1: Flutter 자산이 많아서 미련이 생김
대응: Flutter는 참고용만. 코드 이식 집착 금지.

### 리스크 2: import 단계에서 다시 크래시
대응: UI보다 먼저 `MediaImportManager`와 worker 설계부터 테스트.

### 리스크 3: 기능 범위가 다시 커짐
대응: Drive/Pro/Work 리포트/이중 공간 재도입 금지.

---

## 최종 메모

Memorix 전환의 목표는 “Flutter를 Native로 옮기는 것” 자체가 아니다.
**Android에서 안정적인 로컬 사진 보관 루프를 재구축하는 것**이 목표다.
