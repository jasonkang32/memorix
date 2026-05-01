import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Bug #4 회귀 가드: MediaSaveService._enhance가 OCR/AI 태그를 실제로 호출해야 한다.
///
/// 배경:
/// - `_enhance`가 TODO 주석으로 통째 비활성화되어 있었다 (블랙스크린 회피 사유).
/// - 결과: 사용자가 사진을 추가해도 OCR 텍스트 추출/AI 태그 추천이 동작하지 않았다.
///
/// 가드 원칙:
/// - `_enhance` 본문에 `OcrService.extractText` 호출이 있어야 한다.
/// - `_enhance` 본문에 `_suggestAndApplyTags` 호출이 있어야 한다.
/// - 본문에 `// TODO` 만으로 끝나는 빈 메서드면 안 된다 (실제 코드 활성화 검증).
///
/// 메모리 안전: phase 2 루프가 이미 순차이므로 다중 폭발 위험 완화됨.
/// 추가로 OCR과 태그 사이에 Future.delayed(zero) yield 권장.
void main() {
  late String source;

  setUpAll(() {
    source = File('lib/core/services/media_save_service.dart').readAsStringSync();
  });

  group('MediaSaveService._enhance — OCR/AI 활성 invariant', () {
    test('_enhance 본문에 OcrService.extractText 호출이 있다', () {
      final body = _extractFunctionBody(
        source,
        'static Future<void> _enhance(',
      );
      expect(
        body.contains('OcrService.extractText'),
        isTrue,
        reason:
            '_enhance가 OcrService를 호출하지 않는다. 사용자가 사진을 추가해도 '
            'OCR 텍스트 추출이 안 된다 (Bug #4). TODO 주석 풀고 활성화 필요.',
      );
    });

    test('_enhance 본문에 _suggestAndApplyTags 호출이 있다', () {
      final body = _extractFunctionBody(
        source,
        'static Future<void> _enhance(',
      );
      expect(
        body.contains('_suggestAndApplyTags'),
        isTrue,
        reason:
            '_enhance가 _suggestAndApplyTags를 호출하지 않는다. AI 태그 추천이 '
            '동작하지 않음 (Bug #4).',
      );
    });

    test('_enhance가 빈 메서드(주석만)가 아니다', () {
      final body = _extractFunctionBody(
        source,
        'static Future<void> _enhance(',
      );
      // 주석만 있고 실제 실행 statement가 없는 케이스 잡기.
      // 실제 코드 라인이 최소 5줄 이상은 있어야 한다 (OCR + 태그 흐름이라 충분).
      final nonCommentLines = body
          .split('\n')
          .map((l) => l.trim())
          .where((l) =>
              l.isNotEmpty &&
              !l.startsWith('//') &&
              !l.startsWith('/*') &&
              !l.startsWith('*') &&
              l != '{' &&
              l != '}' &&
              l != 'return;')
          .length;
      expect(
        nonCommentLines,
        greaterThan(4),
        reason:
            '_enhance가 사실상 빈 메서드. 활성 코드 라인이 너무 적음. '
            'Bug #4 회귀 (전체가 다시 주석 처리됨).',
      );
    });
  });
}

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
