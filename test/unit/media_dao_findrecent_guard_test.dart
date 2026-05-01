import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Bug #2 회귀 가드: findRecent()는 반드시 space 필터를 가져야 한다.
///
/// 배경:
/// - Home의 "최근 등록"이 findRecent()를 호출했는데 WHERE 절이 0개였다.
/// - 결과: secret 보관함의 미디어가 Home에 노출되었다 (privacy leak).
///
/// 가드 원칙:
/// - findRecent() 본문에 `where:` 인자가 있어야 한다 (sqflite query 호출).
/// - 본문에 'space' 토큰이 등장해야 한다 (필터링한다는 증거).
/// - findRecent의 시그니처에 space 파라미터(또는 동등한 필터)가 있어야 한다.
///
/// 단순 sql 문자열 검사는 거짓 음성 위험이 있지만, 호출처 단순화 위해
/// architecture guard로 시작. 후속 통합 테스트에서 in-memory sqlite로 보강 가능.
void main() {
  late String source;

  setUpAll(() {
    source = File('lib/core/db/media_dao.dart').readAsStringSync();
  });

  group('MediaDao.findRecent — space 필터 invariant', () {
    test('findRecent 본문에 where 절이 존재한다', () {
      final body = _extractFunctionBody(source, 'Future<List<MediaItem>> findRecent(');
      expect(
        body.contains('where'),
        isTrue,
        reason:
            'findRecent에 where 절이 없다. 모든 space의 미디어가 반환되어 '
            'secret 보관함이 Home에 노출된다 (Bug #2). space 필터 필수.',
      );
    });

    test('findRecent 본문에 space 토큰이 등장한다', () {
      final body = _extractFunctionBody(source, 'Future<List<MediaItem>> findRecent(');
      expect(
        body.contains('space'),
        isTrue,
        reason:
            'findRecent에 space 토큰이 없다. work/secret 분리가 안 되어 '
            'privacy leak 위험. WHERE space = \'work\' 같은 필터 필요.',
      );
    });

    test('findRecent 시그니처에 space 매개변수가 있다', () {
      // `Future<List<MediaItem>> findRecent({...})` 의 매개변수 블록을 추출.
      final sigPattern = RegExp(
        r'Future<List<MediaItem>>\s+findRecent\(\s*(\{[^}]*\})',
      );
      final match = sigPattern.firstMatch(source);
      expect(
        match,
        isNotNull,
        reason: 'findRecent 시그니처를 찾지 못함 — 함수가 사라졌나?',
      );
      final params = match!.group(1)!;
      expect(
        params.contains('space'),
        isTrue,
        reason:
            'findRecent에 space 매개변수가 없다. 호출처가 깜빡하면 모든 '
            'space가 반환된다. 매개변수로 강제하거나 default를 work로 박을 것.',
      );
    });
  });
}

/// 시그니처 prefix로 함수 본문 추출 (중괄호 매칭).
/// 명명 매개변수 `{...}`를 본문 `{...}`로 오인하지 않도록 `) async {` 패턴 사용.
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
