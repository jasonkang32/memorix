# Personal 이름 변경 + 항목별 잠금 설계

> 작성일: 2026-05-04
> 상태: Approved (사용자 승인 완료, 구현 계획 단계로 진입 대기)

## 1. 배경 / 동기

현재 Memorix는 두 공간으로 나뉜다 — **Work**와 **Secret**. Secret 공간 전체가 PIN/생체로 잠겨 있다. 데이터 모델/UI는 다음 미스매치를 안고 있다:

- `MediaSpace.personal`이 v1.x legacy로 남아 있고, 새 row는 모두 `secret`으로 저장됨 (이미 한 번 마이그레이션 거침).
- UI 라벨은 'Secret', 코드 식별자는 `personal_screen.dart` — 라벨/코드 정합성 X.
- 잠금이 **공간 단위**라 사용자가 "이 사진 한 장만 잠그고 싶다"는 자연스러운 요구를 표현 못 함.

사용자 요청 — **두 공간을 `work` + `personal`로 재명명**하고, **잠금은 공간 단위가 아니라 미디어 항목별 옵션**으로 변경. 어느 공간이든 잠긴 항목은 secret처럼 PIN/생체 인증을 통과해야 열림.

## 2. 핵심 결정 (사용자 승인 완료)

| # | 항목 | 결정 |
|---|------|------|
| 1 | 마이그레이션 | 자동 이동 + 잠금 보존. 옛 `secret`/legacy `personal` row → `personal` + `is_locked=1`. `.enc` 파일은 그대로 유지 (잠금 해제 시점에만 복호화). |
| 2 | 인증 정책 | 5분 세션. 한 번 인증 후 5분간 모든 잠긴 항목 자유 접근. 앱 백그라운드 진입 시 즉시 invalidate. |
| 3 | 그리드 표시 | 블러 처리된 썸네일 + 자물쇠 아이콘. `BackdropFilter(blur 20)` + 중앙 lock 아이콘. |
| 4 | 검색 정책 | 그리드와 동일 — 검색 결과에 잠긴 항목 포함하되 블러. OCR/note 매칭됨. |
| 5 | 인증 방법 | PIN + 생체 (생체 우선, PIN 폴백). 첫 잠금 사용 시 PIN 설정 강제. |
| 6 | 잠금 범위 | 미디어 항목별만. 앨범/job 단위 잠금 없음. |
| 7 | 파일 보호 | `.enc` 암호화 (현 secret과 동일 메커니즘). `SecretVaultService` 재사용. |
| 8 | Pro 제한 | 잠금 기능은 Free에 개방. Pro는 Drive/AI 태그/PDF 보고서로 차별. |
| 9 | 잠금 진입점 | ① 미디어 추가 시 옵션 토글 + ② detail 화면 액션 + ③ 그리드 long-press 메뉴. |

## 3. 데이터 모델 + DB 마이그레이션

### MediaSpace enum

```dart
// Before
enum MediaSpace { work, secret, personal }

// After
enum MediaSpace { work, personal }

extension MediaSpaceX on MediaSpace {
  String get dbValue => switch (this) {
    MediaSpace.work => 'work',
    MediaSpace.personal => 'personal',
  };

  // 모든 legacy 값('secret', 옛 'personal')을 personal로 흡수
  static MediaSpace parse(String? raw) => switch (raw) {
    'work' => MediaSpace.work,
    'secret' || 'personal' => MediaSpace.personal,
    _ => MediaSpace.work,
  };
}
```

### MediaItem 필드 추가

```dart
class MediaItem {
  // ... 기존 필드 그대로
  final int isLocked;  // 0 또는 1. encrypted와 별개.
}
```

`encrypted`(파일 형식 `.enc` 여부)와 `isLocked`(잠금 상태)를 분리한다. 일반적으로 둘은 동기화되지만(잠긴 항목 = `.enc`), 의미 분리가 안전하다.

### DB 스키마 v6 → v7

```sql
-- v7 up migration
ALTER TABLE media ADD COLUMN is_locked INTEGER NOT NULL DEFAULT 0;

-- 기존 secret + legacy personal → personal + 잠금 보존
UPDATE media SET space = 'personal', is_locked = 1
  WHERE space IN ('secret', 'personal');
```

### 안전망

- 마이그레이션 직전 `<docs>/memorix/db/memorix.db` → `memorix.db.bak.v7`로 복사.
- 마이그레이션 실패 시 백업 복원 + 사용자 토스트.
- `schema_version` 가드로 1회만 실행.

### FTS5 인덱스

`is_locked` 컬럼은 FTS에 추가하지 않는다. 검색 매칭 자체는 그대로, UI 단계에서 블러 처리.

### 파일 시스템

- 경로 `<docs>/memorix/photos/...` 그대로.
- `.enc` 파일은 변환 없이 유지 (Bug #5 가드와 호환 — `OriginalMediaCleanupService._isInVault`가 보호).

## 4. 인증 + 세션 시스템

### LockSessionManager (신규, 싱글톤)

```dart
class LockSessionManager {
  DateTime? _unlockedAt;
  static const _sessionDuration = Duration(minutes: 5);

  bool get isUnlocked =>
      _unlockedAt != null &&
      DateTime.now().difference(_unlockedAt!) < _sessionDuration;

  void markUnlocked() => _unlockedAt = DateTime.now();
  void invalidate() => _unlockedAt = null;
}

final lockSessionProvider = Provider<LockSessionManager>(
  (ref) => LockSessionManager(),
);
```

- `AppLifecycleObserver`(이미 main.dart에 존재)에서 `paused`/`detached` 시 `invalidate()` 호출.
- Riverpod provider로 노출 — 위젯에서 `ref.watch`로 상태 변화 추적.

### LockAuthService (기존 lock provider들 통합)

```dart
class LockAuthService {
  final LockSessionManager _session;

  /// 생체 우선, 폴백 PIN. 성공 시 세션 unlock.
  Future<bool> authenticate(BuildContext context) async {
    if (_session.isUnlocked) return true;
    if (await _hasBiometric()) {
      if (await _authBiometric()) {
        _session.markUnlocked();
        return true;
      }
    }
    final pinOk = await _promptPin(context);
    if (pinOk) _session.markUnlocked();
    return pinOk;
  }

  Future<bool> _hasBiometric() async { /* local_auth */ }
  Future<bool> _authBiometric() async { /* local_auth */ }
  Future<bool> _promptPin(BuildContext context) async { /* PIN dialog */ }
}
```

- 기존 `local_auth` + `flutter_secure_storage`(PIN 해시 저장) 재사용.
- 기존 `pin_setup_screen.dart` 재사용 (PIN 미설정 시 자동 진입).
- 기존 `secret_lock_provider.dart`, `personal_lock_provider.dart` 제거 또는 `LockAuthService`로 흡수.

## 5. 잠금 토글 UX (3개 진입점)

세 진입점 모두 같은 `LockToggleService.lock(item)` / `.unlock(item)` 호출.

| 진입점 | 위치 | UX |
|-------|-----|----|
| 추가 시점 | `CaptureBottomSheet` 또는 저장 직후 다이얼로그 | "이 항목을 잠금?" 토글. default OFF. ON이면 저장 후 즉시 `.enc` 변환. |
| Detail 액션 | `MediaDetailScreen` 우상단 메뉴 | "🔒 잠금 / 🔓 해제" 토글. 인증 후 `.enc` ↔ `.jpg` 변환. |
| 그리드 long-press | `MediaGrid`/`MediaThumbnailCard` long-press | 컨텍스트 메뉴 "🔒 잠금 / 🔓 해제 / 🗑 삭제". |

### 공통 흐름

```
사용자 토글 → 세션 unlocked? → No → 인증 (PIN/생체) → 성공 →
  LockToggleService.lock/unlock → progress 다이얼로그 →
    .enc ↔ .jpg 변환 (SecretVaultService) →
    DB 업데이트 (is_locked, encrypted, file_path, thumb_path) →
    provider invalidate → UI 갱신
```

## 6. 표시 + 검색

### 그리드/timeline 위젯 변경

`MediaThumbnailCard`, `_MediaImage` (Work=MediaGrid, Personal=MediaTimeline) 두 위젯에 `is_locked` 분기:

```dart
Stack(children: [
  Image.file(File(path),
    fit: BoxFit.cover,
    cacheWidth: 600,
    cacheHeight: 600,
    filterQuality: FilterQuality.medium,
  ),
  if (item.isLocked == 1) ...[
    BackdropFilter(
      filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
      child: Container(color: Colors.black26),
    ),
    const Center(
      child: Icon(Icons.lock_rounded, size: 36, color: Colors.white),
    ),
  ],
])
```

- 비디오는 thumbPath 분기 후 같은 패턴.
- `EncryptedImage`(`.enc` 복호화 위젯)는 풀스크린 진입 후에만 사용. 그리드에서는 .enc면 같은 블러 placeholder.

### 검색 결과

`MediaDao.quickSearch`/FTS는 잠긴 항목 포함 그대로 반환. 검색 결과 화면도 같은 썸네일 위젯 사용 → **자동 블러 일관성**. 별도 코드 X.

### 풀스크린 진입 시 인증 게이트

`MediaViewerScreen`/`MediaDetailScreen` 진입 시:

```
탭 → LockSessionManager.isUnlocked?
       → Yes: 직접 진입
       → No: LockAuthService.authenticate() → 성공 시 진입
```

스와이프 뷰어에서 잠긴 항목 인덱스로 스와이프 시 동일 게이트. 세션 unlocked면 자유 스와이프.

## 7. 파일 보호 흐름 (.enc 변환)

### LockToggleService (신규)

```dart
class LockToggleService {
  final MediaDao _dao;

  /// 평문 → .enc로 잠그기. 실패 시 평문 보존 (atomic).
  Future<MediaItem> lock(MediaItem item) async {
    if (item.isLocked == 1) return item;

    final encPath = await SecretVaultService.savePhoto(item.filePath);
    String? encThumb;
    if (item.thumbPath != null) {
      encThumb = await SecretVaultService.saveVideoThumb(item.thumbPath!);
    }

    // 변환 성공 후에만 평문 삭제
    try {
      await File(item.filePath).delete();
      if (item.thumbPath != null) await File(item.thumbPath!).delete();
    } catch (_) { /* best-effort */ }

    final updated = item.copyWith(
      filePath: encPath,
      thumbPath: encThumb,
      isLocked: 1,
      encrypted: 1,
    );
    await _dao.update(updated);
    return updated;
  }

  /// .enc → 평문 복원. 실패 시 .enc 보존 (atomic).
  Future<MediaItem> unlock(MediaItem item) async {
    if (item.isLocked == 0) return item;

    final plainPath = await SecretVaultService.decryptToFile(item.filePath);
    String? plainThumb;
    if (item.thumbPath != null) {
      plainThumb = await SecretVaultService.decryptToFile(item.thumbPath!);
    }

    try {
      await File(item.filePath).delete();
      if (item.thumbPath != null) await File(item.thumbPath!).delete();
    } catch (_) { /* best-effort */ }

    final updated = item.copyWith(
      filePath: plainPath,
      thumbPath: plainThumb,
      isLocked: 0,
      encrypted: 0,
    );
    await _dao.update(updated);
    return updated;
  }
}
```

- `SecretVaultService.decryptToFile`는 신규 메서드 — 기존 코드는 메모리 복호화만 있음. 추가 필요.
- 토글 시 progress 다이얼로그 (사진 ~1초, 비디오 ~수 초).
- Bug #5 가드와 호환: `.enc`도 보관함 path → `OriginalMediaCleanupService._isInVault` 보호.

## 8. 영향 범위 (잠긴 항목의 기존 기능 처리)

### Drive 동기화 (Pro)

- **잠긴 항목 동기화 제외 (default)**. `findPendingSync` 쿼리에 `is_locked = 0` 추가.
- 후속에서 사용자 명시 요구 시 "잠긴 항목도 동기화" 옵션.

### PDF 보고서 (Work 4종)

- **잠긴 항목 PDF 제외 (default)**. 보고서 SQL에 `is_locked = 0`.
- 미리보기에 "잠긴 N개 항목 제외됨" 안내.
- 후속에서 "잠긴 항목 포함" 사용자 토글.

### HomeSummary 통계

- `workCount`/`personalCount`: 모두 포함 (잠금 무관).
- `recentItems`: 포함하되 그리드 동일 블러 (UI 자동 처리).
- `topTags`/`storageBreakdown`: 포함 (메타·사이즈 노출 OK).

### FTS5 인덱스

- `media_fts` 그대로. `title`/`note`/`ocr_text` 인덱스 유지. UI 단계 블러.

## 9. 테스트 전략

| 종류 | 파일 | 검증 |
|-----|-----|------|
| 마이그레이션 단위 | `test/unit/migration_v7_test.dart` | v6 → v7 시 `secret`/legacy `personal` → `personal` + `is_locked=1`. 백업 파일 생성. |
| LockSessionManager 단위 | `test/unit/lock_session_test.dart` | 5분 만료, `invalidate()` 동작, 백그라운드 진입 시 invalidate. |
| LockToggleService 단위 | `test/unit/lock_toggle_test.dart` | `.enc` 변환 + DB 갱신. 변환 실패 시 평문 보존. unlock 시 `.enc` 보존. |
| Architecture guard | `test/unit/per_item_lock_guard_test.dart` | `MediaSpace.secret` 참조 부재. Drive/PDF 쿼리에 `is_locked = 0` 분기. |
| Widget | `test/widget/locked_thumbnail_test.dart` | `is_locked=1`이면 `BackdropFilter` + lock 아이콘 렌더. |
| 기존 회귀 가드 (Bug #1-5) | 기존 5개 그대로 | 모두 GREEN 유지. |

## 10. 구현 순서 (제안)

1. **데이터 모델 + 마이그레이션 v7** (가장 먼저, 다른 모든 변경의 기반)
2. **LockSessionManager + LockAuthService** (인증 인프라)
3. **LockToggleService + SecretVaultService.decryptToFile** (파일 변환)
4. **그리드/timeline 블러 + lock 아이콘 표시** (UI)
5. **풀스크린 진입 시 인증 게이트** (라우터 또는 위젯 가드)
6. **3개 진입점 UX** (capture/detail/long-press)
7. **Drive/PDF/HomeSummary 영향 범위 처리**
8. **테스트 작성** (각 단계 동시 또는 마지막에 일괄)
9. **버전 bump + 빌드 + 배포**

## 11. 미정 / 후속 결정

- 잠긴 항목의 Drive 동기화 (default 제외, 사용자 요구 시 옵션 추가)
- 잠긴 항목의 PDF 포함 옵션
- "잠긴 항목 검색에서 제외" 사용자 토글 (현재는 default 포함 + 블러)
- PIN 분실 시 복구 흐름 (잠긴 데이터 영구 손실 vs 마스터 키)
- 다중 디바이스 vault 키 공유 (Drive 동기화 활성화 시 필요)

## 12. 보안 모델 명시

| 위협 | 방어 |
|-----|------|
| 디바이스 분실 + 화면 잠금 우회 | `.enc` 파일 (vault 키 없으면 복원 불가) |
| 디바이스 루팅 + 파일 시스템 직접 접근 | `.enc` (동일) |
| 어깨너머 (사용자가 잠긴 항목 둘러볼 때) | 블러 + 5분 세션 만료 |
| 앱 멀티태스킹 화면 | 백그라운드 진입 시 세션 invalidate |
| PIN 무차별 입력 | (현재) 시도 횟수 제한 없음. 후속 결정. |

## 13. 마이그레이션 사용자 영향

- **기존 secret 사용자**: 첫 v7 실행 시 화면이 자동으로 "Personal" 라벨로 바뀜. 모든 secret 사진은 Personal에 잠긴 상태로 보임. 별도 행동 불필요.
- **기존 personal_lock_provider 사용자**: 기존 PIN/생체 설정은 유지 (`flutter_secure_storage`의 PIN 해시 그대로 재사용).
- **새 사용자**: 잠금 첫 사용 시 PIN 설정 안내.
