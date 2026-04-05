import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/shared/models/media_item.dart';
import 'package:memorix/shared/utils/date_grouper.dart';

void main() {
  MediaItem makeItem(int year, int month, int day) => MediaItem(
        filePath: '/test/$year-$month-$day.jpg',
        mediaType: MediaType.photo,
        space: MediaSpace.work,
        takenAt: DateTime(year, month, day).millisecondsSinceEpoch,
        createdAt: DateTime(year, month, day).millisecondsSinceEpoch,
      );

  group('DateGrouper', () {
    test('returns empty list for empty input', () {
      expect(DateGrouper.group([]), isEmpty);
    });

    test('groups items by month', () {
      final items = [
        makeItem(2025, 3, 1),
        makeItem(2025, 3, 15),
        makeItem(2025, 4, 1),
      ];
      final groups = DateGrouper.group(items);
      expect(groups.length, 2);
      expect(groups[0].items.length, 2);
      expect(groups[1].items.length, 1);
    });

    test('label format is "yyyy년 M월"', () {
      final items = [makeItem(2025, 3, 10)];
      final groups = DateGrouper.group(items);
      expect(groups.first.label, '2025년 3월');
    });

    test('same month items are in same group', () {
      final items = List.generate(
        5,
        (i) => makeItem(2024, 12, i + 1),
      );
      final groups = DateGrouper.group(items);
      expect(groups.length, 1);
      expect(groups.first.items.length, 5);
    });

    test('maintains insertion order across months', () {
      final items = [
        makeItem(2025, 1, 1),
        makeItem(2025, 2, 1),
        makeItem(2025, 3, 1),
      ];
      final groups = DateGrouper.group(items);
      expect(groups.length, 3);
      expect(groups[0].label, '2025년 1월');
      expect(groups[2].label, '2025년 3월');
    });
  });
}
