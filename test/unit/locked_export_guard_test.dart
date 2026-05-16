import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// 잠긴 항목(`is_locked = 1`)은 외부 노출 채널에서 자동 제외되어야 한다 (spec §8).
///
/// 외부 노출 채널:
///   (a) Drive 동기화 — `findPendingSync`
///   (b) PDF 보고서 — `report_screen.dart`의 `findWork(includeLocked: false)`
///
/// 내부 노출 채널 (잠긴 항목 그대로 노출 — 위젯 단계에서 블러):
///   (c) HomeSummary 통계 — `home_provider.dart`
///   (d) FTS5 검색 인덱스 — 그리드 위젯이 블러 처리
///
/// 가드 원칙: source-grep 기반 회귀 방지. 누군가 실수로 필터를 빼면 즉시 RED.
void main() {
  late String mediaDaoSrc;
  late String reportScreenSrc;
  late String homeProviderSrc;
  late String mediaDetailScreenSrc;

  setUpAll(() {
    mediaDaoSrc = File('lib/core/db/media_dao.dart').readAsStringSync();
    reportScreenSrc = File(
      'lib/features/work/screens/report_screen.dart',
    ).readAsStringSync();
    homeProviderSrc = File(
      'lib/features/home/providers/home_provider.dart',
    ).readAsStringSync();
    // 단순히 파일 존재 여부 확인용 — 풀스크린 진입 게이트는 Phase 3.
    final detailFile = File('lib/features/personal/screens/media_detail_screen.dart');
    mediaDetailScreenSrc = detailFile.existsSync()
        ? detailFile.readAsStringSync()
        : '';
  });

  group('(a) Drive 동기화 — findPendingSync는 잠긴 항목 제외', () {
    test('findPendingSync 본문에 is_locked = 0 토큰이 등장한다', () {
      final body = _extractFunctionBody(
        mediaDaoSrc,
        'Future<List<MediaItem>> findPendingSync(',
      );
      // 공백 변형 허용
      final hasFilter =
          body.contains('is_locked = 0') || body.contains('is_locked=0');
      expect(
        hasFilter,
        isTrue,
        reason:
            'findPendingSync에 is_locked = 0 필터가 없다. 잠긴 항목이 Drive로 '
            '업로드되어 외부 클라우드에 노출된다 (spec 8a 위반).',
      );
    });

    test('findPendingSync 본문에 drive_synced = 0 필터는 그대로 유지된다', () {
      final body = _extractFunctionBody(
        mediaDaoSrc,
        'Future<List<MediaItem>> findPendingSync(',
      );
      final hasSync =
          body.contains('drive_synced = 0') || body.contains('drive_synced=0');
      expect(
        hasSync,
        isTrue,
        reason: 'drive_synced 필터가 사라졌다 — 동기화된 항목까지 다시 큐에 들어간다.',
      );
    });
  });

  group('(b) PDF 보고서 — 잠긴 항목 자동 제외', () {
    test('findWork 시그니처에 includeLocked 매개변수가 있다', () {
      final sigPattern = RegExp(
        r'Future<List<MediaItem>>\s+findWork\(\s*(\{[^}]*\})',
      );
      final match = sigPattern.firstMatch(mediaDaoSrc);
      expect(match, isNotNull, reason: 'findWork 시그니처를 찾지 못함.');
      final params = match!.group(1)!;
      expect(
        params.contains('includeLocked'),
        isTrue,
        reason:
            'findWork에 includeLocked 매개변수가 없다. PDF 호출처가 잠긴 '
            '항목을 명시적으로 배제할 수단이 없다 (spec 8b).',
      );
    });

    test('findWork 본문에 is_locked = 0 분기가 존재한다', () {
      final body = _extractFunctionBody(
        mediaDaoSrc,
        'Future<List<MediaItem>> findWork(',
      );
      final hasFilter =
          body.contains('is_locked = 0') || body.contains('is_locked=0');
      expect(
        hasFilter,
        isTrue,
        reason:
            'findWork 본문에 is_locked 필터 분기가 없다. includeLocked: false '
            '호출이 무시된다.',
      );
    });

    test('report_screen.dart는 includeLocked: false를 명시한다', () {
      // 띄어쓰기/줄바꿈 변형 허용
      final hasExplicitFalse = RegExp(
        r'includeLocked\s*:\s*false',
      ).hasMatch(reportScreenSrc);
      expect(
        hasExplicitFalse,
        isTrue,
        reason:
            'report_screen.dart의 findWork 호출에 includeLocked: false가 '
            '없다. PDF 보고서에 잠긴 항목이 포함될 수 있다 (spec 8b).',
      );
    });
  });

  group('(c) HomeSummary 통계 — 잠긴 항목도 카운트에 포함 (의도)', () {
    test('home_provider는 is_locked 필터를 직접 추가하지 않는다', () {
      // 통계는 메타·사이즈만 노출하므로 잠긴 항목도 포함되어야 한다.
      // 만약 누군가 home_provider에서 is_locked 필터를 추가하면 통계가 거짓이 된다.
      expect(
        homeProviderSrc.contains('is_locked'),
        isFalse,
        reason:
            'home_provider.dart에 is_locked 토큰이 등장한다. HomeSummary 통계는 '
            '잠긴 항목을 포함해야 한다 (spec 8c). 필터가 필요하면 별도 카운터를 추가하라.',
      );
    });

    test('home_provider는 여전히 findPendingSync를 호출한다', () {
      // findPendingSync 자체가 이미 is_locked = 0을 적용하므로 home의 pendingSync
      // 카운트도 잠긴 항목이 빠진다 — 의미적으로 옳다 (잠긴 항목은 동기화 대상이 아님).
      expect(
        homeProviderSrc.contains('findPendingSync'),
        isTrue,
        reason: 'home_provider의 findPendingSync 호출이 사라졌다.',
      );
    });
  });

  group('(d) FTS5 검색 — 인덱스 자체는 변경 없음', () {
    test('search() 본문에 is_locked 필터가 추가되지 않았다', () {
      // 검색 결과 화면이 그리드와 동일하게 블러 처리하므로 인덱스 매칭은 그대로.
      // 만약 검색 SQL에 is_locked가 추가되면 잠긴 항목을 검색으로 못 찾게 됨.
      final body = _extractFunctionBody(
        mediaDaoSrc,
        'Future<List<MediaItem>> search(',
      );
      expect(
        body.contains('is_locked'),
        isFalse,
        reason:
            'search() 본문에 is_locked 토큰이 등장한다. FTS5 검색은 잠긴 항목도 '
            '매칭해야 한다 (위젯 단계에서 블러). spec 8d 위반.',
      );
    });
  });

  // 후속 라운드 가드 — Phase 3가 도착하면 활성화.
  group('Phase 3 placeholder (skipped)', () {
    test(
      '풀스크린 진입 게이트는 Phase 3에서 추가 — 현재 미구현',
      () {
        // media_detail_screen / media_viewer_screen은 이번 Phase에서 건드리지 않는다.
        expect(mediaDetailScreenSrc, isA<String>());
      },
      skip: 'Phase 3 작업 영역 — 본 가드는 Phase 2A 범위만 검증.',
    );
  });
}

/// 시그니처 prefix로 함수 본문 추출 (중괄호 매칭).
String _extractFunctionBody(String source, String signaturePrefix) {
  final start = source.indexOf(signaturePrefix);
  if (start < 0) {
    throw StateError('Signature not found: $signaturePrefix');
  }

  final bodyOpenPattern = RegExp(r'\)\s*(?:async\*?\s*|sync\*?\s*)?\{');
  final match = bodyOpenPattern.firstMatch(source.substring(start));
  if (match == null) {
    throw StateError('Body opening not found for $signaturePrefix');
  }
  final braceStart = start + match.end - 1;

  var depth = 0;
  for (var i = braceStart; i < source.length; i++) {
    final c = source[i];
    if (c == '{') depth++;
    if (c == '}') {
      depth--;
      if (depth == 0) {
        return source.substring(braceStart, i + 1);
      }
    }
  }
  throw StateError('Unbalanced braces for $signaturePrefix');
}
