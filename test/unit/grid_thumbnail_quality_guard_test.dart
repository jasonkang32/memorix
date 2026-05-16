import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Bug #1 (그리드 썸네일 화질) 회귀 가드.
///
/// 배경:
/// - StorageService.savePhoto는 사진을 저장할 때 원본(filePath)과 압축된
///   썸네일(thumbPath, 300x200 minWidth/minHeight + quality 75 강제 압축)
///   둘 다 만든다.
/// - Work 탭 그리드/타임라인이 thumbPath를 그대로 stretch하면 화질이 저하된다.
/// - 해결: 사진은 원본 filePath + Image.file의 cacheWidth로 디코더 단계에서
///   다운샘플링 (메모리 효율 + 화질 보존). 비디오는 mp4라 Image.file에 못 띄우니
///   thumbPath 유지.
///
/// 가드 원칙:
/// - media_thumbnail.dart의 _buildThumbnail에 cacheWidth가 등장해야 한다.
/// - media_timeline.dart의 _MediaImage 본문에 cacheWidth가 등장해야 한다.
/// - 사진 케이스에서 `final ... = item.thumbPath ?? item.filePath` 식의
///   "thumbPath 우선 fallback to filePath" 안티패턴을 쓰면 안 된다.
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

  group('media_thumbnail — grid quality invariant', () {
    test('_buildThumbnail 본문에 cacheWidth가 있다 (디코더 다운샘플링)', () {
      final body = _extractMethodBody(
        thumbnailSource,
        '_buildThumbnail(BuildContext context)',
      );
      expect(
        body.contains('cacheWidth'),
        isTrue,
        reason:
            'Image.file이 cacheWidth 없이 호출된다. 원본 큰 이미지를 셀 크기에 '
            '맞춰 stretch하면 메모리 폭증 또는 화질 저하. cacheWidth로 디코더 '
            '단계 다운샘플 필수 (Bug #1 회귀 가드).',
      );
    });

    test('사진 케이스에서 thumbPath 우선 fallback to filePath 안티패턴이 없다', () {
      final body = _extractMethodBody(
        thumbnailSource,
        '_buildThumbnail(BuildContext context)',
      );
      // 사진 분기 밖에서 `thumb ?? file` 또는 `item.thumbPath ?? item.filePath`
      // 식으로 thumbPath를 무조건 우선시키면 화질 저하 (Bug #1).
      // 비디오는 thumbPath만 쓰므로 이 패턴 자체가 안 나온다.
      final antipattern = RegExp(
        r'(thumb\s*\?\?\s*(?:item\.)?file(?:Path)?|'
        r'(?:item\.)?thumbPath\s*\?\?\s*(?:item\.)?filePath)',
      );
      expect(
        antipattern.hasMatch(body),
        isFalse,
        reason:
            '`thumbPath ?? filePath` 안티패턴이 있다. 사진의 경우 압축된 '
            'thumbPath가 우선 사용되어 화질이 저하된다 (Bug #1).',
      );
    });
  });

  group('media_timeline — _MediaImage grid quality invariant', () {
    test('_MediaImage build 본문에 cacheWidth가 있다 (디코더 다운샘플링)', () {
      final body = _extractMethodBody(
        timelineSource,
        'build(BuildContext context)',
        afterAnchor: 'class _MediaImage',
      );
      expect(
        body.contains('cacheWidth'),
        isTrue,
        reason:
            '_MediaImage가 Image.file을 cacheWidth 없이 호출한다. 큰 원본을 '
            '카드 크기에 맞춰 디코딩하면 메모리 폭증, 압축 썸네일을 stretch하면 '
            '화질 저하. cacheWidth로 디코더 단계 다운샘플 필수 (Bug #1 회귀 가드).',
      );
    });

    test('_MediaImage 사진 분기에서 thumbPath 우선 fallback to filePath 안티패턴이 없다', () {
      final body = _extractMethodBody(
        timelineSource,
        'build(BuildContext context)',
        afterAnchor: 'class _MediaImage',
      );
      final antipattern = RegExp(
        r'(thumb\s*\?\?\s*(?:item\.)?file(?:Path)?|'
        r'(?:item\.)?thumbPath\s*\?\?\s*(?:item\.)?filePath)',
      );
      expect(
        antipattern.hasMatch(body),
        isFalse,
        reason:
            '`thumbPath ?? filePath` 안티패턴이 있다. 사진의 경우 압축된 '
            'thumbPath가 우선 사용되어 화질이 저하된다 (Bug #1).',
      );
    });
  });
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
