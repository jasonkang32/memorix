import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// 잠긴 항목(`MediaItem.isLocked == 1`) 풀스크린 진입 인증 게이트 회귀 가드.
///
/// 배경 (spec 섹션 6 (c)):
/// - 그리드 셀에서 잠긴 항목은 블러 처리되지만, 셀 탭 시 풀스크린
///   (`MediaDetailScreen`/`MediaViewerScreen`)으로 진입할 때는 인증 게이트가
///   필수다. 5분 unlock 세션이 살아 있으면 자유 통과.
/// - 게이트는 `lockAuthServiceProvider.authenticate(context)` 호출 → 실패 시
///   화면 종료(pop).
///
/// 가드 원칙 — source-grep:
/// - `media_detail_screen.dart`: lock_session_provider import + isLocked 분기 +
///   LockAuthService 토큰 + Navigator.of(context).pop() 종료 흐름.
/// - `media_viewer_screen.dart`: 동일 토큰 셋.
/// - 위젯 렌더링/상호작용 검증은 별도 widget 테스트로 분리. 여기서는 회귀가
///   발생하면 빌드 단계에서 즉시 잡히도록 토큰 존재만 확인한다.
void main() {
  late String detailSource;
  late String viewerSource;

  setUpAll(() {
    detailSource = File(
      'lib/shared/screens/media_detail_screen.dart',
    ).readAsStringSync();
    viewerSource = File(
      'lib/shared/screens/media_viewer_screen.dart',
    ).readAsStringSync();
  });

  group('media_detail_screen — per-item lock fullscreen gate', () {
    test('lock_session_provider import가 있다', () {
      expect(
        detailSource.contains('lock_session_provider.dart'),
        isTrue,
        reason:
            'media_detail_screen이 lock_session_provider를 import하지 않는다. '
            '인증 게이트 자체가 없다 (spec 6 (c) 위반).',
      );
    });

    test('LockAuthService 토큰이 본문에 있다', () {
      expect(
        detailSource.contains('LockAuthService'),
        isTrue,
        reason:
            'media_detail_screen에 LockAuthService 참조가 없다. 인증 호출이 '
            '누락되어 잠긴 항목을 그대로 노출한다 (spec 6 (c) 위반).',
      );
    });

    test('lockAuthServiceProvider 호출이 있다', () {
      expect(
        detailSource.contains('lockAuthServiceProvider'),
        isTrue,
        reason:
            'media_detail_screen에 lockAuthServiceProvider 호출이 없다. '
            '인증 다이얼로그를 띄우지 못한다 (spec 6 (c) 위반).',
      );
    });

    test('lockSessionProvider 호출이 있다 (5분 세션 통과 경로)', () {
      expect(
        detailSource.contains('lockSessionProvider'),
        isTrue,
        reason:
            'media_detail_screen이 lockSessionProvider를 확인하지 않는다. '
            '5분 unlock 세션이 무시되어 매번 인증을 띄운다 (UX 회귀).',
      );
    });

    test('isLocked 분기가 본문에 있다', () {
      expect(
        detailSource.contains('isLocked'),
        isTrue,
        reason:
            'media_detail_screen에 isLocked 분기가 없다. 모든 항목에 대해 '
            '게이트가 동일하게 동작하거나(과다 호출) 항상 통과한다(보안 회귀).',
      );
    });

    test('인증 실패 시 Navigator.pop으로 화면을 종료한다', () {
      // pop 호출 + lockAuthServiceProvider가 같은 파일에 함께 있으면
      // 인증 실패 종료 흐름이 존재한다고 본다.
      expect(
        detailSource.contains('Navigator.of(context).pop()') ||
            detailSource.contains('Navigator.pop(context)'),
        isTrue,
        reason:
            'media_detail_screen에 Navigator.pop 호출이 없다. 인증 실패 시 '
            '화면을 빠져나갈 수 없다 (spec 6 (c) 위반).',
      );
    });
  });

  group('media_viewer_screen — per-item lock fullscreen gate', () {
    test('lock_session_provider import가 있다', () {
      expect(
        viewerSource.contains('lock_session_provider.dart'),
        isTrue,
        reason:
            'media_viewer_screen이 lock_session_provider를 import하지 않는다. '
            '풀스크린 뷰어 진입 시 인증 게이트가 없다 (spec 6 (c) 위반).',
      );
    });

    test('LockAuthService 토큰이 본문에 있다', () {
      expect(
        viewerSource.contains('LockAuthService'),
        isTrue,
        reason:
            'media_viewer_screen에 LockAuthService 참조가 없다. 인증 호출이 '
            '누락되어 잠긴 사진/영상을 즉시 노출한다 (spec 6 (c) 위반).',
      );
    });

    test('lockAuthServiceProvider 호출이 있다', () {
      expect(
        viewerSource.contains('lockAuthServiceProvider'),
        isTrue,
        reason:
            'media_viewer_screen에 lockAuthServiceProvider 호출이 없다. '
            '인증 다이얼로그가 뜨지 않는다 (spec 6 (c) 위반).',
      );
    });

    test('lockSessionProvider 호출이 있다 (5분 세션 통과 경로)', () {
      expect(
        viewerSource.contains('lockSessionProvider'),
        isTrue,
        reason:
            'media_viewer_screen이 lockSessionProvider를 확인하지 않는다. '
            '연속 진입마다 매번 인증을 띄운다 (UX 회귀).',
      );
    });

    test('isLocked 분기가 본문에 있다', () {
      expect(
        viewerSource.contains('isLocked'),
        isTrue,
        reason:
            'media_viewer_screen에 isLocked 분기가 없다. 잠금 여부와 무관하게 '
            '동일한 흐름을 타게 되어 보안/UX 양쪽 회귀.',
      );
    });

    test('인증 실패 시 Navigator.pop으로 화면을 종료한다', () {
      expect(
        viewerSource.contains('Navigator.of(context).pop()') ||
            viewerSource.contains('Navigator.pop(context)'),
        isTrue,
        reason:
            'media_viewer_screen에 Navigator.pop 호출이 없다. 인증 실패 시 '
            '뷰어를 닫을 수 없다 (spec 6 (c) 위반).',
      );
    });

    test('ConsumerStatefulWidget로 변경되었다 (ref 접근 가능)', () {
      expect(
        viewerSource.contains('ConsumerStatefulWidget'),
        isTrue,
        reason:
            'media_viewer_screen이 StatefulWidget으로 남아 있다. ref.read로 '
            'lockAuthServiceProvider에 접근할 수 없다.',
      );
    });
  });
}
