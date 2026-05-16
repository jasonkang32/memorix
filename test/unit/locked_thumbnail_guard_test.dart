import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// 잠긴 항목(`MediaItem.isLocked == 1`) 그리드/타임라인 표시 회귀 가드.
///
/// 배경:
/// - per-item lock: `is_locked == 1`인 항목은 그리드 셀에 블러 + 자물쇠
///   아이콘으로 표시되어야 한다. 인증 통과 후 풀스크린(`media_viewer_screen`)에서만
///   선명하게 보여야 한다 (spec 섹션 6 (a)).
/// - 검색 결과 화면도 같은 위젯(MediaThumbnailCard / MediaTimeline)을 쓰므로
///   자동으로 일관된 블러가 적용된다 (spec 섹션 6 (b)).
///
/// 가드 원칙:
/// - `media_thumbnail.dart`의 `MediaThumbnailCard.build` 본문에 lock 분기
///   (`isLocked` + `BackdropFilter` + `Icons.lock_rounded`) 토큰이 모두 존재.
/// - `media_timeline.dart`의 `_MediaImage.build` 본문에 동일한 토큰이 모두 존재.
/// - widget 렌더링/golden 검증은 별도. 여기서는 source-grep만 한다.
void main() {
  late String thumbnailSource;
  late String timelineSource;

  setUpAll(() {
    thumbnailSource = File(
      'lib/shared/widgets/media_thumbnail.dart',
    ).readAsStringSync();
    timelineSource = File(
      'lib/shared/widgets/media_timeline.dart',
    ).readAsStringSync();
  });

  group('media_thumbnail — locked item overlay invariant', () {
    test('MediaThumbnailCard.build 본문에 isLocked 분기가 있다', () {
      final body = _extractMethodBody(
        thumbnailSource,
        'build(BuildContext context)',
        afterAnchor: 'class MediaThumbnailCard',
      );
      expect(
        body.contains('isLocked'),
        isTrue,
        reason:
            '잠긴 항목(MediaItem.isLocked == 1)을 그리드 셀에서 가리는 분기가 '
            '없다. 인증 전에 잠긴 사진/영상이 그대로 노출된다 (spec 6 (a) 위반).',
      );
    });

    test('MediaThumbnailCard.build 본문에 BackdropFilter 블러 오버레이가 있다', () {
      final body = _extractMethodBody(
        thumbnailSource,
        'build(BuildContext context)',
        afterAnchor: 'class MediaThumbnailCard',
      );
      expect(
        body.contains('BackdropFilter'),
        isTrue,
        reason:
            '잠긴 항목 위에 BackdropFilter(블러)가 적용되지 않는다. 자물쇠 '
            '아이콘만 있으면 원본 이미지가 그대로 들여다보여서 잠금의 의미가 '
            '없다 (spec 6 (a) 위반).',
      );
    });

    test('MediaThumbnailCard.build 본문에 Icons.lock_rounded 아이콘이 있다', () {
      final body = _extractMethodBody(
        thumbnailSource,
        'build(BuildContext context)',
        afterAnchor: 'class MediaThumbnailCard',
      );
      expect(
        body.contains('Icons.lock_rounded'),
        isTrue,
        reason:
            '잠긴 항목에 자물쇠 아이콘(Icons.lock_rounded)이 표시되지 않는다. '
            '사용자가 항목이 잠긴 상태인지 인지할 수 없다 (spec 6 (a) 위반).',
      );
    });
  });

  group('media_timeline — _MediaImage locked overlay invariant', () {
    // _MediaImage는 build()에서 컨텐츠를 만들고 헬퍼(_wrapLock)에서 lock 분기를
    // 적용하는 구조이므로, 클래스 본문 전체에서 토큰을 검사한다.
    test('_MediaImage 클래스 본문에 isLocked 분기가 있다', () {
      final body = _extractClassBody(timelineSource, '_MediaImage');
      expect(
        body.contains('isLocked'),
        isTrue,
        reason:
            '_MediaImage가 isLocked 분기 없이 항상 원본을 렌더한다. 타임라인 '
            '카드에서 잠긴 항목이 그대로 노출된다 (spec 6 (a) 위반).',
      );
    });

    test('_MediaImage 클래스 본문에 BackdropFilter 블러 오버레이가 있다', () {
      final body = _extractClassBody(timelineSource, '_MediaImage');
      expect(
        body.contains('BackdropFilter'),
        isTrue,
        reason:
            '_MediaImage가 BackdropFilter(블러)를 적용하지 않는다. 잠긴 항목의 '
            '원본이 들여다보인다 (spec 6 (a) 위반).',
      );
    });

    test('_MediaImage 클래스 본문에 Icons.lock_rounded 아이콘이 있다', () {
      final body = _extractClassBody(timelineSource, '_MediaImage');
      expect(
        body.contains('Icons.lock_rounded'),
        isTrue,
        reason:
            '_MediaImage에 자물쇠 아이콘(Icons.lock_rounded)이 없다. 잠긴 '
            '항목임을 사용자가 알 수 없다 (spec 6 (a) 위반).',
      );
    });
  });
}

/// 클래스 본문 전체 추출 (`class Name ... { ... }`).
String _extractClassBody(String source, String className) {
  final anchor = RegExp(r'class\s+' + RegExp.escape(className) + r'\b')
      .firstMatch(source);
  if (anchor == null) {
    throw StateError('Class not found: $className');
  }
  final braceStart = source.indexOf('{', anchor.end);
  if (braceStart < 0) {
    throw StateError('Class body opening not found for $className');
  }
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
  throw StateError('Unbalanced braces for class $className');
}

/// 메서드 본문 추출 (block 또는 expression-bodied).
/// [afterAnchor]가 주어지면 그 위치 이후에서 [signaturePrefix]를 찾는다
/// (같은 시그니처가 여러 클래스에 있을 때 특정 클래스 안의 것만 잡기 위함).
String _extractMethodBody(
  String source,
  String signaturePrefix, {
  String? afterAnchor,
}) {
  var searchFrom = 0;
  if (afterAnchor != null) {
    final anchor = source.indexOf(afterAnchor);
    if (anchor < 0) {
      throw StateError('Anchor not found: $afterAnchor');
    }
    searchFrom = anchor;
  }
  final start = source.indexOf(signaturePrefix, searchFrom);
  if (start < 0) {
    throw StateError(
      'Signature not found: $signaturePrefix'
      '${afterAnchor != null ? ' after $afterAnchor' : ''}',
    );
  }

  final after = source.substring(start);
  final blockMatch = RegExp(r'\)\s*(?:async\*?\s*|sync\*?\s*)?\{')
      .firstMatch(after);
  if (blockMatch == null) {
    throw StateError('Body opening not found for $signaturePrefix');
  }
  final braceStart = start + blockMatch.end - 1;

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
