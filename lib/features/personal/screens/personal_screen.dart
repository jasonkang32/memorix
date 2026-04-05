import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/personal_provider.dart';
import '../../../core/db/album_dao.dart';
import '../../../core/db/media_dao.dart';
import '../../../core/services/media_capture_service.dart';
import '../../../core/services/media_save_service.dart';
import '../../import/screens/messenger_import_screen.dart';
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
    final selectedAlbumId = ref.watch(selectedAlbumIdProvider);

    return Scaffold(
      appBar: _searching ? _searchBar() : _normalBar(),
      body: _searching
          ? _buildSearchBody()
          : Column(
              children: [
                albumAsync.when(
                  loading: () => const SizedBox(
                      height: 80,
                      child: Center(child: CircularProgressIndicator())),
                  error: (e, _) => const SizedBox(),
                  data: (albums) =>
                      _AlbumChipRow(albums: albums, selectedId: selectedAlbumId),
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
          icon: const Icon(Icons.create_new_folder_outlined),
          onPressed: () => _showCreateAlbumDialog(context, ref),
        ),
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
      builder: (_) => _CreateAlbumDialog(onCreated: () => ref.invalidate(albumListProvider)),
    );
  }

  Future<void> _onAddMedia(BuildContext context, WidgetRef ref) async {
    List<CapturedMedia>? capturedList = await CaptureBottomSheet.show(context);

    // 빈 리스트(메신저 선택)이면 메신저 화면으로 이동, null(취소)이면 무시
    if (capturedList != null && capturedList.isEmpty && context.mounted) {
      capturedList = await Navigator.push<List<CapturedMedia>>(
        context,
        MaterialPageRoute(
          builder: (_) => const MessengerImportScreen(space: MediaSpace.personal),
        ),
      );
    }

    if (capturedList == null || capturedList.isEmpty || !context.mounted) return;

    final albumId = ref.read(selectedAlbumIdProvider);
    final results = await MediaSaveService.saveAll(
      captured: capturedList,
      space: MediaSpace.personal,
      albumId: albumId,
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
    final changed = await Navigator.push<dynamic>(
      context,
      MaterialPageRoute(
        builder: (_) => MediaDetailScreen(items: savedItems, initialIndex: 0),
      ),
    );
    if (changed != null && context.mounted) ref.invalidate(personalMediaProvider);
  }

  void _openViewer(BuildContext context, List<MediaItem> items, int index) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => MediaViewerScreen(items: items, initialIndex: index),
      ),
    );
  }

  Future<void> _openDetail(BuildContext context, List<MediaItem> group, int index) async {
    final changed = await Navigator.push<dynamic>(
      context,
      MaterialPageRoute(
        builder: (_) => MediaDetailScreen(items: group, initialIndex: index),
      ),
    );
    if (changed != null) ref.invalidate(personalMediaProvider);
  }
}

class _AlbumChipRow extends ConsumerWidget {
  final List<Album> albums;
  final int? selectedId;
  const _AlbumChipRow({required this.albums, required this.selectedId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return SizedBox(
      height: 48,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        children: [
          ChoiceChip(
            label: const Text('전체'),
            selected: selectedId == null,
            onSelected: (_) => ref.read(selectedAlbumIdProvider.notifier).state = null,
          ),
          ...albums.map((a) => Padding(
                padding: const EdgeInsets.only(left: 8),
                child: GestureDetector(
                  onLongPress: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                        builder: (_) => AlbumDetailScreen(album: a)),
                  ),
                  child: ChoiceChip(
                    label: Text(a.title),
                    selected: selectedId == a.id,
                    onSelected: (_) =>
                        ref.read(selectedAlbumIdProvider.notifier).state = a.id,
                  ),
                ),
              )),
        ],
      ),
    );
  }
}

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
              child: const Icon(Icons.favorite_outline, size: 44, color: Colors.white),
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

class _CreateAlbumDialog extends StatefulWidget {
  final VoidCallback? onCreated;
  const _CreateAlbumDialog({this.onCreated});

  @override
  State<_CreateAlbumDialog> createState() => _CreateAlbumDialogState();
}

// ── Personal Space 잠금 게이트 ──
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

class _CreateAlbumDialogState extends State<_CreateAlbumDialog> {
  final _titleCtrl = TextEditingController();
  final _albumDao = AlbumDao();
  EventType _eventType = EventType.travel;

  @override
  void dispose() {
    _titleCtrl.dispose();
    super.dispose();
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
            decoration: const InputDecoration(labelText: '앨범 이름'),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<EventType>(
            initialValue: _eventType,
            decoration: const InputDecoration(labelText: '이벤트 유형'),
            items: EventType.values
                .map((e) => DropdownMenuItem(value: e, child: Text(e.name)))
                .toList(),
            onChanged: (v) => setState(() => _eventType = v!),
          ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('취소')),
        FilledButton(
          onPressed: () async {
            if (_titleCtrl.text.trim().isEmpty) return;
            final nav = Navigator.of(context);
            final now = DateTime.now().millisecondsSinceEpoch;
            await _albumDao.insert(Album(
              eventType: _eventType,
              title: _titleCtrl.text.trim(),
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
}
