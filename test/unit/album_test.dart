import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/shared/models/album.dart';

void main() {
  group('Album', () {
    final base = Album(
      title: '제주 여행',
      eventType: EventType.travel,
      createdAt: 1700000000000,
    );

    test('toMap / fromMap roundtrip', () {
      final map = base.toMap();
      final restored = Album.fromMap(map);
      expect(restored.title, base.title);
      expect(restored.eventType, base.eventType);
      expect(restored.createdAt, base.createdAt);
    });

    test('eventType serialized as name string', () {
      final map = base.toMap();
      expect(map['event_type'], 'travel');
    });

    test('all EventType values serialize and deserialize', () {
      for (final type in EventType.values) {
        final a = Album(title: 'test', eventType: type, createdAt: 0);
        final restored = Album.fromMap(a.toMap());
        expect(restored.eventType, type);
      }
    });

    test('optional fields have default values', () {
      expect(base.id, isNull);
      expect(base.coverMediaId, isNull);
      expect(base.memo, '');       // 기본값 빈 문자열
      expect(base.dateStart, isNull);
      expect(base.dateEnd, isNull);
    });
  });
}
