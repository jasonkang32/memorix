import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/shared/models/media_item.dart';

void main() {
  group('MediaItem', () {
    final base = MediaItem(
      filePath: '/memorix/photos/2025/01/abc.jpg',
      mediaType: MediaType.photo,
      space: MediaSpace.work,
      takenAt: 1700000000000,
      createdAt: 1700000000000,
      title: '출장 사진',
      note: '서울 미팅',
      countryCode: 'KR',
      region: '서울',
    );

    test('copyWith updates only specified fields', () {
      final updated = base.copyWith(title: '수정된 제목', region: '부산');
      expect(updated.title, '수정된 제목');
      expect(updated.region, '부산');
      // 나머지는 유지
      expect(updated.countryCode, 'KR');
      expect(updated.space, MediaSpace.work);
      expect(updated.filePath, base.filePath);
    });

    test('toMap / fromMap roundtrip', () {
      final map = base.toMap();
      final restored = MediaItem.fromMap(map);
      expect(restored.filePath, base.filePath);
      expect(restored.mediaType, base.mediaType);
      expect(restored.space, base.space);
      expect(restored.takenAt, base.takenAt);
      expect(restored.title, base.title);
      expect(restored.note, base.note);
      expect(restored.countryCode, base.countryCode);
      expect(restored.region, base.region);
    });

    test('space serialized as name string', () {
      final map = base.toMap();
      expect(map['space'], 'work');
    });

    test('personal space serializes correctly', () {
      final personal = base.copyWith(space: MediaSpace.personal);
      expect(personal.toMap()['space'], 'personal');
    });
  });
}
