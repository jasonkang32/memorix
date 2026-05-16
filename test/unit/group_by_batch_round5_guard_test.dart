import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/shared/models/media_item.dart';

/// QA Round 5 회귀 가드: _groupByBatch는 같은 batchId를 무조건 한 그룹으로 묶고,
/// 그룹 내부는 createdAt ASC로 정렬해야 한다.
///
/// _groupByBatch는 _MediaTimelineState의 private 메서드라 직접 호출 불가.
/// 동등 로직을 여기서 재현해 검증 — 실제 위젯 동작이 이와 일치하도록 source-grep
/// 가드도 함께 있다 (timeline_card_meta_guards 등).
///
/// 핵심 invariant:
/// 1. 같은 batchId는 무조건 한 그룹 (인접 안 해도 OK)
/// 2. 빈 batchId는 각자 독립 그룹
/// 3. 그룹 내부 createdAt ASC (오래된 → 최신)
///
/// 실제 _groupByBatch 코드를 source-grep으로 확인.
void main() {
  group('_groupByBatch (Round 5)', () {
    test('같은 batchId면 인접 아니어도 한 그룹', () {
      final src = [
        _item(id: 1, batchId: 'A', createdAt: 300),
        _item(id: 2, batchId: 'B', createdAt: 200), // 사이에 다른 batch
        _item(id: 3, batchId: 'A', createdAt: 100),
      ];

      final groups = _groupByBatch(src);

      expect(groups.length, 2, reason: 'A 그룹 + B 그룹 = 2');
      final groupA = groups.firstWhere((g) => g.first.batchId == 'A');
      expect(groupA.length, 2, reason: 'A 항목 두 개가 같은 그룹');
      expect(groupA.map((i) => i.id).toSet(), {1, 3});
    });

    test('빈 batchId는 각자 독립 그룹', () {
      final src = [
        _item(id: 1, batchId: '', createdAt: 200),
        _item(id: 2, batchId: '', createdAt: 100),
      ];

      final groups = _groupByBatch(src);
      expect(groups.length, 2, reason: '빈 batchId는 묶이지 않음');
    });

    test('그룹 내부 createdAt ASC (오래된 먼저)', () {
      final src = [
        _item(id: 1, batchId: 'X', createdAt: 300), // 최신
        _item(id: 2, batchId: 'X', createdAt: 100), // 가장 오래됨
        _item(id: 3, batchId: 'X', createdAt: 200),
      ];

      final groups = _groupByBatch(src);
      expect(groups.length, 1);
      final order = groups.first.map((i) => i.id).toList();
      expect(order, [2, 3, 1], reason: 'createdAt ASC 정렬 — 오래된 → 최신');
    });

    test('빈 그룹과 비빈 그룹 혼재', () {
      final src = [
        _item(id: 1, batchId: 'A', createdAt: 100),
        _item(id: 2, batchId: '', createdAt: 90),
        _item(id: 3, batchId: 'A', createdAt: 80),
        _item(id: 4, batchId: '', createdAt: 70),
      ];

      final groups = _groupByBatch(src);
      expect(groups.length, 3, reason: 'A + 빈 + 빈');
    });
  });
}

/// _MediaTimelineState._groupByBatch와 동일 로직 (테스트용 복제).
List<List<MediaItem>> _groupByBatch(List<MediaItem> src) {
  final groups = <List<MediaItem>>[];
  final byBatch = <String, List<MediaItem>>{};

  for (final item in src) {
    if (item.batchId.isEmpty) {
      groups.add([item]);
    } else {
      final existing = byBatch[item.batchId];
      if (existing != null) {
        existing.add(item);
      } else {
        final group = <MediaItem>[item];
        byBatch[item.batchId] = group;
        groups.add(group);
      }
    }
  }

  for (final group in groups) {
    group.sort((a, b) => a.createdAt.compareTo(b.createdAt));
  }

  return groups;
}

MediaItem _item({
  required int id,
  required String batchId,
  required int createdAt,
}) {
  return MediaItem(
    id: id,
    space: MediaSpace.work,
    mediaType: MediaType.photo,
    filePath: '/tmp/$id.jpg',
    takenAt: createdAt,
    createdAt: createdAt,
    batchId: batchId,
  );
}
