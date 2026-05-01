import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Bug #3 회귀 가드: MediaTimeline 섹션 키는 createdAt 기반이어야 한다.
///
/// 배경:
/// - `_dateKey`가 `item.takenAt` (EXIF 촬영일)을 섹션 키로 썼다.
/// - 사용자가 갤러리에서 옛날 사진을 추가하면 EXIF가 과거 날짜라
///   새 항목이 과거 섹션에 묻혀 화면 상단에 안 보였다.
/// - 사용자 인식: "Work에 추가가 안 되고 새로 등록됨"
///
/// 가드 원칙:
/// - memorix는 보관함이라 "내가 기록한 시간(createdAt)"이 자연스럽다.
/// - 코드 안의 같은 라인 주석도 이미 동의: "EXIF 촬영일 기준 X"
/// - 섹션 정렬(이미 createdAt)과 섹션 키(takenAt이었음)의 일관성 회복.
void main() {
  late String source;

  setUpAll(() {
    source = File('lib/shared/widgets/media_timeline.dart').readAsStringSync();
  });

  group('MediaTimeline._dateKey — createdAt 기반 invariant', () {
    test('_dateKey 본문이 createdAt을 사용한다', () {
      final body = _extractMethodBody(source, '_dateKey(MediaItem item)');
      expect(
        body.contains('createdAt'),
        isTrue,
        reason:
            '_dateKey가 createdAt이 아닌 다른 필드를 사용한다. 사용자가 옛날 사진을 '
            '추가하면 과거 섹션에 묻혀 안 보인다 (Bug #3). 보관함은 등록 시간 기준.',
      );
    });

    test('_dateKey 본문이 takenAt을 직접 쓰지 않는다', () {
      final body = _extractMethodBody(source, '_dateKey(MediaItem item)');
      expect(
        body.contains('takenAt'),
        isFalse,
        reason:
            '_dateKey가 takenAt(EXIF 촬영일)을 쓴다. 옛날 사진 추가 시 과거 '
            '섹션으로 묻힘. createdAt(등록일)으로 변경 필요.',
      );
    });
  });
}

/// 짧은 시그니처로 메서드 본문 추출. expression-bodied (`=>`)와 block (`{}`) 둘 다 지원.
String _extractMethodBody(String source, String signaturePrefix) {
  final start = source.indexOf(signaturePrefix);
  if (start < 0) {
    throw StateError('Signature not found: $signaturePrefix');
  }

  // signature 다음 문자가 ')' 일 때까지 진행 후, '=>' 또는 '{' 까지.
  final after = source.substring(start);
  final exprMatch = RegExp(r'\)\s*=>').firstMatch(after);
  final blockMatch = RegExp(r'\)\s*(?:async\*?\s*|sync\*?\s*)?\{').firstMatch(after);

  // expression-bodied 우선 시도 (더 짧은 메서드)
  if (exprMatch != null &&
      (blockMatch == null || exprMatch.start < blockMatch.start)) {
    final bodyStart = start + exprMatch.end;
    // ';' 또는 다음 ')' until matched 까지
    final endIdx = source.indexOf(';', bodyStart);
    if (endIdx < 0) throw StateError('No expression terminator');
    return source.substring(bodyStart, endIdx);
  }

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
