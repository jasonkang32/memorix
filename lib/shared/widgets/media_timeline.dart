import 'dart:io';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../core/db/tag_dao.dart';
import '../models/media_item.dart';
import '../models/tag.dart';

/// SNS 타임라인 형태의 미디어 목록
/// - 같은 batch_id 항목 → 하나의 카드 (캐러셀)
/// - 날짜 섹션 헤더
class MediaTimeline extends StatelessWidget {
  final List<MediaItem> items;
  final void Function(List<MediaItem> group, int indexInGroup) onTap;
  final void Function(MediaItem item) onLongPress;
  final EdgeInsets padding;
  final Future<void> Function()? onRefresh;
  final bool showSpaceBadge;

  const MediaTimeline({
    super.key,
    required this.items,
    required this.onTap,
    required this.onLongPress,
    this.padding = const EdgeInsets.fromLTRB(0, 0, 0, 80),
    this.onRefresh,
    this.showSpaceBadge = false,
  });

  // 연속된 같은 batch_id 항목을 하나의 카드 그룹으로 묶음
  List<List<MediaItem>> _groupByBatch(List<MediaItem> src) {
    final groups = <List<MediaItem>>[];
    for (final item in src) {
      if (item.batchId.isNotEmpty &&
          groups.isNotEmpty &&
          groups.last.first.batchId == item.batchId) {
        groups.last.add(item);
      } else {
        groups.add([item]);
      }
    }
    return groups;
  }

  static final _dateFmt = DateFormat('M월 d일 (E)', 'ko');

  String _dateKey(MediaItem item) =>
      DateFormat('yyyy-MM-dd').format(
          DateTime.fromMillisecondsSinceEpoch(item.takenAt));

  @override
  Widget build(BuildContext context) {
    final groups = _groupByBatch(items);

    // 날짜별 섹션 구성
    final sections = <String, List<List<MediaItem>>>{};
    for (final group in groups) {
      final key = _dateKey(group.first);
      sections.putIfAbsent(key, () => []).add(group);
    }

    final sectionKeys = sections.keys.toList()..sort((a, b) => b.compareTo(a));

    final scrollView = CustomScrollView(
      physics: const AlwaysScrollableScrollPhysics(),
      slivers: [
        for (final dateKey in sectionKeys) ...[
          SliverToBoxAdapter(
            child: _DateHeader(
              label: _dateFmt.format(DateTime.parse(dateKey)),
            ),
          ),
          SliverList(
            delegate: SliverChildBuilderDelegate(
              (context, i) {
                final group = sections[dateKey]![i];
                return _TimelineCard(
                  group: group,
                  allItems: items,
                  onTap: onTap,
                  onLongPress: onLongPress,
                  showSpaceBadge: showSpaceBadge,
                );
              },
              childCount: sections[dateKey]!.length,
            ),
          ),
        ],
        SliverPadding(
          padding: EdgeInsets.only(
            bottom: MediaQuery.of(context).padding.bottom + 16,
          ),
        ),
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

// ── 날짜 헤더 ────────────────────────────────────────────────

class _DateHeader extends StatelessWidget {
  final String label;
  const _DateHeader({required this.label});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 4),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 13,
          fontWeight: FontWeight.w700,
          color: isDark ? Colors.white70 : const Color(0xFF1A1F2E),
          letterSpacing: -0.2,
        ),
      ),
    );
  }
}

// ── 타임라인 카드 ─────────────────────────────────────────────

class _TimelineCard extends StatefulWidget {
  final List<MediaItem> group;
  final List<MediaItem> allItems;
  final void Function(List<MediaItem> group, int indexInGroup) onTap;
  final void Function(MediaItem) onLongPress;
  final bool showSpaceBadge;

  const _TimelineCard({
    required this.group,
    required this.allItems,
    required this.onTap,
    required this.onLongPress,
    this.showSpaceBadge = false,
  });

  @override
  State<_TimelineCard> createState() => _TimelineCardState();
}

class _TimelineCardState extends State<_TimelineCard> {
  List<Tag>? _tags;

  @override
  void initState() {
    super.initState();
    _loadTags();
  }

  /// 그룹 내 모든 아이템의 태그를 합쳐서 중복 제거 후 로드
  Future<void> _loadTags() async {
    final dao = TagDao();
    final merged = <Tag>[];
    final seen = <int>{};
    for (final item in widget.group) {
      if (item.id == null) continue;
      for (final tag in await dao.findByMediaId(item.id!)) {
        if (tag.id != null && seen.add(tag.id!)) merged.add(tag);
      }
    }
    if (mounted) setState(() => _tags = merged);
  }

  static final _dtFmt = DateFormat('yyyy.MM.dd  HH:mm:ss');

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final first = widget.group.first;
    final isWork = first.space == MediaSpace.work;
    final count = widget.group.length;

    return GestureDetector(
      onLongPress: () => widget.onLongPress(first),
      child: Container(
        margin: const EdgeInsets.fromLTRB(12, 0, 12, 12),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF161B22) : Colors.white,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: isDark ? 0.2 : 0.06),
              blurRadius: 12,
              offset: const Offset(0, 3),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // ── 카드 헤더 ──
            _buildHeader(context, first, isWork, count),

            // ── 이미지 영역 ──
            ClipRRect(
              borderRadius: const BorderRadius.vertical(top: Radius.zero),
              child: _buildImageArea(count),
            ),

            // ── 하단 정보 (탭 → 상세보기) ──
            GestureDetector(
              onTap: () => widget.onTap(widget.group, 0),
              child: _buildFooter(context, first, isDark),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(
      BuildContext context, MediaItem item, bool isWork, int count) {
    final time = _dtFmt.format(DateTime.fromMillisecondsSinceEpoch(item.takenAt));
    final location = [item.countryCode, item.region]
        .where((s) => s.isNotEmpty)
        .join('  ');

    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 8),
      child: Row(
        children: [
          // Space badge (검색 등 혼합 목록에서만 표시)
          if (widget.showSpaceBadge) ...[
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: isWork
                      ? [const Color(0xFF1A73E8), const Color(0xFF00C896)]
                      : [const Color(0xFFFF6B9D), const Color(0xFF7B61FF)],
                ),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                isWork ? 'Work' : 'Personal',
                style: const TextStyle(
                    color: Colors.white,
                    fontSize: 10,
                    fontWeight: FontWeight.w700),
              ),
            ),
            const SizedBox(width: 8),
          ],
          Text(time,
              style: const TextStyle(fontSize: 12, color: Color(0xFF555566))),
          if (location.isNotEmpty) ...[
            const SizedBox(width: 6),
            const Icon(Icons.location_on_outlined,
                size: 12, color: Colors.grey),
            const SizedBox(width: 2),
            Expanded(
              child: Text(
                location,
                style: const TextStyle(fontSize: 12, color: Colors.grey),
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ] else
            const Spacer(),
          // 여러 장 배지
          if (count > 1)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: Colors.black87,
                borderRadius: BorderRadius.circular(6),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.collections_outlined,
                      size: 10, color: Colors.white),
                  const SizedBox(width: 3),
                  Text(
                    '$count',
                    style: const TextStyle(
                        color: Colors.white,
                        fontSize: 10,
                        fontWeight: FontWeight.w700),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  /// 1장: 단독 풀 너비
  /// 2장: 좌우 50/50
  /// 3장 이상: 왼쪽 1장 + 오른쪽 2장 세로 배열 (최대 3장 표시, 초과분은 숫자 오버레이)
  Widget _buildImageArea(int count) {
    const height = 280.0;
    const gap = 2.0;

    if (count == 1) {
      return GestureDetector(
        onTap: () => widget.onTap(widget.group, 0),
        child: SizedBox(
            height: height, width: double.infinity, child: _MediaImage(item: widget.group[0])),
      );
    }

    if (count == 2) {
      return SizedBox(
        height: height,
        child: Row(
          children: List.generate(2, (i) {
            return Expanded(
              child: GestureDetector(
                onTap: () => widget.onTap(widget.group, i),
                child: Container(
                  margin: EdgeInsets.only(left: i == 1 ? gap : 0),
                  child: _MediaImage(item: widget.group[i]),
                ),
              ),
            );
          }),
        ),
      );
    }

    // 3장 이상: left + right-stack
    final rightItems = widget.group.skip(1).take(2).toList();
    final extra = count - 3; // 3장 초과분

    return SizedBox(
      height: height,
      child: Row(
        children: [
          // 왼쪽 — 큰 이미지 (full height)
          Expanded(
            child: GestureDetector(
              onTap: () => widget.onTap(widget.group, 0),
              child: SizedBox.expand(
                child: _MediaImage(item: widget.group[0]),
              ),
            ),
          ),
          const SizedBox(width: gap),
          // 오른쪽 — 2장 세로 배열
          Expanded(
            child: Column(
              children: List.generate(rightItems.length, (i) {
                final item = rightItems[i];
                final isLast = i == rightItems.length - 1;
                return Expanded(
                  child: Container(
                    margin: EdgeInsets.only(top: i == 0 ? 0 : gap),
                    child: GestureDetector(
                      onTap: () => widget.onTap(widget.group, i + 1),
                      child: Stack(
                        fit: StackFit.expand,
                        children: [
                          _MediaImage(item: item),
                          // 마지막 칸에 초과 개수 오버레이
                          if (isLast && extra > 0)
                            Container(
                              color: Colors.black54,
                              child: Center(
                                child: Text(
                                  '+$extra',
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 22,
                                    fontWeight: FontWeight.w800,
                                  ),
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
                );
              }),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFooter(BuildContext context, MediaItem item, bool isDark) {
    final tags = _tags ?? [];
    final note = item.note.trim();
    final hasNote = note.isNotEmpty;

    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // ── 태그 (최근 등록 순, 최대 2라인) ──
          if (tags.isNotEmpty) ...[
            ClipRect(
              child: SizedBox(
                height: 52,
                child: Wrap(
                  spacing: 5,
                  runSpacing: 4,
                  children: (List.of(tags)
                        ..sort((a, b) => (b.id ?? 0).compareTo(a.id ?? 0)))
                      .map((tag) => _TagChipDisplay(label: tag.label))
                      .toList(),
                ),
              ),
            ),
            const SizedBox(height: 8),
          ],

          // ── 메모 / 내용없음 (항상 표시) ──
          Text(
            hasNote ? note : '내용없음',
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 13,
              color: hasNote
                  ? (isDark ? Colors.white70 : const Color(0xFF2D3748))
                  : Colors.grey,
              height: 1.4,
              fontStyle: hasNote ? FontStyle.normal : FontStyle.italic,
            ),
          ),

          const SizedBox(height: 8),

          // ── 타입 배지 + 동기화 상태 ──
          Row(
            children: [
              _TypeBadge(type: item.mediaType),
              if (item.driveSynced == 0) ...[
                const SizedBox(width: 10),
                const Icon(Icons.cloud_upload_outlined,
                    size: 13, color: Colors.grey),
                const SizedBox(width: 3),
                const Text('동기화 대기',
                    style: TextStyle(fontSize: 11, color: Colors.grey)),
              ],
            ],
          ),
        ],
      ),
    );
  }

}

// ── 미디어 이미지 렌더러 ─────────────────────────────────────

class _MediaImage extends StatelessWidget {
  final MediaItem item;
  const _MediaImage({required this.item});

  @override
  Widget build(BuildContext context) {
    if (item.mediaType == MediaType.document) {
      return Container(
        color: Colors.blue[50],
        child: const Center(
          child: Icon(Icons.description, size: 56, color: Colors.blueGrey),
        ),
      );
    }

    // 영상: 썸네일 + play 오버레이
    if (item.mediaType == MediaType.video) {
      final thumb = item.thumbPath;
      return Stack(
        fit: StackFit.expand,
        children: [
          if (thumb != null && File(thumb).existsSync())
            Image.file(File(thumb), fit: BoxFit.cover)
          else
            Container(color: Colors.grey[900]),
          const Center(
            child: Icon(Icons.play_circle_fill,
                color: Colors.white70, size: 56),
          ),
        ],
      );
    }

    // 사진
    final thumb = item.thumbPath;
    if (thumb != null && File(thumb).existsSync()) {
      return Image.file(File(thumb), fit: BoxFit.cover,
          width: double.infinity, height: double.infinity);
    }
    if (File(item.filePath).existsSync()) {
      return Image.file(File(item.filePath), fit: BoxFit.cover,
          width: double.infinity, height: double.infinity);
    }
    return Container(color: Colors.grey[200]);
  }
}

// ── 태그 칩 (통일 색상) ──────────────────────────────────────

class _TagChipDisplay extends StatelessWidget {
  final String label;
  const _TagChipDisplay({required this.label});

  static const _color = Color(0xFF00C896);
  static const _textColor = Color(0xFF005C42); // 진한 다크그린

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: _color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: _color, width: 1.2),
      ),
      child: Text(
        '#$label',
        style: const TextStyle(
          fontSize: 11,
          color: _textColor,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

// ── 미디어 타입 배지 ─────────────────────────────────────────

class _TypeBadge extends StatelessWidget {
  final MediaType type;
  const _TypeBadge({required this.type});

  @override
  Widget build(BuildContext context) {
    final (icon, label, color) = switch (type) {
      MediaType.photo => (Icons.photo_outlined, '사진', const Color(0xFF1A73E8)),
      MediaType.video => (Icons.videocam_outlined, '영상', const Color(0xFFFF6B9D)),
      MediaType.document => (Icons.description_outlined, '문서', const Color(0xFFFFB800)),
    };
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 13, color: color),
        const SizedBox(width: 3),
        Text(label,
            style: TextStyle(
                fontSize: 11, color: color, fontWeight: FontWeight.w600)),
      ],
    );
  }
}
