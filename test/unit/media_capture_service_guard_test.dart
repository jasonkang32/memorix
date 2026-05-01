import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// MediaCaptureService.pickGallery 구조적 invariant 가드.
///
/// 배경:
/// - 갤러리 다중 선택에 fallback 3단계(native → image_picker → file_picker)가
///   누적되어 디버깅 비용이 폭증한 사건이 있었다.
/// - 2단계로 단순화 후, 다음 fallback 추가 충동이 들면 이 테스트가 먼저 깨진다.
/// - 진짜 필요한 경우엔 이 테스트와 함께 의도를 코드에 명시한 뒤 수정한다.
///
/// 외부 의존성을 mock하지 않고 소스 자체를 읽어 구조를 검증하는 architecture
/// guard test 패턴이다. ImagePicker / MethodChannel / StorageService / sqflite를
/// mock하는 일반 단위 테스트보다 회귀 차단 의도에 더 정확하다.
void main() {
  late String source;

  setUpAll(() {
    source = File('lib/core/services/media_capture_service.dart')
        .readAsStringSync();
  });

  group('MediaCaptureService.pickGallery — 구조적 invariant', () {
    test('pickGallery 본문에 FilePicker 호출이 없다', () {
      final body = _extractFunctionBody(
        source,
        'static Future<List<CapturedMedia>> pickGallery(',
      );
      expect(
        body.contains('FilePicker'),
        isFalse,
        reason:
            'pickGallery에 FilePicker 호출이 추가되었다. 갤러리 다중 선택의 다중 '
            'fallback은 디버깅 비용 폭증의 원인이었다. 진짜 필요하면 이 테스트와 '
            '함께 의도를 명시한 뒤 수정할 것.',
      );
    });

    test('pickGallery는 native picker + image_picker 2단계만 가진다', () {
      final body = _extractFunctionBody(
        source,
        'static Future<List<CapturedMedia>> pickGallery(',
      );

      final nativeCount =
          '_pickGalleryWithAndroidPicker'.allMatches(body).length;
      final imagePickerCount = 'pickMultipleMedia'.allMatches(body).length;

      expect(
        nativeCount,
        1,
        reason: 'native picker 호출은 정확히 1회여야 한다 (단계 추가/중복 금지).',
      );
      expect(
        imagePickerCount,
        1,
        reason:
            'image_picker.pickMultipleMedia 호출은 정확히 1회여야 한다 (분기 추가 금지).',
      );
    });

    test('importFromPlatformFiles 함수가 부활하지 않았다', () {
      expect(
        source.contains('importFromPlatformFiles'),
        isFalse,
        reason:
            'importFromPlatformFiles는 file_picker 갤러리 fallback과 함께 제거되었다. '
            '부활은 같은 종류의 중복 누적 위험 신호.',
      );
    });

    test('갤러리 import 경로는 importFromXFiles + importFromPaths 2개로 한정된다', () {
      // 외부 호출 가능한 import 함수: 정확히 이 둘만 존재해야 한다.
      // (importFromPlatformFiles 부활 방지 + 새로운 import 헬퍼 무분별 추가 방지)
      final importFunctions = RegExp(
        r'static Future<List<CapturedMedia>>\s+(importFrom\w+)\(',
      ).allMatches(source).map((m) => m.group(1)!).toSet();

      expect(
        importFunctions,
        equals({'importFromXFiles', 'importFromPaths'}),
        reason:
            'importFrom* 헬퍼는 importFromXFiles(image_picker용) + '
            'importFromPaths(messenger import용) 2개만 허용된다. '
            '새 헬퍼 추가 시 정규화 레이어를 먼저 도입할 것.',
      );
    });
  });
}

/// 시그니처 prefix로 함수 본문 추출 (중괄호 매칭).
///
/// 명명 매개변수 `{...}`를 본문 `{...}`로 오인하지 않도록 `) async {` 또는
/// `) {` 패턴(매개변수 닫기 + 선택적 async/sync modifier + 본문 여는 중괄호)을
/// 본문 시작점으로 잡는다.
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
  final braceStart = start + match.end - 1; // 매치의 마지막 문자 = '{'

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
