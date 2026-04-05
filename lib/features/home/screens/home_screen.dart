import 'dart:io';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../providers/home_provider.dart';
import '../../../core/services/storage_service.dart';
import '../../../shared/models/media_item.dart';
import '../../../shared/screens/media_viewer_screen.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final summaryAsync = ref.watch(homeSummaryProvider);

    return Scaffold(
      body: CustomScrollView(
        slivers: [
          _buildAppBar(context),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 100),
            sliver: summaryAsync.when(
              loading: () => const SliverFillRemaining(
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (e, _) => SliverFillRemaining(
                child: Center(child: Text('오류: $e')),
              ),
              data: (s) => SliverList(
                delegate: SliverChildListDelegate([
                  const SizedBox(height: 16),
                  GestureDetector(
                    onTap: () => context.go('/work'),
                    child: _TotalSummaryCard(summary: s),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Expanded(child: _SpaceCard(
                        label: 'Work',
                        count: s.workCount,
                        byType: s.workByType,
                        sub: '${s.countryCount}개국',
                        gradient: const LinearGradient(
                          colors: [Color(0xFF1A73E8), Color(0xFF00C896)],
                        ),
                        icon: Icons.work_rounded,
                      )),
                      const SizedBox(width: 12),
                      Expanded(child: _SpaceCard(
                        label: 'Personal',
                        count: s.personalCount,
                        byType: s.personalByType,
                        sub: '앨범 ${s.albumCount}개 · 인물 ${s.peopleCount}명',
                        gradient: const LinearGradient(
                          colors: [Color(0xFFFF6B9D), Color(0xFF7B61FF)],
                        ),
                        icon: Icons.favorite_rounded,
                      )),
                    ],
                  ),
                  if (s.pendingSync > 0) ...[
                    const SizedBox(height: 12),
                    _SyncBanner(count: s.pendingSync),
                  ],
                  const SizedBox(height: 20),
                  _SectionTitle('최근 등록'),
                  const SizedBox(height: 10),
                  _RecentRow(items: s.recentItems),
                  const SizedBox(height: 20),
                  _SectionTitle('최근 30일 활동'),
                  const SizedBox(height: 10),
                  _ActivityChart(activityByDay: s.activityByDay),
                  if (s.topTags.isNotEmpty) ...[
                    const SizedBox(height: 20),
                    _SectionTitle('태그 TOP ${s.topTags.length}'),
                    const SizedBox(height: 10),
                    _TagRanking(tags: s.topTags),
                  ],
                  const SizedBox(height: 20),
                  _SectionTitle('저장 공간'),
                  const SizedBox(height: 10),
                  _StorageUsageCard(breakdown: s.storageBreakdown),
                  const SizedBox(height: 20),
                  _TypeBreakdown(workByType: s.workByType, personalByType: s.personalByType),
                ]),
              ),
            ),
          ),
        ],
      ),
    );
  }

  SliverAppBar _buildAppBar(BuildContext context) {
    final now = DateTime.now();
    final greeting = now.hour < 12 ? '좋은 아침이에요' :
                     now.hour < 18 ? '안녕하세요' : '수고하셨어요';
    final dateStr = DateFormat('M월 d일 (E)', 'ko').format(now);

    return SliverAppBar(
      expandedHeight: 110,
      floating: true,
      snap: true,
      pinned: false,
      flexibleSpace: FlexibleSpaceBar(
        background: Container(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              colors: [Color(0xFF00C896), Color(0xFF7B61FF)],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
          ),
          child: SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.baseline,
                    textBaseline: TextBaseline.alphabetic,
                    children: [
                      const Text(
                        'Memorix',
                        style: TextStyle(
                          fontSize: 24,
                          fontWeight: FontWeight.w900,
                          color: Colors.white,
                          letterSpacing: -0.5,
                        ),
                      ),
                      const SizedBox(width: 10),
                      Text(
                        dateStr,
                        style: const TextStyle(
                          fontSize: 12,
                          color: Colors.white70,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  const Text(
                    '기억은 빠르게, 보관은 조용하게.',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: Colors.white,
                      letterSpacing: 0.3,
                      shadows: [
                        Shadow(
                          color: Color(0x55000000),
                          blurRadius: 4,
                          offset: Offset(0, 1),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

// ── 총 요약 카드 ─────────────────────────────────────────────

class _TotalSummaryCard extends StatelessWidget {
  final HomeSummary summary;
  const _TotalSummaryCard({required this.summary});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final totalPhotos = (summary.workByType['photo'] ?? 0) +
        (summary.personalByType['photo'] ?? 0);
    final totalVideos = (summary.workByType['video'] ?? 0) +
        (summary.personalByType['video'] ?? 0);
    final totalDocs = (summary.workByType['document'] ?? 0) +
        (summary.personalByType['document'] ?? 0);
    final divColor = isDark ? const Color(0xFF2D3340) : const Color(0xFFECEFF3);

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF161B22) : Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: divColor),
        boxShadow: isDark ? null : [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Row(
        children: [
          // 전체 등록 수 (크게) + 유형별 (작게)
          Expanded(
            child: Column(
              children: [
                const Icon(Icons.perm_media_outlined,
                    color: Color(0xFF00C896), size: 22),
                const SizedBox(height: 6),
                Text('${summary.totalCount}개',
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w800,
                      color: Color(0xFF00C896),
                    )),
                const SizedBox(height: 3),
                const Text('등록 수',
                    style: TextStyle(fontSize: 11, color: Colors.grey)),
                const SizedBox(height: 5),
                Wrap(
                  alignment: WrapAlignment.center,
                  spacing: 6,
                  runSpacing: 2,
                  children: [
                    _MiniTypeLabel('사진', totalPhotos, const Color(0xFF1A73E8)),
                    _MiniTypeLabel('영상', totalVideos, const Color(0xFFFF6B9D)),
                    if (totalDocs > 0)
                      _MiniTypeLabel('문서', totalDocs, const Color(0xFFFFB800)),
                  ],
                ),
              ],
            ),
          ),
          Container(width: 1, height: 64, color: divColor),
          Expanded(
            child: _StatItem(
              label: '저장 용량',
              value: summary.storageBreakdown.totalLabel,
              icon: Icons.storage_outlined,
              color: const Color(0xFF7B61FF),
            ),
          ),
          Container(width: 1, height: 64, color: divColor),
          Expanded(
            child: _StatItem(
              label: 'Drive 대기',
              value: '${summary.pendingSync}개',
              icon: Icons.cloud_upload_outlined,
              color: summary.pendingSync > 0
                  ? const Color(0xFFFF6B35)
                  : const Color(0xFF00C896),
            ),
          ),
        ],
      ),
    );
  }
}

class _MiniTypeLabel extends StatelessWidget {
  final String label;
  final int count;
  final Color color;
  const _MiniTypeLabel(this.label, this.count, this.color);

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(width: 6, height: 6,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
        const SizedBox(width: 3),
        Text('$label $count',
            style: TextStyle(fontSize: 10, color: color, fontWeight: FontWeight.w600)),
      ],
    );
  }
}

class _StatItem extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  final Color color;
  const _StatItem({
    required this.label,
    required this.value,
    required this.icon,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Icon(icon, color: color, size: 22),
        const SizedBox(height: 6),
        Text(value,
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w800,
              color: color,
            )),
        const SizedBox(height: 2),
        Text(label,
            style: const TextStyle(fontSize: 11, color: Colors.grey)),
      ],
    );
  }
}

// ── Work/Personal 공간 카드 ──────────────────────────────────

class _SpaceCard extends StatelessWidget {
  final String label;
  final int count;
  final Map<String, int> byType;
  final String sub;
  final LinearGradient gradient;
  final IconData icon;

  const _SpaceCard({
    required this.label,
    required this.count,
    required this.byType,
    required this.sub,
    required this.gradient,
    required this.icon,
  });

  @override
  Widget build(BuildContext context) {
    final photos = byType['photo'] ?? 0;
    final videos = byType['video'] ?? 0;
    final docs = byType['document'] ?? 0;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: gradient,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: Colors.white, size: 18),
              const SizedBox(width: 6),
              Text(label,
                  style: const TextStyle(
                    color: Colors.white70,
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                  )),
            ],
          ),
          const SizedBox(height: 8),
          Text('$count개',
              style: const TextStyle(
                color: Colors.white,
                fontSize: 26,
                fontWeight: FontWeight.w800,
                letterSpacing: -0.5,
              )),
          const SizedBox(height: 4),
          Text(sub,
              style: const TextStyle(color: Colors.white70, fontSize: 11)),
          const SizedBox(height: 12),
          Row(
            children: [
              _TypePill('📷', '$photos'),
              const SizedBox(width: 6),
              _TypePill('🎬', '$videos'),
              if (docs > 0) ...[
                const SizedBox(width: 6),
                _TypePill('📄', '$docs'),
              ],
            ],
          ),
        ],
      ),
    );
  }
}

class _TypePill extends StatelessWidget {
  final String emoji;
  final String count;
  const _TypePill(this.emoji, this.count);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.2),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text('$emoji $count',
          style: const TextStyle(color: Colors.white, fontSize: 11)),
    );
  }
}

// ── Drive 동기화 배너 ────────────────────────────────────────

class _SyncBanner extends StatelessWidget {
  final int count;
  const _SyncBanner({required this.count});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: const Color(0xFFFF6B35).withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFFF6B35).withValues(alpha: 0.3)),
      ),
      child: Row(
        children: [
          const Icon(Icons.cloud_upload_outlined,
              color: Color(0xFFFF6B35), size: 18),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              'Drive 동기화 대기 중 $count개',
              style: const TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: Color(0xFFFF6B35),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ── 최근 등록 가로 스크롤 ────────────────────────────────────

class _RecentRow extends StatelessWidget {
  final List<MediaItem> items;
  const _RecentRow({required this.items});

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text('등록된 미디어가 없습니다',
              style: TextStyle(color: Colors.grey, fontSize: 13)),
        ),
      );
    }
    return SizedBox(
      height: 120,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: items.length,
        separatorBuilder: (context, index) => const SizedBox(width: 8),
        itemBuilder: (context, i) => GestureDetector(
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => MediaViewerScreen(items: items, initialIndex: i),
            ),
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: SizedBox(
              width: 120,
              height: 120,
              child: _RecentThumb(item: items[i]),
            ),
          ),
        ),
      ),
    );
  }
}

class _RecentThumb extends StatelessWidget {
  final MediaItem item;
  const _RecentThumb({required this.item});

  @override
  Widget build(BuildContext context) {
    if (item.mediaType == MediaType.document) {
      return Container(
        color: Colors.blue[50],
        child: const Center(
          child: Icon(Icons.description, color: Colors.blueGrey, size: 36),
        ),
      );
    }
    final path = item.thumbPath ?? item.filePath;
    if (File(path).existsSync()) {
      return Stack(
        fit: StackFit.expand,
        children: [
          Image.file(File(path), fit: BoxFit.cover),
          if (item.mediaType == MediaType.video)
            const Center(
              child: Icon(Icons.play_circle_outline,
                  color: Colors.white, size: 28),
            ),
        ],
      );
    }
    return Container(color: Colors.grey[200]);
  }
}

// ── 30일 활동 바 차트 ────────────────────────────────────────

class _ActivityChart extends StatelessWidget {
  final Map<String, int> activityByDay;
  const _ActivityChart({required this.activityByDay});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    if (activityByDay.isEmpty) {
      return Container(
        height: 80,
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF161B22) : Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
              color: isDark ? const Color(0xFF262D37) : const Color(0xFFECEFF3)),
        ),
        child: const Center(
          child: Text('이번 달 활동이 없습니다',
              style: TextStyle(color: Colors.grey, fontSize: 13)),
        ),
      );
    }

    // 최근 30일 날짜 목록 생성
    final days = List.generate(30, (i) {
      final d = DateTime.now().subtract(Duration(days: 29 - i));
      return DateFormat('yyyy-MM-dd').format(d);
    });

    final maxCount = activityByDay.values.isEmpty
        ? 1
        : activityByDay.values.reduce(math.max);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF161B22) : Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
            color: isDark ? const Color(0xFF262D37) : const Color(0xFFECEFF3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            height: 80,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: days.map((day) {
                final count = activityByDay[day] ?? 0;
                final ratio = maxCount > 0 ? count / maxCount : 0.0;
                return Expanded(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 1),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        AnimatedContainer(
                          duration: const Duration(milliseconds: 400),
                          height: 6 + 66 * ratio,
                          decoration: BoxDecoration(
                            color: count > 0
                                ? const Color(0xFF00C896)
                                : (isDark
                                    ? const Color(0xFF262D37)
                                    : const Color(0xFFECEFF3)),
                            borderRadius: BorderRadius.circular(2),
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                DateFormat('M/d').format(
                    DateTime.now().subtract(const Duration(days: 29))),
                style: const TextStyle(fontSize: 10, color: Colors.grey),
              ),
              Text(
                '오늘',
                style: const TextStyle(fontSize: 10, color: Colors.grey),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

// ── 태그 랭킹 ────────────────────────────────────────────────

class _TagRanking extends StatelessWidget {
  final List<Map<String, dynamic>> tags;
  const _TagRanking({required this.tags});

  static Color _hexColor(String hex) {
    try {
      return Color(int.parse(hex.replaceFirst('#', 'FF'), radix: 16));
    } catch (_) {
      return Colors.grey;
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final maxCount = tags.isEmpty
        ? 1
        : (tags.first['cnt'] as int? ?? 1);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF161B22) : Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
            color: isDark ? const Color(0xFF262D37) : const Color(0xFFECEFF3)),
      ),
      child: Column(
        children: tags.asMap().entries.map((entry) {
          final i = entry.key;
          final tag = entry.value;
          final label = tag['label'] as String? ?? '';
          final color = _hexColor(tag['color'] as String? ?? '#9E9E9E');
          final cnt = tag['cnt'] as int? ?? 0;
          final ratio = maxCount > 0 ? cnt / maxCount : 0.0;

          return Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Row(
              children: [
                SizedBox(
                  width: 20,
                  child: Text(
                    '${i + 1}',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: i == 0 ? const Color(0xFFFFB800) : Colors.grey,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                      color: color, shape: BoxShape.circle),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(label,
                              style: const TextStyle(
                                  fontSize: 13, fontWeight: FontWeight.w600)),
                          Text('$cnt회',
                              style: const TextStyle(
                                  fontSize: 11, color: Colors.grey)),
                        ],
                      ),
                      const SizedBox(height: 4),
                      ClipRRect(
                        borderRadius: BorderRadius.circular(4),
                        child: LinearProgressIndicator(
                          value: ratio,
                          minHeight: 5,
                          backgroundColor: color.withValues(alpha: 0.12),
                          valueColor: AlwaysStoppedAnimation(color),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          );
        }).toList(),
      ),
    );
  }
}

// ── 미디어 타입 분포 ─────────────────────────────────────────

class _TypeBreakdown extends StatelessWidget {
  final Map<String, int> workByType;
  final Map<String, int> personalByType;
  const _TypeBreakdown({
    required this.workByType,
    required this.personalByType,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final items = [
      _BreakdownItem('사진', Icons.photo_outlined, const Color(0xFF1A73E8),
          (workByType['photo'] ?? 0) + (personalByType['photo'] ?? 0)),
      _BreakdownItem('영상', Icons.videocam_outlined, const Color(0xFFFF6B9D),
          (workByType['video'] ?? 0) + (personalByType['video'] ?? 0)),
      _BreakdownItem('문서', Icons.description_outlined, const Color(0xFFFFB800),
          (workByType['document'] ?? 0) + (personalByType['document'] ?? 0)),
    ];
    final total = items.fold<int>(0, (s, e) => s + e.count);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF161B22) : Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
            color: isDark ? const Color(0xFF262D37) : const Color(0xFFECEFF3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('미디어 타입',
              style: TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
          const SizedBox(height: 14),
          Row(
            children: items.map((item) {
              final ratio = total > 0 ? item.count / total : 0.0;
              return Expanded(
                child: Column(
                  children: [
                    Container(
                      width: 44,
                      height: 44,
                      decoration: BoxDecoration(
                        color: item.color.withValues(alpha: 0.12),
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: Icon(item.icon, color: item.color, size: 22),
                    ),
                    const SizedBox(height: 6),
                    Text(item.label,
                        style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
                    const SizedBox(height: 2),
                    Text('${(ratio * 100).toStringAsFixed(0)}%',
                        style: TextStyle(fontSize: 12, color: item.color, fontWeight: FontWeight.w700)),
                    Text('${item.count}개',
                        style: const TextStyle(fontSize: 11, color: Colors.grey)),
                  ],
                ),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }
}

class _BreakdownItem {
  final String label;
  final IconData icon;
  final Color color;
  final int count;
  const _BreakdownItem(this.label, this.icon, this.color, this.count);
}

// ── 저장 공간 사용량 카드 ────────────────────────────────────

class _StorageUsageCard extends StatelessWidget {
  final StorageBreakdown breakdown;
  const _StorageUsageCard({required this.breakdown});

  static const _segments = [
    ('사진', const Color(0xFF1A73E8)),
    ('영상', const Color(0xFFFF6B9D)),
    ('문서', const Color(0xFFFFB800)),
    ('보고서', const Color(0xFF7B61FF)),
    ('DB', const Color(0xFF9E9E9E)),
  ];

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final total = breakdown.total;
    final sizes = [
      breakdown.photos,
      breakdown.videos,
      breakdown.documents,
      breakdown.reports,
      breakdown.db,
    ];
    final labels = [
      breakdown.photosLabel,
      breakdown.videosLabel,
      breakdown.documentsLabel,
      breakdown.reportsLabel,
      breakdown.dbLabel,
    ];

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF161B22) : Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
            color:
                isDark ? const Color(0xFF262D37) : const Color(0xFFECEFF3)),
        boxShadow: isDark
            ? null
            : [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.03),
                  blurRadius: 8,
                  offset: const Offset(0, 2),
                ),
              ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                '총 ${breakdown.totalLabel}',
                style: const TextStyle(
                    fontSize: 15, fontWeight: FontWeight.w800),
              ),
              Text(
                total == 0 ? '비어 있음' : '메모릭스 내부 저장',
                style:
                    const TextStyle(fontSize: 11, color: Colors.grey),
              ),
            ],
          ),
          const SizedBox(height: 12),
          // 세그먼트 바
          if (total == 0)
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: LinearProgressIndicator(
                value: 0,
                minHeight: 12,
                backgroundColor:
                    isDark ? const Color(0xFF262D37) : const Color(0xFFECEFF3),
              ),
            )
          else
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: SizedBox(
                height: 12,
                child: Row(
                  children: List.generate(_segments.length, (i) {
                    if (sizes[i] == 0) return const SizedBox.shrink();
                    return Flexible(
                      flex: sizes[i],
                      child: Container(color: _segments[i].$2),
                    );
                  }),
                ),
              ),
            ),
          const SizedBox(height: 12),
          // 범례
          Wrap(
            spacing: 16,
            runSpacing: 6,
            children: List.generate(_segments.length, (i) {
              if (sizes[i] == 0) return const SizedBox.shrink();
              return Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration: BoxDecoration(
                        color: _segments[i].$2, shape: BoxShape.circle),
                  ),
                  const SizedBox(width: 4),
                  Text(
                    '${_segments[i].$1}  ${labels[i]}',
                    style: const TextStyle(
                        fontSize: 11, color: Colors.grey),
                  ),
                ],
              );
            }),
          ),
        ],
      ),
    );
  }
}

// ── 섹션 타이틀 ──────────────────────────────────────────────

class _SectionTitle extends StatelessWidget {
  final String text;
  const _SectionTitle(this.text);

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: Theme.of(context)
          .textTheme
          .titleMedium
          ?.copyWith(fontWeight: FontWeight.w700),
    );
  }
}
