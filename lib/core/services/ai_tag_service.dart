import 'dart:async';

import 'package:google_mlkit_image_labeling/google_mlkit_image_labeling.dart';
import '../../shared/models/media_item.dart';

/// ML Kit 레이블 → Memorix 태그 key 매핑
const _labelMap = <String, String>{
  // Work 관련
  'Tool': 'equipment',
  'Machine': 'equipment',
  'Electronics': 'equipment',
  'Technology': 'equipment',
  'Building': 'site',
  'Architecture': 'site',
  'Construction': 'install',
  'Engineering': 'install',
  'Person': 'meeting',
  'People': 'meeting',
  'Text': 'document',
  'Document': 'document',
  'Paper': 'document',
  // Personal 관련
  'Food': 'food',
  'Dish': 'food',
  'Cuisine': 'food',
  'Travel': 'travel',
  'Landmark': 'travel',
  'Mountain': 'travel',
  'Beach': 'travel',
  'Family': 'family',
  'Child': 'family',
  'Wedding': 'event',
  'Celebration': 'event',
  'Party': 'event',
};

class AiTagService {
  static const _confidenceThreshold = 0.75;

  static final _labeler = ImageLabeler(
    options: ImageLabelerOptions(confidenceThreshold: _confidenceThreshold),
  );

  /// 이미지 파일에서 태그 key 목록을 반환
  static Future<List<String>> suggestTags(
    String imagePath,
    MediaSpace space,
  ) async {
    try {
      final inputImage = InputImage.fromFilePath(imagePath);
      final labels = await _labeler
          .processImage(inputImage)
          .timeout(const Duration(seconds: 10));

      final tagKeys = <String>{};
      for (final label in labels) {
        final key = _labelMap[label.label];
        if (key != null) tagKeys.add(key);
      }
      return tagKeys.toList();
    } on TimeoutException {
      return [];
    } catch (_) {
      return [];
    }
  }

  /// 문서 파일 → 'document' 태그 자동 적용
  static List<String> suggestForDocument() => ['document'];

  static void close() => _labeler.close();
}
