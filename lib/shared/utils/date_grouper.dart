import 'package:intl/intl.dart';
import '../models/media_item.dart';

/// 미디어 목록을 월 단위로 그룹핑한 결과
/// [entries] = (label, items) 쌍의 순서 보장 리스트
class DateGroup {
  final String label; // "2025년 3월"
  final List<MediaItem> items;
  const DateGroup({required this.label, required this.items});
}

class DateGrouper {
  static final _fmt = DateFormat('yyyy년 M월');

  /// 섹션 키는 createdAt(등록일) 기반.
  /// takenAt(EXIF)이면 옛날 사진을 가져왔을 때 과거 섹션에 묻혀 사용자가
  /// "추가가 안 됨"으로 인식한다 (Bug #3 회귀 가드).
  /// memorix는 보관함이라 "내가 기록한 시간" 기준이 자연스럽다.
  static List<DateGroup> group(List<MediaItem> items) {
    if (items.isEmpty) return [];

    final map = <String, List<MediaItem>>{};
    for (final item in items) {
      final dt = DateTime.fromMillisecondsSinceEpoch(item.createdAt);
      final key = _fmt.format(dt);
      (map[key] ??= []).add(item);
    }
    // 섹션 자체도 createdAt 최신 순 정렬 (각 그룹의 max createdAt 기준).
    final entries = map.entries.toList()
      ..sort((a, b) {
        final maxA = a.value
            .map((i) => i.createdAt)
            .reduce((x, y) => x > y ? x : y);
        final maxB = b.value
            .map((i) => i.createdAt)
            .reduce((x, y) => x > y ? x : y);
        return maxB.compareTo(maxA);
      });
    return entries
        .map((e) => DateGroup(label: e.key, items: e.value))
        .toList();
  }
}
