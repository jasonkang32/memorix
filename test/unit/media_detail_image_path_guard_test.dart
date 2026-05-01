import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Bug #1 회귀 가드: media_detail_screen의 사진 preview는 filePath(원본)를 써야 한다.
///
/// 배경:
/// - `_buildSingleImage`가 `cur.thumbPath ?? cur.filePath`로 path를 정했다.
/// - 결과: 사진의 경우 항상 썸네일(300x200, quality 75 압축)을 240×full-width로
///   stretch해서 보여줬다 → 화질 저하 + 흐림.
/// - 비디오는 mp4라 Image.file에 못 쓰므로 thumbPath가 맞다.
///
/// 가드 원칙:
/// - 사진은 filePath(원본) 우선.
/// - 비디오는 thumbPath 사용 (Image.file에 .mp4 못 띄움).
/// - mediaType 분기가 본문에 등장해야 한다.
void main() {
  late String source;

  setUpAll(() {
    source = File('lib/shared/screens/media_detail_screen.dart')
        .readAsStringSync();
  });

  group('media_detail_screen — preview path invariant', () {
    test('_buildSingleImage 본문에 mediaType 분기가 있다 (사진 vs 비디오)', () {
      final body = _extractMethodBody(
        source,
        '_buildSingleImage(MediaItem cur, int index)',
      );
      expect(
        body.contains('MediaType.video') ||
            body.contains("'video'") ||
            body.contains('mediaType =='),
        isTrue,
        reason:
            '_buildSingleImage가 mediaType 분기 없이 무조건 thumbPath를 쓴다. '
            '사진의 경우 압축된 썸네일을 stretch해서 화질 저하 (Bug #1).',
      );
    });

    test('path 변수 할당이 단순 thumbPath fallback이 아니다 (분기 필수)', () {
      final body = _extractMethodBody(
        source,
        '_buildSingleImage(MediaItem cur, int index)',
      );
      // `final path = cur.thumbPath ??` 같이 분기 없이 바로 thumbPath로 시작하면 안티패턴.
      // 비디오 분기 안의 (cur.thumbPath ?? ...)는 OK.
      final antipattern = RegExp(
        r'final\s+path\s*=\s*cur\.thumbPath\s*\?\?',
      );
      expect(
        antipattern.hasMatch(body),
        isFalse,
        reason:
            '`final path = cur.thumbPath ?? ...` 분기 없는 할당이 있다. 사진의 '
            '경우도 무조건 썸네일이 쓰여 화질이 떨어진다 (Bug #1). mediaType '
            '분기로 사진은 filePath 우선해야 한다.',
      );
    });
  });
}

/// 메서드 본문 추출 (block 또는 expression-bodied).
String _extractMethodBody(String source, String signaturePrefix) {
  final start = source.indexOf(signaturePrefix);
  if (start < 0) {
    throw StateError('Signature not found: $signaturePrefix');
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
