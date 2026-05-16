import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// QA Round 2 회귀 가드: detail "미디어 추가"가 기존 batchId를 인계해야 한다.
///
/// 배경:
/// - MediaTimeline은 같은 batchId 항목을 한 카드로 묶음.
/// - 사용자가 기존 work 카드 detail 진입 → "미디어 추가" → 저장 시
///   `MediaSaveService.saveAll(...)` 호출에 batchId 인계 안 했음 → saveAll
///   내부가 새 uuid 생성 → 새 카드 생성. 사용자가 "기존 work에 안 합쳐짐"으로 인식.
///
/// 가드 원칙:
/// - `MediaSaveService.saveAll` 시그니처에 `overrideBatchId` 매개변수가 있다.
/// - 본문에서 `overrideBatchId ?? ...` 분기로 인계 우선.
/// - `MediaDetailScreen._addMedia`에서 `overrideBatchId:` 인자로 전달한다.
void main() {
  late String saveServiceSource;
  late String detailScreenSource;

  setUpAll(() {
    saveServiceSource = File('lib/core/services/media_save_service.dart')
        .readAsStringSync();
    detailScreenSource = File('lib/shared/screens/media_detail_screen.dart')
        .readAsStringSync();
  });

  group('Detail "미디어 추가" — batchId 인계 invariant', () {
    test('saveAll 시그니처에 overrideBatchId 매개변수가 있다', () {
      // `static Future<List<MediaSaveResult>> saveAll({...})` 매개변수 블록 추출
      final sigPattern = RegExp(
        r'Future<List<MediaSaveResult>>\s+saveAll\(\s*(\{[^}]*\})',
      );
      final match = sigPattern.firstMatch(saveServiceSource);
      expect(
        match,
        isNotNull,
        reason: 'saveAll 시그니처를 찾지 못함',
      );
      expect(
        match!.group(1)!.contains('overrideBatchId'),
        isTrue,
        reason:
            'saveAll에 overrideBatchId 매개변수가 없다. detail 안에서 추가 시 '
            '기존 batchId 인계 불가 → 새 카드가 생성됨 (QA Round 2 회귀).',
      );
    });

    test('saveAll 본문이 overrideBatchId를 우선 사용한다', () {
      // `overrideBatchId ?? (captured.length > 1 ? _uuid.v4() : '')`
      final pattern = RegExp(r'overrideBatchId\s*\?\?');
      expect(
        pattern.hasMatch(saveServiceSource),
        isTrue,
        reason:
            'saveAll 본문이 overrideBatchId 인계 처리를 안 한다. 매개변수만 '
            '받고 무시 → 여전히 새 batchId가 생성된다.',
      );
    });

    test('MediaDetailScreen._addMedia가 overrideBatchId를 전달한다', () {
      // `_addMedia` 함수 본문에 `overrideBatchId:` 인자 등장
      // 단순 source-grep — _addMedia 내부에 saveAll 호출이 1군데뿐이므로 충분.
      final addMediaPattern = RegExp(
        r'Future<void>\s+_addMedia\([^{]*\)\s*async\s*\{',
      );
      final match = addMediaPattern.firstMatch(detailScreenSource);
      expect(
        match,
        isNotNull,
        reason: '_addMedia 시그니처를 찾지 못함',
      );

      // _addMedia 본문 추출
      final braceStart = match!.end - 1;
      var depth = 0;
      var braceEnd = -1;
      for (var i = braceStart; i < detailScreenSource.length; i++) {
        final c = detailScreenSource[i];
        if (c == '{') depth++;
        if (c == '}') {
          depth--;
          if (depth == 0) {
            braceEnd = i + 1;
            break;
          }
        }
      }
      expect(braceEnd, greaterThan(braceStart),
          reason: '_addMedia 본문 매칭 실패');
      final body = detailScreenSource.substring(braceStart, braceEnd);

      expect(
        body.contains('overrideBatchId'),
        isTrue,
        reason:
            '_addMedia 본문에 overrideBatchId 전달 코드가 없다. saveAll이 '
            '새 batchId를 만들어 새 카드가 생성됨 (QA Round 2 회귀).',
      );
    });
  });
}
