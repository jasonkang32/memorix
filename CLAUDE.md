# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

**Memorix** — 개인 미디어 보관 Flutter 앱. "기억은 빠르게, 보관은 조용하게."  
업무(Work)와 개인(Personal) 미디어를 완전히 분리하여 보관·검색·보고서 생성까지 처리하는 온디바이스 중심 앱.

## 빌드 및 실행 명령

```bash
# 통합 빌드 (기본)
flutter run --dart-define=APP_FLAVOR=memorix

# Work 전용 빌드
flutter run --dart-define=APP_FLAVOR=memorix_work

# Personal 전용 빌드
flutter run --dart-define=APP_FLAVOR=memorix_personal

# 테스트
flutter test

# 단일 테스트 실행
flutter test test/path/to/test_file.dart

# 정적 분석
flutter analyze
```

## 기술 스택

- **플랫폼**: Flutter (iOS + Android)
- **상태관리**: Riverpod
- **로컬 DB**: sqflite (SQLite)
- **Flavor 관리**: flutter_flavorizr (`--dart-define=APP_FLAVOR`)

주요 패키지:

| 역할 | 패키지 |
|------|--------|
| 카메라 촬영 | `camera` |
| 갤러리·문서 가져오기 | `image_picker`, `file_picker` |
| 썸네일·압축 | `flutter_image_compress`, `video_thumbnail` |
| AI 자동 태깅 | `google_ml_kit` (ImageLabeler) |
| PDF 보고서 생성 | `pdf` (온디바이스) |
| Google Drive 연동 | `google_sign_in`, `googleapis` |
| 보안 저장소 | `flutter_secure_storage` |
| 생체인증 | `local_auth` |

## 아키텍처

### 두 공간 분리 원칙
모든 데이터는 `space` 필드(`'work'` | `'personal'`)로 분리. DB 쿼리, 파일 경로, Drive 동기화 경로 모두 space 기준으로 격리.

### 데이터 모델 (SQLite)
- **media**: 사진·영상·문서 통합 저장. Work 필드(`country_code`, `region`)와 Personal 필드(`album_id`)가 동일 테이블에 공존
- **albums**: Personal Space의 이벤트 앨범 (travel, ceremony, gathering 등)
- **tags / media_tags**: Work·Personal 공용 태그 시스템
- **people / media_people**: Personal Space 인물 태깅
- **media_fts (FTS5)**: `title`, `note` 전문 검색용 가상 테이블

### 파일 저장소
갤러리 저장 절대 금지 — 모든 파일은 `AppDocumentsDirectory/memorix/` 내부에만 저장.
```
memorix/
  photos/{year}/{month}/{uuid}.jpg         # 원본
  photos/{year}/{month}/{uuid}_thumb.jpg   # 썸네일 300x200
  videos/{year}/{month}/{uuid}.mp4
  documents/{year}/{month}/{uuid}.pdf
  reports/{uuid}_report.pdf
  db/memorix.db
```

### Flavor 시스템
`APP_FLAVOR` 환경변수로 빌드 타입 결정. `main.dart`에서 `String.fromEnvironment('APP_FLAVOR', defaultValue: 'memorix')`로 읽음. 코드베이스는 공유, UI·기능 노출만 Flavor에 따라 분기.

### 미디어 가져오기 흐름
1. `image_picker` / `file_picker` / `camera`로 파일 취득
2. 내부 저장소에 복사 (`uuid` 기반 파일명)
3. `flutter_image_compress`로 썸네일 생성
4. `google_ml_kit` ImageLabeler → `TagMappingService.map()` → 태그 추천
5. 원본 삭제 여부 팝업 (기본값: 삭제)

### 검색
복합 조건 쿼리: `media_fts` MATCH + JOIN `media_tags` + 공간/날짜/위치 필터를 조합. Work는 국가·지역 필터, Personal은 앨범·인물 필터가 핵심.

### PDF 보고서
Work 4종 (출장보고서·현장분위기·장애현상·사진대지), Personal은 v2.0 예정. 서버 없이 `pdf` 패키지로 온디바이스 생성 후 `Share.shareXFiles()` 또는 Drive 업로드.

### Google Drive (Pro 기능)
- OAuth 스코프: `drive.file` (앱 생성 파일만)
- 토큰: `flutter_secure_storage` (iOS Keychain / Android Keystore)
- 오프라인 촬영분은 `drive_synced=0`으로 표시 → 네트워크 복구 시 자동 재시도

### 보안
- 앱 잠금: `local_auth` (Face ID·지문·PIN 폴백), 백그라운드 30초 후 재인증
- Personal Space 별도 잠금: Pro 옵션
- Android 스크린샷 방지: `FLAG_SECURE`

### 네트워크 복구 자동 동기화
`ConnectivityService` (connectivity_plus)가 오프라인→온라인 전환을 감지하면 `DriveService.syncPending()`을 자동 호출. `main.dart`에서 앱 수명과 같이 구독 유지.

### 상태관리 패턴
- `FutureProvider` / `FutureProvider.family`: DB 조회 (workMediaProvider, searchResultProvider 등)
- `StateNotifierProvider`: 잠금 상태 (lockProvider, personalLockProvider)
- `StateProvider`: 단순 필터 상태 (workFilterProvider, searchSpaceProvider 등)
- Provider 갱신: `ref.invalidate(provider)` — 저장·삭제·편집 후 호출

### UI 구조
- `MediaGrid`: `SliverGrid` + 월별 섹션 헤더 (DateGrouper 유틸)
- `MediaThumbnailCard`: Stack 레이아웃. 영상은 play 오버레이, 미동기화 아이템은 cloud_upload 오버레이(우하단)
- `CaptureBottomSheet`: 카메라·갤러리·문서 선택 공통 바텀시트
- `MediaViewerScreen`: photo_view + video_player/chewie 풀스크린 뷰어, 인앱 삭제 지원

## 구현 현황 (Stage 11 완료)

| 기능 | 상태 |
|------|------|
| Work/Personal 미디어 그리드 (월별 섹션) | ✅ |
| 미디어 캡처·가져오기·AI 태그 추천 | ✅ |
| 미디어 상세 편집 (제목·메모·태그·인물·위치) | ✅ |
| 풀스크린 뷰어 (사진·영상) | ✅ |
| Personal 앨범 관리 | ✅ |
| 인물 태깅 | ✅ |
| 검색 (FTS5 + 공간/날짜/태그/앨범/인물 필터) | ✅ |
| Work PDF 보고서 4종 | ✅ |
| Google Drive 동기화 (오프라인 자동 재시도) | ✅ |
| 앱 잠금 + Personal 별도 잠금 | ✅ |
| 설정 (스토리지 시각화·태그관리·인물관리·Drive) | ✅ |
| 미동기화 배지 (설정탭 + 썸네일 오버레이) | ✅ |
| 온보딩 | ✅ |
| 인앱 결제 (Pro 플랜) | ⏳ 최후순위 |

## 플랜 구조

| Tier | 제한 |
|------|------|
| Free | 저장 공간 제한, Drive 연동 없음, AI 태깅 제한 |
| Pro | Drive 연동, AI 자동 태깅, PDF 보고서, Personal 별도 잠금 |
| Team | Pro + 팀원 공유 |

인앱 결제 영수증 검증: AWS Lambda (서버리스). 오프라인 시 마지막 검증 후 7일간 Pro 유지.
