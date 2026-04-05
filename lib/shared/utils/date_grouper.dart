import 'package:intl/intl.dart';
import '../models/media_item.dart';

/// 미디어 목록을 월 단위로 그룹핑한 결과
/// [entries] = (label, items) 쌍의 순서 보장 리스트
class DateGroup {
  final String label;   // "2025년 3월"
  final List<MediaItem> items;
  const DateGroup({required this.label, required this.items});
}

class DateGrouper {
  static final _fmt = DateFormat('yyyy년 M월');

  static List<DateGroup> group(List<MediaItem> items) {
    if (items.isEmpty) return [];

    final map = <String, List<MediaItem>>{};
    for (final item in items) {
      final dt = DateTime.fromMillisecondsSinceEpoch(item.takenAt);
      final key = _fmt.format(dt);
      (map[key] ??= []).add(item);
    }
    return map.entries
        .map((e) => DateGroup(label: e.key, items: e.value))
        .toList();
  }
}
