import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/search_provider.dart';
import '../../../shared/models/media_item.dart';
import '../../../shared/screens/media_detail_screen.dart';
import '../../../shared/screens/media_viewer_screen.dart';
import '../../../shared/widgets/media_thumbnail.dart';

class SearchScreen extends ConsumerStatefulWidget {
  const SearchScreen({super.key});

  @override
  ConsumerState<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends ConsumerState<SearchScreen> {
  final _ctrl = TextEditingController();

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final query = ref.watch(searchQueryProvider);
    final spaceFilter = ref.watch(searchSpaceProvider);
    final resultAsync = ref.watch(searchResultProvider);

    return Scaffold(
      appBar: AppBar(
        titleSpacing: 0,
        title: TextField(
          controller: _ctrl,
          autofocus: false,
          decoration: InputDecoration(
            hintText: '제목, 메모, 국가, 지역 검색...',
            border: InputBorder.none,
            prefixIcon: const Icon(Icons.search),
            suffixIcon: query.isNotEmpty
                ? IconButton(
                    icon: const Icon(Icons.clear),
                    onPressed: () {
                      _ctrl.clear();
                      ref.read(searchQueryProvider.notifier).state = '';
                      ref.read(searchAlbumIdProvider.notifier).state = null;
                      ref.read(searchPersonIdProvider.notifier).state = null;
                      ref.read(searchTagIdProvider.notifier).state = null;
                    },
                  )
                : null,
          ),
          onChanged: (v) => ref.read(searchQueryProvider.notifier).state = v,
        ),
      ),
      body: Column(
        children: [
          // ── Space 필터 ──
          _SpaceFilterBar(
            selected: spaceFilter,
            onSelect: (s) {
              ref.read(searchSpaceProvider.notifier).state = s;
              ref.read(searchAlbumIdProvider.notifier).state = null;
              ref.read(searchPersonIdProvider.notifier).state = null;
              ref.read(searchTagIdProvider.notifier).state = null;
            },
          ),
          // ── 태그 필터 ──
          _TagFilterBar(space: spaceFilter),
          // ── Secret 전용 필터 (앨범 + 인물) ──
          if (spaceFilter == MediaSpace.secret) _PersonalFilterBar(),
          // ── 결과 ──
          Expanded(
            child: query.isEmpty
                ? const _SearchHint()
                : resultAsync.when(
                    loading: () =>
                        const Center(child: CircularProgressIndicator()),
                    error: (e, _) => Center(child: Text('오류: $e')),
                    data: (items) => items.isEmpty
                        ? _EmptyResult(query: query)
                        : _ResultGrid(items: items),
                  ),
          ),
        ],
      ),
    );
  }
}

// ── Space 필터 바 ──
class _SpaceFilterBar extends StatelessWidget {
  final MediaSpace? selected;
  final ValueChanged<MediaSpace?> onSelect;

  const _SpaceFilterBar({required this.selected, required this.onSelect});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 44,
      padding: const EdgeInsets.symmetric(horizontal: 12),
      decoration: BoxDecoration(
        border: Border(bottom: BorderSide(color: Colors.grey[200]!)),
      ),
      child: Row(
        children: [
          _chip(context, null, '전체'),
          const SizedBox(width: 8),
          _chip(context, MediaSpace.work, '💼 Work'),
          const SizedBox(width: 8),
          _chip(context, MediaSpace.secret, '🔒 Secret'),
        ],
      ),
    );
  }

  Widget _chip(BuildContext context, MediaSpace? space, String label) {
    return ChoiceChip(
      label: Text(label, style: const TextStyle(fontSize: 12)),
      selected: selected == space,
      visualDensity: VisualDensity.compact,
      onSelected: (_) => onSelect(space),
    );
  }
}

// ── 태그 필터 바 (공간 공통) ──
class _TagFilterBar extends ConsumerWidget {
  final MediaSpace? space;
  const _TagFilterBar({required this.space});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tagsAsync = ref.watch(searchTagListProvider(space));
    final selTag = ref.watch(searchTagIdProvider);

    return tagsAsync.when(
      loading: () => const SizedBox.shrink(),
      error: (e, st) => const SizedBox.shrink(),
      data: (tags) {
        if (tags.isEmpty) return const SizedBox.shrink();
        return Container(
          height: 44,
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surfaceContainerLowest,
            border: Border(bottom: BorderSide(color: Colors.grey[200]!)),
          ),
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
            children: [
              Padding(
                padding: const EdgeInsets.only(right: 6),
                child: ChoiceChip(
                  label: const Text('태그 전체', style: TextStyle(fontSize: 11)),
                  selected: selTag == null,
                  visualDensity: VisualDensity.compact,
                  onSelected: (_) =>
                      ref.read(searchTagIdProvider.notifier).state = null,
                ),
              ),
              ...tags.map(
                (t) => Padding(
                  padding: const EdgeInsets.only(right: 6),
                  child: ChoiceChip(
                    avatar: Text(t.icon, style: const TextStyle(fontSize: 12)),
                    label: Text(t.label, style: const TextStyle(fontSize: 11)),
                    selected: selTag == t.id,
                    visualDensity: VisualDensity.compact,
                    onSelected: (_) =>
                        ref.read(searchTagIdProvider.notifier).state = t.id,
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

// ── Personal 전용 앨범/인물 필터 바 ──
class _PersonalFilterBar extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final albumAsync = ref.watch(searchAlbumListProvider);
    final personAsync = ref.watch(searchPersonListProvider);
    final selAlbum = ref.watch(searchAlbumIdProvider);
    final selPerson = ref.watch(searchPersonIdProvider);

    return Container(
      constraints: const BoxConstraints(maxHeight: 96),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerLowest,
        border: Border(bottom: BorderSide(color: Colors.grey[200]!)),
      ),
      child: ListView(
        shrinkWrap: true,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        children: [
          // 앨범 필터
          albumAsync.when(
            loading: () => const SizedBox.shrink(),
            error: (e, st) => const SizedBox.shrink(),
            data: (albums) {
              if (albums.isEmpty) return const SizedBox.shrink();
              return _FilterRow(
                label: '앨범',
                children: [
                  _smallChip(
                    context,
                    label: '전체',
                    selected: selAlbum == null,
                    onTap: () =>
                        ref.read(searchAlbumIdProvider.notifier).state = null,
                  ),
                  ...albums.map(
                    (a) => _smallChip(
                      context,
                      label: a.title,
                      selected: selAlbum == a.id,
                      onTap: () =>
                          ref.read(searchAlbumIdProvider.notifier).state = a.id,
                    ),
                  ),
                ],
              );
            },
          ),
          const SizedBox(height: 4),
          // 인물 필터
          personAsync.when(
            loading: () => const SizedBox.shrink(),
            error: (e, st) => const SizedBox.shrink(),
            data: (people) {
              if (people.isEmpty) return const SizedBox.shrink();
              return _FilterRow(
                label: '인물',
                children: [
                  _smallChip(
                    context,
                    label: '전체',
                    selected: selPerson == null,
                    onTap: () =>
                        ref.read(searchPersonIdProvider.notifier).state = null,
                  ),
                  ...people.map(
                    (p) => _smallChip(
                      context,
                      label: p.name,
                      selected: selPerson == p.id,
                      onTap: () =>
                          ref.read(searchPersonIdProvider.notifier).state =
                              p.id,
                    ),
                  ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _smallChip(
    BuildContext context, {
    required String label,
    required bool selected,
    required VoidCallback onTap,
  }) {
    return Padding(
      padding: const EdgeInsets.only(right: 6),
      child: ChoiceChip(
        label: Text(label, style: const TextStyle(fontSize: 11)),
        selected: selected,
        visualDensity: VisualDensity.compact,
        onSelected: (_) => onTap(),
      ),
    );
  }
}

class _FilterRow extends StatelessWidget {
  final String label;
  final List<Widget> children;
  const _FilterRow({required this.label, required this.children});

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Text(
          label,
          style: TextStyle(
            fontSize: 11,
            color: Colors.grey[600],
            fontWeight: FontWeight.w500,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(children: children),
          ),
        ),
      ],
    );
  }
}

// ── 검색 힌트 ──
class _SearchHint extends StatelessWidget {
  const _SearchHint();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.search, size: 56, color: Colors.grey[300]),
          const SizedBox(height: 12),
          Text(
            '제목, 메모를 입력하세요',
            style: TextStyle(color: Colors.grey[500], fontSize: 15),
          ),
          const SizedBox(height: 8),
          Text(
            'Work + Personal 동시 검색',
            style: TextStyle(color: Colors.grey[400], fontSize: 12),
          ),
        ],
      ),
    );
  }
}

// ── 결과 없음 ──
class _EmptyResult extends StatelessWidget {
  final String query;
  const _EmptyResult({required this.query});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.search_off, size: 56, color: Colors.grey[300]),
          const SizedBox(height: 12),
          Text(
            '"$query" 결과 없음',
            style: TextStyle(color: Colors.grey[500], fontSize: 15),
          ),
        ],
      ),
    );
  }
}

// ── 결과 그리드 ──
class _ResultGrid extends ConsumerWidget {
  final List<MediaItem> items;
  const _ResultGrid({required this.items});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return CustomScrollView(
      slivers: [
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          sliver: SliverToBoxAdapter(
            child: Text(
              '결과 ${items.length}개',
              style: TextStyle(
                fontSize: 12,
                color: Colors.grey[600],
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ),
        SliverPadding(
          padding: const EdgeInsets.all(8),
          sliver: SliverGrid(
            delegate: SliverChildBuilderDelegate((context, index) {
              final item = items[index];
              return Stack(
                fit: StackFit.expand,
                children: [
                  MediaThumbnailCard(
                    item: item,
                    onTap: () => _openViewer(context, items, index),
                    onLongPress: () => _openDetail(context, ref, item),
                  ),
                  // Space 뱃지
                  Positioned(
                    top: 4,
                    left: 4,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 5,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: item.space == MediaSpace.work
                            ? Colors.indigo.withValues(alpha: 0.85)
                            : Colors.teal.withValues(alpha: 0.85),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Text(
                        item.space == MediaSpace.work ? '💼' : '🏠',
                        style: const TextStyle(fontSize: 10),
                      ),
                    ),
                  ),
                ],
              );
            }, childCount: items.length),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 3,
              crossAxisSpacing: 4,
              mainAxisSpacing: 4,
            ),
          ),
        ),
      ],
    );
  }

  void _openViewer(BuildContext context, List<MediaItem> items, int index) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => MediaViewerScreen(items: items, initialIndex: index),
      ),
    );
  }

  Future<void> _openDetail(
    BuildContext context,
    WidgetRef ref,
    MediaItem item,
  ) async {
    final changed = await Navigator.push<dynamic>(
      context,
      MaterialPageRoute(
        builder: (_) => MediaDetailScreen(items: [item], initialIndex: 0),
      ),
    );
    if (changed != null) ref.invalidate(searchResultProvider);
  }
}
