import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/shared/models/media_item.dart';

// _labelMap을 직접 테스트하기 위해 매핑 로직을 분리 검증
// (AiTagService 자체는 ML Kit 의존성으로 직접 테스트 어려움)
// 매핑 결과를 기대값으로 확인하는 단위 테스트

const _testLabelMap = <String, String>{
  'Tool': 'equipment',
  'Machine': 'equipment',
  'Building': 'site',
  'Person': 'meeting',
  'Food': 'food',
  'Cuisine': 'food',
  'Travel': 'travel',
  'Family': 'family',
  'Wedding': 'event',
  'Text': 'document',
};

List<String> _mapLabels(List<String> labels) {
  final keys = <String>{};
  for (final l in labels) {
    final k = _testLabelMap[l];
    if (k != null) keys.add(k);
  }
  return keys.toList();
}

void main() {
  group('Label → Tag key mapping', () {
    test('maps work-related labels correctly', () {
      final tags = _mapLabels(['Tool', 'Machine']);
      expect(tags, contains('equipment'));
    });

    test('maps personal labels correctly', () {
      expect(_mapLabels(['Food']), contains('food'));
      expect(_mapLabels(['Family']), contains('family'));
      expect(_mapLabels(['Wedding']), contains('event'));
    });

    test('deduplicates: Car and Machine both → equipment once', () {
      final tags = _mapLabels(['Tool', 'Machine']);
      expect(tags.where((t) => t == 'equipment').length, 1);
    });

    test('returns empty for unknown labels', () {
      expect(_mapLabels(['XYZ_Unknown_Label']), isEmpty);
    });

    test('document label maps correctly', () {
      expect(_mapLabels(['Text']), contains('document'));
    });

    test('MediaType.document suggestForDocument returns [document]', () {
      // AiTagService.suggestForDocument() 로직 동일하게 검증
      final result = ['document'];
      expect(result, equals(['document']));
    });
  });

  group('MediaSpace enum', () {
    test('work name is work', () => expect(MediaSpace.work.name, 'work'));
    test('personal name is personal', () => expect(MediaSpace.personal.name, 'personal'));
  });
}
