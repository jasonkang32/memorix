import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// 잠금 토글 사용자 진입점(3개) 회귀 가드 — Phase 3C.
///
/// 배경 (spec 섹션 5, QA 라운드 1 갱신):
/// per-item lock 기능의 사용자 진입점:
///  1) Detail 액션: AppBar 잠금 토글 아이콘
///  2) Long-press 컨텍스트 메뉴: 그리드/타임라인 셀 길게 누르기
///  3) 카드 자물쇠 버튼: 타임라인 카드 헤더의 IconButton
///     (이전: 미디어 추가 직후 "잠그시겠어요?" 다이얼로그 → QA 라운드 1에서
///      사용자 보고로 다이얼로그 제거 + per-card 자물쇠 버튼으로 대체)
///
/// 가드 원칙 — source-grep:
///  - 공통 helper(`handleLockToggle`)와 `MediaLongPressMenu`가 호출처에서
///    실제 사용되는지를 토큰 단위로 검증.
///  - 회귀 시 빌드/분석 단계가 아닌, 이 테스트가 명확히 어디서 진입점이
///    빠졌는지 알려준다.
void main() {
  late String detailSource;
  late String workSource;
  late String personalSource;
  late String helperSource;
  late String menuSource;

  setUpAll(() {
    detailSource = File(
      'lib/shared/screens/media_detail_screen.dart',
    ).readAsStringSync();
    workSource = File(
      'lib/features/work/screens/work_screen.dart',
    ).readAsStringSync();
    personalSource = File(
      'lib/features/personal/screens/personal_screen.dart',
    ).readAsStringSync();
    helperSource = File(
      'lib/features/auth/services/lock_toggle_helper.dart',
    ).readAsStringSync();
    menuSource = File(
      'lib/shared/widgets/media_long_press_menu.dart',
    ).readAsStringSync();
  });

  group('공통 helper 파일 존재 + 시그니처', () {
    test('lock_toggle_helper.dart에 handleLockToggle 함수가 있다', () {
      expect(
        helperSource.contains('Future<bool> handleLockToggle('),
        isTrue,
        reason:
            'handleLockToggle 시그니처가 사라졌다. 3개 진입점이 인증/토글 '
            '흐름을 공유할 수 없다 (spec 5 위반).',
      );
    });

    test('handleLockToggle이 lockAuthServiceProvider를 사용한다', () {
      expect(
        helperSource.contains('lockAuthServiceProvider'),
        isTrue,
        reason:
            'helper에 lockAuthServiceProvider 참조가 없다. 인증 단계가 빠졌다.',
      );
    });

    test('handleLockToggle이 lockToggleServiceProvider를 사용한다', () {
      expect(
        helperSource.contains('lockToggleServiceProvider'),
        isTrue,
        reason:
            'helper에 lockToggleServiceProvider 참조가 없다. 평문 ↔ .enc 변환 '
            '흐름이 빠졌다.',
      );
    });

    // QA 라운드 1: offerLockAfterAdd 다이얼로그는 사용자가 거부 → 카드 자물쇠
    // 버튼으로 진입점 3을 옮겼다. helper 함수 자체는 향후 다른 use case를 위해
    // 남아있을 수 있지만, 호출처(work_screen / personal_screen)에서는 호출 0건.
    // → 이 그룹의 helper-존재 가드는 더 이상 강제하지 않는다.

    test('media_long_press_menu.dart에 MediaLongPressMenu 클래스가 있다', () {
      expect(
        menuSource.contains('class MediaLongPressMenu'),
        isTrue,
        reason:
            'MediaLongPressMenu 클래스가 사라졌다. long-press 컨텍스트 메뉴 '
            '진입점(2)이 빠진다.',
      );
    });

    test('MediaLongPressMenu가 handleLockToggle을 호출한다', () {
      expect(
        menuSource.contains('handleLockToggle'),
        isTrue,
        reason:
            'long-press 메뉴에서 handleLockToggle 호출이 빠졌다. 메뉴 항목이 '
            '사용자 의도와 맞지 않는 동작을 한다.',
      );
    });

    test('MediaLongPressMenu에 잠금 토글 항목이 노출된다 (Icons.lock 토큰)', () {
      expect(
        menuSource.contains('Icons.lock_open') &&
            menuSource.contains('Icons.lock'),
        isTrue,
        reason:
            'long-press 메뉴에 잠금/해제 아이콘 토큰이 빠졌다. UI 노출 회귀.',
      );
    });
  });

  group('진입점 1 — MediaDetailScreen AppBar 잠금 토글', () {
    test('media_detail_screen이 lock_toggle_helper를 import한다', () {
      expect(
        detailSource.contains('lock_toggle_helper.dart'),
        isTrue,
        reason:
            'media_detail_screen이 helper를 import하지 않는다. AppBar 잠금 '
            '토글 진입점(1)이 동작할 수 없다 (spec 5 위반).',
      );
    });

    test('detail 화면에서 handleLockToggle을 호출한다', () {
      expect(
        detailSource.contains('handleLockToggle('),
        isTrue,
        reason:
            'media_detail_screen에 handleLockToggle 호출이 없다. AppBar 액션 '
            '진입점(1)이 빠졌다.',
      );
    });

    test('detail AppBar에 lock 아이콘이 노출된다', () {
      // Icons.lock_open 또는 Icons.lock_outline 둘 중 하나는 있어야 한다.
      expect(
        detailSource.contains('Icons.lock_open') ||
            detailSource.contains('Icons.lock_outline'),
        isTrue,
        reason:
            'media_detail_screen AppBar에 잠금 토글 아이콘이 없다. 진입점(1) '
            'UI 노출 회귀.',
      );
    });

    test('detail에서 토글 후 workMediaProvider/secretMediaProvider 갱신', () {
      expect(
        detailSource.contains('ref.invalidate(workMediaProvider)') &&
            detailSource.contains('ref.invalidate(secretMediaProvider)'),
        isTrue,
        reason:
            'detail의 잠금 토글 후 그리드 invalidate가 누락. 화면이 stale '
            '상태로 남는다.',
      );
    });
  });

  group('진입점 2 — Long-press 컨텍스트 메뉴', () {
    test('work_screen이 MediaLongPressMenu를 import + 호출한다', () {
      expect(
        workSource.contains('media_long_press_menu.dart') &&
            workSource.contains('MediaLongPressMenu.show'),
        isTrue,
        reason:
            'work_screen에 MediaLongPressMenu 호출이 없다. 그리드 long-press '
            '진입점(2)이 viewer 즉시 열기로 회귀했다.',
      );
    });

    test('personal_screen이 MediaLongPressMenu를 import + 호출한다', () {
      expect(
        personalSource.contains('media_long_press_menu.dart') &&
            personalSource.contains('MediaLongPressMenu.show'),
        isTrue,
        reason:
            'personal_screen에 MediaLongPressMenu 호출이 없다. 타임라인 '
            'long-press 진입점(2)이 viewer 즉시 열기로 회귀했다.',
      );
    });

    test('work long-press onAfterToggle에서 workMediaProvider 갱신', () {
      expect(
        workSource.contains('ref.invalidate(workMediaProvider)'),
        isTrue,
        reason:
            'work_screen에 workMediaProvider invalidate가 없다. 잠금 토글 후 '
            '그리드가 stale 상태.',
      );
    });

    test('personal long-press onAfterToggle에서 secretMediaProvider 갱신', () {
      expect(
        personalSource.contains('ref.invalidate(secretMediaProvider)'),
        isTrue,
        reason:
            'personal_screen에 secretMediaProvider invalidate가 없다. 잠금 '
            '토글 후 타임라인이 stale 상태.',
      );
    });
  });

  group('진입점 3 — 타임라인 카드 자물쇠 버튼', () {
    // QA 라운드 1: "잠그시겠어요?" 다이얼로그를 사용자가 거부 → MediaTimeline
    // 카드 헤더의 자물쇠 IconButton으로 대체. 호출처는 onLockToggle 콜백을
    // 통해 handleLockToggle을 호출.

    test('work_screen _onAddMedia에 offerLockAfterAdd 호출이 없다 (회귀 방지)', () {
      expect(
        workSource.contains('offerLockAfterAdd('),
        isFalse,
        reason:
            'work_screen에 offerLockAfterAdd 호출이 다시 들어왔다. '
            'QA 라운드 1: "잠금?" 다이얼로그는 제거되어야 한다.',
      );
    });

    test('personal_screen _onAddMedia에 offerLockAfterAdd 호출이 없다 (회귀 방지)', () {
      expect(
        personalSource.contains('offerLockAfterAdd('),
        isFalse,
        reason:
            'personal_screen에 offerLockAfterAdd 호출이 다시 들어왔다. '
            'QA 라운드 1: "잠금?" 다이얼로그는 제거되어야 한다.',
      );
    });

    test('work_screen이 MediaTimeline.onLockToggle 콜백을 전달한다', () {
      expect(
        workSource.contains('onLockToggle'),
        isTrue,
        reason:
            'work_screen이 MediaTimeline에 onLockToggle을 전달하지 않는다. '
            '카드 자물쇠 버튼이 동작 안 함 (진입점 3 회귀).',
      );
    });

    test('personal_screen이 MediaTimeline.onLockToggle 콜백을 전달한다', () {
      expect(
        personalSource.contains('onLockToggle'),
        isTrue,
        reason:
            'personal_screen이 MediaTimeline에 onLockToggle을 전달하지 않는다. '
            '카드 자물쇠 버튼이 동작 안 함 (진입점 3 회귀).',
      );
    });

    test('work_screen이 handleLockToggle을 호출한다 (onLockToggle 콜백 안에서)', () {
      expect(
        workSource.contains('handleLockToggle'),
        isTrue,
        reason:
            'work_screen에 handleLockToggle 호출이 없다. 카드 자물쇠 버튼이 '
            '인증/토글 흐름을 트리거하지 못함.',
      );
    });

    test('personal_screen이 handleLockToggle을 호출한다 (onLockToggle 콜백 안에서)', () {
      expect(
        personalSource.contains('handleLockToggle'),
        isTrue,
        reason:
            'personal_screen에 handleLockToggle 호출이 없다. 카드 자물쇠 버튼이 '
            '인증/토글 흐름을 트리거하지 못함.',
      );
    });

    test('work_screen이 lock_toggle_helper를 import한다', () {
      expect(
        workSource.contains('lock_toggle_helper.dart'),
        isTrue,
        reason:
            'work_screen이 helper를 import하지 않는다. handleLockToggle 호출이 '
            '컴파일 안 됨.',
      );
    });

    test('personal_screen이 lock_toggle_helper를 import한다', () {
      expect(
        personalSource.contains('lock_toggle_helper.dart'),
        isTrue,
        reason:
            'personal_screen이 helper를 import하지 않는다. handleLockToggle '
            '호출이 컴파일 안 됨.',
      );
    });
  });
}
