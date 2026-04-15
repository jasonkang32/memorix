import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/personal_provider.dart';
import '../../../core/db/album_dao.dart';
import '../../../core/db/media_dao.dart';
import '../../../core/services/media_capture_service.dart';
import '../../../core/services/media_save_service.dart';
import '../../../features/auth/providers/personal_lock_provider.dart';
import '../../../shared/models/album.dart';
import '../../../shared/models/media_item.dart';
import '../../../shared/widgets/capture_bottom_sheet.dart';
import '../../../shared/widgets/media_timeline.dart';
import '../../../shared/screens/media_detail_screen.dart';
import '../../../shared/screens/media_viewer_screen.dart';
import 'album_detail_screen.dart';

class PersonalScreen extends ConsumerStatefulWidget {
  const PersonalScreen({super.key});

  @override
  ConsumerState<PersonalScreen> createState() => _PersonalScreenState();
}

class _PersonalScreenState extends ConsumerState<PersonalScreen> {
  bool _searching = false;
  bool _albumGridMode = false;
  String _query = '';
  List<MediaItem>? _searchResults;
  final _searchCtrl = TextEditingController();
  final _dao = MediaDao();

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  Future<void> _runSearch(String q) async {
    setState(() => _query = q);
    if (q.trim().isEmpty) {
      setState(() => _searchResults = null);
      return;
    }
    final results = await _dao.quickSearch(q, 'personal');
    setState(() => _searchResults = results);
  }

  @override
  Widget build(BuildContext context) {
    final lockState = ref.watch(personalLockProvider);

    if (lockState == PersonalLockState.checking) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (lockState == PersonalLockState.locked) {
      return _PersonalLockGate(
        onUnlock: () => ref.read(personalLockProvider.notifier).tryUnlock(),
      );
    }

    final albumAsync = ref.watch(albumListProvider);

    return Scaffold(
      appBar: _searching ? _searchBar() : _normalBar(),
      body: _searching
          ? _buildSearchBody()
          : _albumGridMode
              ? albumAsync.when(
                  loading: () =>
                      const Center(child: CircularProgressIndicator()),
                  error: (e, _) => Center(child: Text('오류: $e')),
                  data: (albums) => _AlbumGridView(
                    albums: albums,
                    onAlbumUpdated: () => ref.invalidate(albumListProvider),
                  ),
                )
              : Column(
                  children: [
                    albumAsync.when(
                      loading: () => const SizedBox(
                          height: 52,
                          child: Center(child: CircularProgressIndicator())),
                      error: (e, _) => const SizedBox(),
                      data: (albums) => _AlbumChipRow(albums: albums),
                    ),
                    Expanded(
                      child: Consumer(
                        builder: (context, ref, _) {
                          final mediaAsync = ref.watch(personalMediaProvider);
                          return mediaAsync.when(
                            loading: () =>
                                const Center(child: CircularProgressIndicator()),
                            error: (e, _) => Center(child: Text('오류: $e')),
                            data: (items) => items.isEmpty
                                ? const _EmptyPersonalView()
                                : MediaTimeline(
                                    items: items,
                                    onTap: (group, idx) =>
                                        _openDetail(context, group, idx),
                                    onLongPress: (item) =>
                                        _openViewer(context, [item], 0),
                                    onRefresh: () async =>
                                        ref.invalidate(personalMediaProvider),
                                  ),
                          );
                        },
                      ),
                    ),
                  ],
                ),
    );
  }

  AppBar _normalBar() {
    return AppBar(
      title: Row(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                  colors: [Color(0xFFFF6B9D), Color(0xFF7B61FF)]),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Text('Personal',
                style: TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w800,
                    fontSize: 14)),
          ),
        ],
      ),
      actions: [
        IconButton(
          icon: const Icon(Icons.search_rounded),
          onPressed: () => setState(() => _searching = true),
        ),
        IconButton(
          icon: Icon(_albumGridMode
              ? Icons.view_stream_outlined
              : Icons.grid_view_outlined),
          tooltip: _albumGridMode ? '타임라인' : '앨범 목록',
          onPressed: () => setState(() => _albumGridMode = !_albumGridMode),
        ),
        IconButton(
          icon: const Icon(Icons.create_new_folder_outlined),
          onPressed: () => _showCreateAlbumDialog(context, ref),
        ),
        if (!_albumGridMode)
          IconButton(
            icon: const Icon(Icons.add_a_photo_outlined),
            onPressed: () => _onAddMedia(context, ref),
          ),
      ],
    );
  }

  AppBar _searchBar() {
    return AppBar(
      leading: IconButton(
        icon: const Icon(Icons.arrow_back),
        onPressed: () {
          setState(() {
            _searching = false;
            _searchResults = null;
            _query = '';
            _searchCtrl.clear();
          });
        },
      ),
      title: TextField(
        controller: _searchCtrl,
        autofocus: true,
        decoration: const InputDecoration(
          hintText: 'Personal 검색...',
          border: InputBorder.none,
          contentPadding: EdgeInsets.zero,
        ),
        onChanged: _runSearch,
      ),
      actions: [
        if (_query.isNotEmpty)
          IconButton(
            icon: const Icon(Icons.clear),
            onPressed: () {
              _searchCtrl.clear();
              _runSearch('');
            },
          ),
      ],
    );
  }

  Widget _buildSearchBody() {
    if (_query.isEmpty) {
      return const Center(
          child: Text('검색어를 입력하세요',
              style: TextStyle(color: Colors.grey, fontSize: 14)));
    }
    final results = _searchResults ?? [];
    if (results.isEmpty) {
      return const Center(
          child: Text('검색 결과가 없습니다',
              style: TextStyle(color: Colors.grey, fontSize: 14)));
    }
    return MediaTimeline(
      items: results,
      onTap: (group, idx) => _openDetail(context, group, idx),
      onLongPress: (item) => _openViewer(context, [item], 0),
    );
  }

  void _showCreateAlbumDialog(BuildContext context, WidgetRef ref) {
    showDialog(
      context: context,
      builder: (_) =>
          _CreateAlbumDialog(onCreated: () => ref.invalidate(albumListProvider)),
    );
  }

  Future<void> _onAddMedia(BuildContext context, WidgetRef ref) async {
    List<CapturedMedia>? capturedList = await CaptureBottomSheet.show(context);

    if (capturedList == null || capturedList.isEmpty || !context.mounted) return;

    final results = await MediaSaveService.saveAll(
      captured: capturedList,
      space: MediaSpace.personal,
    );
    ref.invalidate(personalMediaProvider);

    if (!context.mounted) return;
    if (results.length > 1) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('${results.length}개 저장됨. 첫 번째 항목을 편집합니다.'),
        duration: const Duration(seconds: 2),
      ));
    }
    final savedItems = results.map((r) => r.item).toList();
    await Navigator.push<dynamic>(
      context,
      MaterialPageRoute(
        builder: (_) => MediaDetailScreen(items: savedItems, initialIndex: 0),
      ),
    );
    if (context.mounted) ref.invalidate(personalMediaProvider);
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
      BuildContext context, List<MediaItem> group, int index) async {
    final changed = await Navigator.push<dynamic>(
      context,
      MaterialPageRoute(
        builder: (_) => MediaDetailScreen(items: group, initialIndex: index),
      ),
    );
    if (changed != null) ref.invalidate(personalMediaProvider);
  }
}

// ── 앨범 칩 가로 스크롤 바 ──
class _AlbumChipRow extends ConsumerStatefulWidget {
  final List<Album> albums;
  const _AlbumChipRow({required this.albums});

  @override
  ConsumerState<_AlbumChipRow> createState() => _AlbumChipRowState();
}

class _AlbumChipRowState extends ConsumerState<_AlbumChipRow> {
  final _mediaDao = MediaDao();
  final Map<int, String?> _coverPaths = {};

  @override
  void initState() {
    super.initState();
    _loadCovers();
  }

  @override
  void didUpdateWidget(_AlbumChipRow old) {
    super.didUpdateWidget(old);
    if (old.albums != widget.albums) _loadCovers();
  }

  Future<void> _loadCovers() async {
    for (final album in widget.albums) {
      if (album.id != null && album.coverMediaId != null) {
        final item = await _mediaDao.findById(album.coverMediaId!);
        if (mounted && item?.thumbPath != null) {
          setState(() => _coverPaths[album.id!] = item!.thumbPath);
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (widget.albums.isEmpty) return const SizedBox(height: 8);
    return SizedBox(
      height: 52,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        children: widget.albums.map((a) {
          final coverPath = a.id != null ? _coverPaths[a.id] : null;
          Widget? avatar;
          if (coverPath != null) {
            avatar = ClipOval(
              child: Image.file(
                File(coverPath),
                width: 24,
                height: 24,
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) =>
                    const Icon(Icons.photo, size: 16),
              ),
            );
          }
          return Padding(
            padding: const EdgeInsets.only(right: 8),
            child: ActionChip(
              avatar: avatar,
              label: Text(a.title),
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(
                    builder: (_) => AlbumDetailScreen(album: a)),
              ).then((_) => ref.invalidate(albumListProvider)),
            ),
          );
        }).toList(),
      ),
    );
  }
}

// ── 앨범 카드 그리드 뷰 ──
class _AlbumGridView extends StatefulWidget {
  final List<Album> albums;
  final VoidCallback onAlbumUpdated;
  const _AlbumGridView(
      {required this.albums, required this.onAlbumUpdated});

  @override
  State<_AlbumGridView> createState() => _AlbumGridViewState();
}

class _AlbumGridViewState extends State<_AlbumGridView> {
  final _mediaDao = MediaDao();
  final Map<int, String?> _coverPaths = {};

  @override
  void initState() {
    super.initState();
    _loadCovers();
  }

  @override
  void didUpdateWidget(_AlbumGridView old) {
    super.didUpdateWidget(old);
    if (old.albums != widget.albums) _loadCovers();
  }

  Future<void> _loadCovers() async {
    for (final album in widget.albums) {
      if (album.id != null && album.coverMediaId != null) {
        final item = await _mediaDao.findById(album.coverMediaId!);
        if (mounted) {
          setState(() => _coverPaths[album.id!] = item?.thumbPath);
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (widget.albums.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(32),
          child: Text(
            '앨범이 없습니다\n우측 상단 버튼으로 앨범을 만드세요',
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.grey, fontSize: 14, height: 1.7),
          ),
        ),
      );
    }
    return GridView.builder(
      padding: const EdgeInsets.all(12),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        crossAxisSpacing: 12,
        mainAxisSpacing: 12,
        childAspectRatio: 0.82,
      ),
      itemCount: widget.albums.length,
      itemBuilder: (ctx, i) {
        final album = widget.albums[i];
        final coverPath = album.id != null ? _coverPaths[album.id] : null;
        return _AlbumCard(
          album: album,
          coverPath: coverPath,
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(
                builder: (_) => AlbumDetailScreen(album: album)),
          ).then((_) => widget.onAlbumUpdated()),
        );
      },
    );
  }
}

class _AlbumCard extends StatelessWidget {
  final Album album;
  final String? coverPath;
  final VoidCallback onTap;
  const _AlbumCard(
      {required this.album, required this.coverPath, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final emoji = _eventEmoji(album.eventType);
    return GestureDetector(
      onTap: onTap,
      child: Card(
        clipBehavior: Clip.antiAlias,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              child: coverPath != null
                  ? Image.file(
                      File(coverPath!),
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) =>
                          _placeholder(context, emoji),
                    )
                  : _placeholder(context, emoji),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(10, 8, 10, 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '$emoji ${album.title}',
                    style: const TextStyle(
                        fontWeight: FontWeight.w600, fontSize: 14),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (album.dateStart != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      _fmtDateRange(album.dateStart, album.dateEnd),
                      style:
                          TextStyle(fontSize: 11, color: Colors.grey[600]),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _placeholder(BuildContext context, String emoji) {
    return Container(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: Center(
        child: Text(emoji, style: const TextStyle(fontSize: 40)),
      ),
    );
  }

  static String _eventEmoji(EventType type) => switch (type) {
        EventType.travel => '✈️',
        EventType.ceremony => '💍',
        EventType.gathering => '🎉',
        EventType.birthday => '🎂',
        EventType.daily => '📅',
        EventType.other => '📁',
      };

  static String _fmtDateRange(int? start, int? end) {
    if (start == null) return '';
    final s = DateTime.fromMillisecondsSinceEpoch(start);
    final startStr =
        '${s.year}.${s.month.toString().padLeft(2, '0')}.${s.day.toString().padLeft(2, '0')}';
    if (end == null) return startStr;
    final e = DateTime.fromMillisecondsSinceEpoch(end);
    final endStr =
        '${e.year}.${e.month.toString().padLeft(2, '0')}.${e.day.toString().padLeft(2, '0')}';
    return '$startStr ~ $endStr';
  }
}

// ── Personal Space 잠금 게이트 ──
class _EmptyPersonalView extends StatelessWidget {
  const _EmptyPersonalView();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 40),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 96,
              height: 96,
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFFFF6B9D), Color(0xFF7B61FF)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(28),
              ),
              child: const Icon(Icons.favorite_outline,
                  size: 44, color: Colors.white),
            ),
            const SizedBox(height: 24),
            Text(
              '개인 미디어가 없어요',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            Text(
              '메모릭스에만 보관\n외부에 노출되지 않아요',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 13,
                color: Colors.grey[500],
                height: 1.6,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              '앨범을 만들고 사진을 추가하세요',
              style: TextStyle(fontSize: 12, color: Colors.grey[400]),
            ),
          ],
        ),
      ),
    );
  }
}

class _PersonalLockGate extends StatefulWidget {
  final Future<bool> Function() onUnlock;
  const _PersonalLockGate({required this.onUnlock});

  @override
  State<_PersonalLockGate> createState() => _PersonalLockGateState();
}

class _PersonalLockGateState extends State<_PersonalLockGate> {
  bool _unlocking = false;

  Future<void> _tryUnlock() async {
    setState(() => _unlocking = true);
    await widget.onUnlock();
    if (mounted) setState(() => _unlocking = false);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('🏠 Personal')),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.lock_outlined, size: 64, color: Colors.grey[400]),
            const SizedBox(height: 20),
            const Text('Personal Space가 잠겨 있습니다',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500)),
            const SizedBox(height: 8),
            Text('생체인증으로 잠금 해제',
                style: TextStyle(color: Colors.grey[600], fontSize: 13)),
            const SizedBox(height: 32),
            _unlocking
                ? const CircularProgressIndicator()
                : FilledButton.icon(
                    onPressed: _tryUnlock,
                    icon: const Icon(Icons.fingerprint),
                    label: const Text('잠금 해제'),
                  ),
          ],
        ),
      ),
    );
  }
}

// ── 새 앨범 만들기 다이얼로그 ──
class _CreateAlbumDialog extends StatefulWidget {
  final VoidCallback? onCreated;
  const _CreateAlbumDialog({this.onCreated});

  @override
  State<_CreateAlbumDialog> createState() => _CreateAlbumDialogState();
}

class _CreateAlbumDialogState extends State<_CreateAlbumDialog> {
  final _titleCtrl = TextEditingController();
  final _albumDao = AlbumDao();
  EventType _eventType = EventType.travel;
  DateTime? _dateStart;
  DateTime? _dateEnd;

  @override
  void dispose() {
    _titleCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickDateRange() async {
    final range = await showDateRangePicker(
      context: context,
      firstDate: DateTime(2000),
      lastDate: DateTime(2100),
      initialDateRange: _dateStart != null && _dateEnd != null
          ? DateTimeRange(start: _dateStart!, end: _dateEnd!)
          : null,
      locale: const Locale('ko'),
    );
    if (range != null) {
      setState(() {
        _dateStart = range.start;
        _dateEnd = range.end;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('새 앨범'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: _titleCtrl,
            decoration: const InputDecoration(
              labelText: '앨범 이름',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<EventType>(
            initialValue: _eventType,
            decoration: const InputDecoration(
              labelText: '이벤트 유형',
              border: OutlineInputBorder(),
            ),
            items: EventType.values
                .map((e) => DropdownMenuItem(value: e, child: Text(e.name)))
                .toList(),
            onChanged: (v) => setState(() => _eventType = v!),
          ),
          const SizedBox(height: 12),
          InkWell(
            onTap: _pickDateRange,
            borderRadius: BorderRadius.circular(4),
            child: InputDecorator(
              decoration: const InputDecoration(
                labelText: '기간 (선택)',
                border: OutlineInputBorder(),
                suffixIcon: Icon(Icons.calendar_month_outlined),
              ),
              child: Text(
                _dateStart != null
                    ? '${_fmtDate(_dateStart!)} ~ ${_fmtDate(_dateEnd!)}'
                    : '날짜 선택',
                style: TextStyle(
                  color: _dateStart != null ? null : Colors.grey[600],
                  fontSize: 14,
                ),
              ),
            ),
          ),
        ],
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.pop(context), child: const Text('취소')),
        FilledButton(
          onPressed: () async {
            if (_titleCtrl.text.trim().isEmpty) return;
            final nav = Navigator.of(context);
            final now = DateTime.now().millisecondsSinceEpoch;
            await _albumDao.insert(Album(
              eventType: _eventType,
              title: _titleCtrl.text.trim(),
              dateStart: _dateStart?.millisecondsSinceEpoch,
              dateEnd: _dateEnd?.millisecondsSinceEpoch,
              createdAt: now,
            ));
            widget.onCreated?.call();
            nav.pop();
          },
          child: const Text('만들기'),
        ),
      ],
    );
  }

  static String _fmtDate(DateTime d) =>
      '${d.year}.${d.month.toString().padLeft(2, '0')}.${d.day.toString().padLeft(2, '0')}';
}
