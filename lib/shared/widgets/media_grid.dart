import 'package:flutter/material.dart';
import '../models/media_item.dart';
import '../utils/date_grouper.dart';
import 'media_thumbnail.dart';

/// 날짜별 섹션 헤더가 있는 미디어 그리드
class MediaGrid extends StatelessWidget {
  final List<MediaItem> items;
  final void Function(List<MediaItem> items, int index) onTap;
  final void Function(MediaItem item) onLongPress;
  final EdgeInsets padding;
  final Future<void> Function()? onRefresh;

  const MediaGrid({
    super.key,
    required this.items,
    required this.onTap,
    required this.onLongPress,
    this.padding = const EdgeInsets.fromLTRB(0, 0, 0, 80),
    this.onRefresh,
  });

  @override
  Widget build(BuildContext context) {
    final groups = DateGrouper.group(items);

    final scrollView = CustomScrollView(
      physics: const AlwaysScrollableScrollPhysics(),
      slivers: [
        for (final group in groups) ...[
          SliverToBoxAdapter(
            child: _SectionHeader(
              label: group.label,
              count: group.items.length,
            ),
          ),
          SliverPadding(
            padding: EdgeInsets.fromLTRB(padding.left, 0, padding.right, 2),
            sliver: SliverGrid(
              delegate: SliverChildBuilderDelegate((context, index) {
                final item = group.items[index];
                // 전체 items 기준 index 계산 (뷰어에 전달)
                final globalIndex = items.indexOf(item);
                return MediaThumbnailCard(
                  item: item,
                  onTap: () => onTap(items, globalIndex),
                  onLongPress: () => onLongPress(item),
                );
              }, childCount: group.items.length),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 3,
                crossAxisSpacing: 2,
                mainAxisSpacing: 2,
                childAspectRatio: 1.0, // 정사각형
              ),
            ),
          ),
        ],
        SliverPadding(padding: EdgeInsets.only(bottom: padding.bottom)),
      ],
    );

    if (onRefresh != null) {
      return RefreshIndicator(
        onRefresh: onRefresh!,
        color: const Color(0xFF00C896),
        child: scrollView,
      );
    }
    return scrollView;
  }
}

class _SectionHeader extends StatelessWidget {
  final String label;
  final int count;
  const _SectionHeader({required this.label, required this.count});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 20, 12, 8),
      child: Row(
        children: [
          Text(
            label,
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w700,
              color: isDark ? Colors.white70 : const Color(0xFF1A1F2E),
              letterSpacing: -0.2,
            ),
          ),
          const SizedBox(width: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
            decoration: BoxDecoration(
              color: const Color(0xFF00C896).withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Text(
              '$count',
              style: const TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w700,
                color: Color(0xFF00C896),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
