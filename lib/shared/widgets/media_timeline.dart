import 'dart:io';
import 'dart:math' as math;
import 'dart:ui';
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

  /// 카드 헤더의 자물쇠 버튼 콜백 (Phase 3C — per-card 잠금 토글).
  /// 호출처는 인증 + DB 갱신 + provider invalidate를 책임진다
  /// (`handleLockToggle` 사용 권장).
  final Future<void> Function(MediaItem item)? onLockToggle;

  const MediaTimeline({
    super.key,
    required this.items,
    required this.onTap,
    required this.onLongPress,
    this.padding = const EdgeInsets.fromLTRB(0, 0, 0, 80),
    this.onRefresh,
    this.showSpaceBadge = false,
    this.onLockToggle,
  });

  // 같은 batch_id 항목을 하나의 카드 그룹으로 묶음.
  //
  // 이전 구현은 "인접 같은 batchId만" 그룹화 — DB created_at DESC 정렬에서
  // 같은 batchId의 row 사이에 다른 batchId의 row가 끼면 두 그룹으로 분리되어
  // 같은 work가 두 카드로 나뉘는 버그가 있었다 (QA Round 5).
  //
  // 이제는 전체 src를 한 번 훑어 같은 batchId면 무조건 한 그룹.
  // 그룹 위치는 그 batchId의 첫 등장 순서 (= 가장 최근 createdAt 위치).
  // 그룹 내 정렬은 createdAt ASC — 오래된 사진 먼저, 새로 추가한 사진이 마지막.
  // 사용자 의도("최하단에 하나씩 붙임")와 일치.
  List<List<MediaItem>> _groupByBatch(List<MediaItem> src) {
    final groups = <List<MediaItem>>[];
    final byBatch = <String, List<MediaItem>>{};

    for (final item in src) {
      if (item.batchId.isEmpty) {
        // 빈 batchId는 각자 독립 그룹.
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

    // 각 그룹 내부를 createdAt ASC로 정렬 (오래된 → 최신).
    for (final group in groups) {
      group.sort((a, b) => a.createdAt.compareTo(b.createdAt));
    }

    return groups;
  }

  static final _dateFmt = DateFormat('M월 d일 (E)', 'ko');

  // 섹션 키는 createdAt(등록일) 기반. takenAt(EXIF)이면 옛날 사진을 가져왔을 때
  // 과거 섹션에 묻혀 사용자가 "추가가 안 됨"으로 인식한다 (Bug #3 회귀 가드).
  // memorix는 보관함이라 "내가 기록한 시간" 기준이 자연스럽다.
  String _dateKey(MediaItem item) => DateFormat(
    'yyyy-MM-dd',
  ).format(DateTime.fromMillisecondsSinceEpoch(item.createdAt));

  @override
  Widget build(BuildContext context) {
    final groups = _groupByBatch(items);

    // 날짜별 섹션 구성
    final sections = <String, List<List<MediaItem>>>{};
    for (final group in groups) {
      final key = _dateKey(group.first);
      sections.putIfAbsent(key, () => []).add(group);
    }

    // 섹션 내 그룹을 createdAt 내림차순 정렬 (최신 등록 항목이 상단)
    for (final key in sections.keys) {
      sections[key]!.sort(
        (a, b) => b
            .map((i) => i.createdAt)
            .reduce(math.max)
            .compareTo(a.map((i) => i.createdAt).reduce(math.max)),
      );
    }

    // 섹션을 "가장 최근에 Memorix에 등록된" 기준으로 정렬 (EXIF 촬영일 기준 X)
    final sectionMaxCreatedAt = <String, int>{};
    for (final entry in sections.entries) {
      sectionMaxCreatedAt[entry.key] = entry.value
          .expand((group) => group)
          .map((item) => item.createdAt)
          .reduce(math.max);
    }
    final sectionKeys = sections.keys.toList()
      ..sort(
        (a, b) => (sectionMaxCreatedAt[b] ?? 0).compareTo(
          sectionMaxCreatedAt[a] ?? 0,
        ),
      );

    final scrollView = CustomScrollView(
      physics: const AlwaysScrollableScrollPhysics(),
      slivers: [
        for (final dateKey in sectionKeys) ...[
          SliverToBoxAdapter(
            child: _DateHeader(label: _dateFmt.format(DateTime.parse(dateKey))),
          ),
          SliverList(
            delegate: SliverChildBuilderDelegate((context, i) {
              final group = sections[dateKey]![i];
              return _TimelineCard(
                group: group,
                allItems: items,
                onTap: onTap,
                onLongPress: onLongPress,
                showSpaceBadge: showSpaceBadge,
                onLockToggle: onLockToggle,
              );
            }, childCount: sections[dateKey]!.length),
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
  final Future<void> Function(MediaItem item)? onLockToggle;

  const _TimelineCard({
    required this.group,
    required this.allItems,
    required this.onTap,
    required this.onLongPress,
    this.showSpaceBadge = false,
    this.onLockToggle,
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
    BuildContext context,
    MediaItem item,
    bool isWork,
    int count,
  ) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final time = _dtFmt.format(
      DateTime.fromMillisecondsSinceEpoch(item.takenAt),
    );
    final location = [
      item.countryCode,
      item.region,
    ].where((s) => s.isNotEmpty).join('  ');
    final isLocked = item.isLocked == 1;

    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 4),
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
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            const SizedBox(width: 8),
          ],
          Text(
            time,
            style: const TextStyle(fontSize: 12, color: Color(0xFF555566)),
          ),
          if (location.isNotEmpty) ...[
            const SizedBox(width: 6),
            Icon(
              Icons.location_on_outlined,
              size: 12,
              color: Theme.of(
                context,
              ).colorScheme.onSurface.withValues(alpha: 0.6),
            ),
            const SizedBox(width: 2),
            Expanded(
              child: Text(
                location,
                style: TextStyle(
                  fontSize: 12,
                  color: Theme.of(
                    context,
                  ).colorScheme.onSurface.withValues(alpha: 0.6),
                ),
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
                  const Icon(
                    Icons.collections_outlined,
                    size: 10,
                    color: Colors.white,
                  ),
                  const SizedBox(width: 3),
                  Text(
                    '$count',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
            ),
          // 동기화 상태 아이콘 (그룹 단위 — 자물쇠 토글 왼쪽)
          ..._buildSyncStatusIcon(context),
          // 자물쇠 토글 버튼 (Phase 3C — per-card 잠금)
          if (widget.onLockToggle != null) ...[
            const SizedBox(width: 4),
            IconButton(
              iconSize: 20,
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
              visualDensity: VisualDensity.compact,
              icon: Icon(
                isLocked ? Icons.lock_rounded : Icons.lock_open_rounded,
                color: isLocked
                    ? Colors.amber
                    : (isDark ? Colors.white60 : Colors.black54),
              ),
              tooltip: isLocked ? '잠금 해제' : '잠금',
              onPressed: () async {
                await widget.onLockToggle?.call(item);
              },
            ),
          ],
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
          height: height,
          width: double.infinity,
          child: _MediaImage(item: widget.group[0]),
        ),
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
              child: SizedBox.expand(child: _MediaImage(item: widget.group[0])),
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

  /// 그룹 단위 동기화 상태 아이콘 — 헤더의 자물쇠 토글 왼쪽에 배치.
  /// - 일부라도 미동기화: cloud_upload_outlined (warning 색)
  /// - 전부 동기화 완료: cloud_done_outlined (mutedText)
  /// - 그 외 (예: 빈 그룹): 표시 없음
  List<Widget> _buildSyncStatusIcon(BuildContext context) {
    final allSynced = widget.group.every((m) => m.driveSynced == 1);
    final hasPending = widget.group.any((m) => m.driveSynced == 0);

    if (hasPending) {
      return [
        const Padding(
          padding: EdgeInsets.only(left: 4, right: 0),
          child: Tooltip(
            message: '동기화 대기',
            child: Icon(
              Icons.cloud_upload_outlined,
              size: 18,
              color: Color(0xFFFFB800),
            ),
          ),
        ),
      ];
    }
    if (allSynced) {
      return [
        Padding(
          padding: const EdgeInsets.only(left: 4, right: 0),
          child: Tooltip(
            message: '동기화 완료',
            child: Icon(
              Icons.cloud_done_outlined,
              size: 18,
              color: Theme.of(
                context,
              ).colorScheme.onSurface.withValues(alpha: 0.5),
            ),
          ),
        ),
      ];
    }
    return const [];
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
                  children:
                      (List.of(tags)
                            ..sort((a, b) => (b.id ?? 0).compareTo(a.id ?? 0)))
                          .map((tag) => _TagChipDisplay(label: tag.label))
                          .toList(),
                ),
              ),
            ),
            const SizedBox(height: 8),
          ],

          // ── 메모 (주요 콘텐츠) ──
          if (hasNote)
            Text(
              note,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w500,
                color: isDark
                    ? Colors.white.withValues(alpha: 0.87)
                    : const Color(0xFF1A2030),
                height: 1.5,
              ),
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

  // 타임라인 카드 이미지(폭 ~화면 절반~전체) 디코딩 캐시 폭. retina 대응 ~2배.
  static const int _gridDecodeSize = 600;

  @override
  Widget build(BuildContext context) {
    if (item.mediaType == MediaType.document) {
      return _wrapLock(
        Container(
          color: Colors.blue[50],
          child: const Center(
            child: Icon(Icons.description, size: 56, color: Colors.blueGrey),
          ),
        ),
      );
    }

    // 영상: 썸네일 + play 오버레이 (mp4는 Image.file에 못 씀)
    if (item.mediaType == MediaType.video) {
      final thumb = item.thumbPath;
      return _wrapLock(
        Stack(
          fit: StackFit.expand,
          children: [
            if (thumb != null && File(thumb).existsSync())
              Image.file(
                File(thumb),
                fit: BoxFit.cover,
                cacheWidth: _gridDecodeSize,
                cacheHeight: _gridDecodeSize,
                filterQuality: FilterQuality.medium,
              )
            else
              Container(
                color: Theme.of(context).colorScheme.surfaceContainerLowest,
              ),
            const Center(
              child: Icon(
                Icons.play_circle_fill,
                color: Colors.white70,
                size: 56,
              ),
            ),
          ],
        ),
      );
    }

    // 사진: 원본 filePath + cacheWidth 다운샘플 (Bug #1 회귀 방지).
    // 압축된 thumbPath를 stretch하면 화질이 저하되므로 원본을 디코더 단계에서
    // 다운샘플한다. 메모리 효율 + 화질 보존.
    if (File(item.filePath).existsSync()) {
      return _wrapLock(
        Image.file(
          File(item.filePath),
          fit: BoxFit.cover,
          width: double.infinity,
          height: double.infinity,
          cacheWidth: _gridDecodeSize,
          cacheHeight: _gridDecodeSize,
          filterQuality: FilterQuality.medium,
        ),
      );
    }
    // 원본이 없을 때만 thumbPath fallback.
    final thumb = item.thumbPath;
    if (thumb != null && File(thumb).existsSync()) {
      return _wrapLock(
        Image.file(
          File(thumb),
          fit: BoxFit.cover,
          width: double.infinity,
          height: double.infinity,
          cacheWidth: _gridDecodeSize,
          cacheHeight: _gridDecodeSize,
          filterQuality: FilterQuality.medium,
        ),
      );
    }
    return _wrapLock(
      Container(color: Theme.of(context).colorScheme.surfaceContainerHighest),
    );
  }

  /// 잠긴 항목(`isLocked == 1`)은 그리드/타임라인에서 블러 + 자물쇠로 가려진다.
  /// 인증 후 풀스크린(`media_viewer_screen`)에서만 선명하게 표시되어야 한다.
  Widget _wrapLock(Widget child) {
    if (item.isLocked != 1) return child;
    return Stack(
      fit: StackFit.expand,
      children: [
        child,
        ClipRect(
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
            child: Container(color: Colors.black.withValues(alpha: 0.25)),
          ),
        ),
        const Center(
          child: Icon(Icons.lock_rounded, size: 36, color: Colors.white),
        ),
      ],
    );
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
