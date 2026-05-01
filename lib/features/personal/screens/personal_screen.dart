import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/personal_provider.dart';
import '../../../core/db/album_dao.dart';
import '../../../core/db/media_dao.dart';
import '../../../core/services/media_capture_service.dart';
import '../../../core/services/original_media_cleanup_service.dart';
import '../../../core/services/media_save_service.dart';
import '../../../features/auth/providers/secret_lock_provider.dart';
import '../../../features/home/providers/home_provider.dart';
import '../../../shared/models/album.dart';
import '../../../shared/models/media_item.dart';
import '../../../shared/widgets/capture_bottom_sheet.dart';
import '../../../shared/widgets/media_timeline.dart';
import '../../../shared/screens/media_detail_screen.dart';
import '../../../shared/screens/media_viewer_screen.dart';
import 'album_detail_screen.dart';
import '../../../shared/theme/app_theme.dart';

class PersonalScreen extends ConsumerStatefulWidget {
  const PersonalScreen({super.key});

  @override
  ConsumerState<PersonalScreen> createState() => _PersonalScreenState();
}

class _PersonalScreenState extends ConsumerState<PersonalScreen> {
  bool _searching = false;
  bool _isImporting = false;
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
    final results = await _dao.quickSearch(q, 'secret');
    setState(() => _searchResults = results);
  }

  @override
  Widget build(BuildContext context) {
    final lockState = ref.watch(secretLockProvider);

    if (lockState == SecretLockState.locked) {
      return _SecretLockGate(
        onUnlock: () => ref.read(secretLockProvider.notifier).tryUnlock(),
      );
    }

    final albumAsync = ref.watch(albumListProvider);

    return Scaffold(
      appBar: _searching ? _searchBar() : _normalBar(),
      floatingActionButton: _searching
          ? null
          : FloatingActionButton(
              onPressed: _isImporting ? null : () => _onAddMedia(context, ref),
              child: _isImporting
                  ? const SizedBox(
                      width: 24,
                      height: 24,
                      child: CircularProgressIndicator(
                        color: Colors.white,
                        strokeWidth: 2.5,
                      ),
                    )
                  : const Icon(Icons.add_a_photo_outlined),
            ),
      body: _searching
          ? _buildSearchBody()
          : _albumGridMode
          ? albumAsync.when(
              loading: () => const Center(child: CircularProgressIndicator()),
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
                    child: Center(child: CircularProgressIndicator()),
                  ),
                  error: (e, _) => const SizedBox(),
                  data: (albums) => _AlbumChipRow(albums: albums),
                ),
                Expanded(
                  child: Consumer(
                    builder: (context, ref, _) {
                      final mediaAsync = ref.watch(secretMediaProvider);
                      return mediaAsync.when(
                        loading: () =>
                            const Center(child: CircularProgressIndicator()),
                        error: (e, _) => Center(child: Text('오류: $e')),
                        data: (items) => items.isEmpty
                            ? const _EmptySecretView()
                            : MediaTimeline(
                                items: items,
                                onTap: (group, idx) =>
                                    _openDetail(context, group, idx),
                                onLongPress: (item) =>
                                    _openViewer(context, [item], 0),
                                onRefresh: () async =>
                                    ref.invalidate(secretMediaProvider),
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
                colors: [AppColors.brandSecondary, AppColors.workAccent],
              ),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.lock_rounded, color: Colors.white, size: 14),
                SizedBox(width: 4),
                Text(
                  'Secret',
                  style: TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w800,
                    fontSize: 14,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
      actions: [
        IconButton(
          icon: const Icon(Icons.search_rounded),
          onPressed: () => setState(() => _searching = true),
        ),
        IconButton(
          icon: Icon(
            _albumGridMode
                ? Icons.view_stream_outlined
                : Icons.grid_view_outlined,
          ),
          tooltip: _albumGridMode ? '타임라인' : '앨범 목록',
          onPressed: () => setState(() => _albumGridMode = !_albumGridMode),
        ),
        IconButton(
          icon: const Icon(Icons.create_new_folder_outlined),
          onPressed: () => _showCreateAlbumDialog(context, ref),
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
          hintText: 'Secret 보관함 검색...',
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
      return Center(
        child: Text(
          '검색어를 입력하세요',
          style: TextStyle(
            color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.6),
            fontSize: 14,
          ),
        ),
      );
    }
    final results = _searchResults ?? [];
    if (results.isEmpty) {
      return Center(
        child: Text(
          '검색 결과가 없습니다',
          style: TextStyle(
            color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.6),
            fontSize: 14,
          ),
        ),
      );
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
      builder: (_) => _CreateAlbumDialog(
        onCreated: () => ref.invalidate(albumListProvider),
      ),
    );
  }

  Future<void> _onAddMedia(BuildContext context, WidgetRef ref) async {
    List<CapturedMedia>? capturedList = await CaptureBottomSheet.show(
      context,
      space: MediaSpace.secret,
    );

    if (capturedList == null || capturedList.isEmpty) {
      if (mounted) setState(() => _isImporting = false);
      return;
    }
    if (!context.mounted) {
      if (mounted) setState(() => _isImporting = false);
      return;
    }

    final total = capturedList.length;
    final progressNotifier = ValueNotifier<int>(0);
    BuildContext? progressDialogContext;
    final dialogReady = Completer<void>();
    void closeProgressDialog() {
      final dialogContext = progressDialogContext;
      progressDialogContext = null;
      if (dialogContext != null && dialogContext.mounted) {
        Navigator.of(dialogContext).pop();
      }
    }

    if (mounted) setState(() => _isImporting = true);

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) {
        progressDialogContext = dialogContext;
        if (!dialogReady.isCompleted) {
          dialogReady.complete();
        }
        return PopScope(
          canPop: false,
          child: AlertDialog(
            content: ValueListenableBuilder<int>(
              valueListenable: progressNotifier,
              builder: (_, done, _) => Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const CircularProgressIndicator(),
                  const SizedBox(height: 16),
                  Text(total > 1 ? '$done / $total 저장 중...' : '저장 중...'),
                  const SizedBox(height: 4),
                  Text(
                    'AI 분석은 백그라운드에서 진행됩니다',
                    style: TextStyle(
                      fontSize: 11,
                      color: Theme.of(
                        context,
                      ).colorScheme.onSurface.withValues(alpha: 0.6),
                    ),
                  ),
                  if (total > 1) ...[
                    const SizedBox(height: 8),
                    LinearProgressIndicator(
                      value: total > 0 ? done / total : 0,
                    ),
                  ],
                ],
              ),
            ),
          ),
        );
      },
    );

    try {
      // 다이얼로그 route가 실제로 mount되기 전에 빠르게 닫으면서
      // 검은 화면 barrier만 남는 race를 방지한다.
      await dialogReady.future.timeout(
        const Duration(milliseconds: 300),
        onTimeout: () {},
      );

      final results = await MediaSaveService.saveAll(
        captured: capturedList,
        space: MediaSpace.secret,
        onProgress: (done, _) => progressNotifier.value = done,
        onEnhancementComplete: () {
          if (context.mounted) {
            ref.invalidate(secretMediaProvider);
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('AI 태그 분석 완료'),
                duration: Duration(seconds: 2),
              ),
            );
          }
        },
      );

      closeProgressDialog();

      if (mounted) {
        ref.invalidate(secretMediaProvider);
        ref.invalidate(homeSummaryProvider);
      }

      if (!context.mounted) return;

      if (results.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('저장된 항목이 없습니다.'),
            backgroundColor: Colors.orange,
          ),
        );
        return;
      }

      if (results.length > 1) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${results.length}개 저장됨. 첫 번째 항목을 편집합니다.'),
            duration: const Duration(seconds: 2),
          ),
        );
      }
      final savedItems = results.map((r) => r.item).toList();
      final detailChanged = await Navigator.push<dynamic>(
        context,
        MaterialPageRoute(
          builder: (_) => MediaDetailScreen(items: savedItems, initialIndex: 0),
        ),
      );
      if (context.mounted) {
        ref.invalidate(secretMediaProvider);
        ref.invalidate(homeSummaryProvider);
      }
      if (context.mounted && detailChanged != null) {
        await _offerDeleteOriginals(context, capturedList);
      }
    } catch (e) {
      closeProgressDialog();
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('미디어 저장 실패: $e'), backgroundColor: Colors.red),
        );
      }
    } finally {
      if (mounted) setState(() => _isImporting = false);
      progressNotifier.dispose();
    }
  }

  Future<void> _offerDeleteOriginals(
    BuildContext context,
    List<CapturedMedia> capturedList,
  ) async {
    if (capturedList.isEmpty) return;

    final shouldDelete = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('원본 사진을 정리할까요?'),
        content: const Text(
          '선택한 파일은 메모릭스 보관함에 복사되었습니다. '
          '갤러리에 원본을 그대로 두면 같은 사진이 두 곳에 남습니다.\n\n'
          '메모릭스에만 보관하려면 원본 삭제를 권장합니다.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('그대로 두기'),
          ),
          FilledButton.icon(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            icon: const Icon(Icons.delete_outline),
            label: const Text('원본 삭제'),
          ),
        ],
      ),
    );

    if (shouldDelete != true || !context.mounted) return;

    final result = await OriginalMediaCleanupService.deleteOriginals(
      capturedList,
    );
    if (!context.mounted) return;

    final message = result.failed == 0
        ? '${result.deleted}개 원본을 삭제했습니다.'
        : '${result.deleted}개 삭제, ${result.failed}개는 기기 권한 제한으로 삭제하지 못했습니다.';
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: result.failed == 0 ? null : Colors.orange,
      ),
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
    List<MediaItem> group,
    int index,
  ) async {
    final changed = await Navigator.push<dynamic>(
      context,
      MaterialPageRoute(
        builder: (_) => MediaDetailScreen(items: group, initialIndex: index),
      ),
    );
    if (changed != null) ref.invalidate(secretMediaProvider);
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
                errorBuilder: (_, _, _) => const Icon(Icons.photo, size: 16),
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
                MaterialPageRoute(builder: (_) => AlbumDetailScreen(album: a)),
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
  const _AlbumGridView({required this.albums, required this.onAlbumUpdated});

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
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Text(
            '앨범이 없습니다\n우측 상단 버튼으로 앨범을 만드세요',
            textAlign: TextAlign.center,
            style: TextStyle(
              color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.6),
              fontSize: 14,
              height: 1.7,
            ),
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
            MaterialPageRoute(builder: (_) => AlbumDetailScreen(album: album)),
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
  const _AlbumCard({
    required this.album,
    required this.coverPath,
    required this.onTap,
  });

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
                      errorBuilder: (_, _, _) => _placeholder(context, emoji),
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
                      fontWeight: FontWeight.w600,
                      fontSize: 14,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (album.dateStart != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      _fmtDateRange(album.dateStart, album.dateEnd),
                      style: TextStyle(
                        fontSize: 11,
                        color: Theme.of(
                          context,
                        ).colorScheme.onSurface.withValues(alpha: 0.7),
                      ),
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
      child: Center(child: Text(emoji, style: const TextStyle(fontSize: 40))),
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

// ── Secret 보관함 빈 상태 ──
class _EmptySecretView extends StatelessWidget {
  const _EmptySecretView();

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
                  colors: [AppColors.brandSecondary, AppColors.workAccent],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(28),
              ),
              child: const Icon(
                Icons.lock_rounded,
                size: 44,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 24),
            Text(
              'Secret 보관함이 비어 있어요',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            Text(
              '암호화되어 저장되며\n갤러리·파일 탐색기에 노출되지 않아요',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 13,
                color: Theme.of(
                  context,
                ).colorScheme.onSurface.withValues(alpha: 0.6),
                height: 1.6,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              '우측 하단 + 버튼으로 사진을 추가하세요',
              style: TextStyle(
                fontSize: 12,
                color: Theme.of(
                  context,
                ).colorScheme.onSurface.withValues(alpha: 0.4),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SecretLockGate extends StatefulWidget {
  final Future<bool> Function() onUnlock;
  const _SecretLockGate({required this.onUnlock});

  @override
  State<_SecretLockGate> createState() => _SecretLockGateState();
}

class _SecretLockGateState extends State<_SecretLockGate> {
  bool _unlocking = false;
  bool _autoTried = false;

  @override
  void initState() {
    super.initState();
    // 진입 즉시 자동으로 1회 인증 시도 — 사용자가 버튼을 다시 누르지 않도록
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_autoTried && mounted) {
        _autoTried = true;
        _tryUnlock();
      }
    });
  }

  Future<void> _tryUnlock() async {
    if (_unlocking) return;
    setState(() => _unlocking = true);
    await widget.onUnlock();
    if (mounted) setState(() => _unlocking = false);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('🔒 Secret 보관함')),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.lock_outlined,
              size: 64,
              color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.4),
            ),
            const SizedBox(height: 20),
            const Text(
              'Secret 보관함이 잠겨 있어요',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
            ),
            const SizedBox(height: 8),
            Text(
              '생체인증 또는 기기 비밀번호로 잠금 해제',
              style: TextStyle(
                color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.7),
                fontSize: 13,
              ),
            ),
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
                  color: _dateStart != null
                      ? null
                      : Theme.of(
                          context,
                        ).colorScheme.onSurface.withValues(alpha: 0.7),
                  fontSize: 14,
                ),
              ),
            ),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('취소'),
        ),
        FilledButton(
          onPressed: () async {
            if (_titleCtrl.text.trim().isEmpty) return;
            final nav = Navigator.of(context);
            final now = DateTime.now().millisecondsSinceEpoch;
            await _albumDao.insert(
              Album(
                eventType: _eventType,
                title: _titleCtrl.text.trim(),
                dateStart: _dateStart?.millisecondsSinceEpoch,
                dateEnd: _dateEnd?.millisecondsSinceEpoch,
                createdAt: now,
              ),
            );
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
