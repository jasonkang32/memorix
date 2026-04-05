import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/personal_provider.dart';
import '../../../core/db/album_dao.dart';
import '../../../core/db/media_dao.dart';
import '../../../core/services/media_save_service.dart';
import '../../../shared/models/album.dart';
import '../../../shared/models/media_item.dart';
import '../../../shared/widgets/capture_bottom_sheet.dart';
import '../../../shared/widgets/media_thumbnail.dart';
import '../../../shared/screens/media_detail_screen.dart';
import '../../../shared/screens/media_viewer_screen.dart';

class AlbumDetailScreen extends ConsumerStatefulWidget {
  final Album album;
  const AlbumDetailScreen({super.key, required this.album});

  @override
  ConsumerState<AlbumDetailScreen> createState() => _AlbumDetailScreenState();
}

class _AlbumDetailScreenState extends ConsumerState<AlbumDetailScreen> {
  List<MediaItem> _items = [];
  bool _loading = true;
  final _mediaDao = MediaDao();
  final _albumDao = AlbumDao();

  @override
  void initState() {
    super.initState();
    _loadItems();
  }

  Future<void> _loadItems() async {
    final items = await _mediaDao.findPersonal(albumId: widget.album.id);
    setState(() {
      _items = items;
      _loading = false;
    });
  }

  Future<void> _onAddMedia() async {
    final capturedList = await CaptureBottomSheet.show(context);
    if (capturedList == null || capturedList.isEmpty || !mounted) return;
    await MediaSaveService.saveAll(
      captured: capturedList,
      space: MediaSpace.personal,
      albumId: widget.album.id,
    );
    _loadItems();
    ref.invalidate(personalMediaProvider);
  }

  Future<void> _setCover(MediaItem item) async {
    await _albumDao.update(
      Album(
        id: widget.album.id,
        eventType: widget.album.eventType,
        title: widget.album.title,
        dateStart: widget.album.dateStart,
        dateEnd: widget.album.dateEnd,
        coverMediaId: item.id,
        memo: widget.album.memo,
        createdAt: widget.album.createdAt,
      ),
    );
    ref.invalidate(albumListProvider);
    if (mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('커버 사진이 변경되었습니다')));
    }
  }

  Future<void> _editAlbum() async {
    await showDialog(
      context: context,
      builder: (ctx) => _AlbumEditDialog(
        album: widget.album,
        onSaved: (updated) async {
          await _albumDao.update(updated);
          ref.invalidate(albumListProvider);
          if (mounted) setState(() {});
        },
      ),
    );
  }

  Future<void> _deleteAlbum() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('앨범 삭제'),
        content: Text(
          '"${widget.album.title}" 앨범을 삭제합니다.\n앨범 내 미디어는 "전체"에서 볼 수 있습니다.',
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('취소')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('삭제'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    await _albumDao.delete(widget.album.id!);
    ref.invalidate(albumListProvider);
    ref.invalidate(personalMediaProvider);
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final eventEmoji = _eventEmoji(widget.album.eventType);

    return Scaffold(
      appBar: AppBar(
        title: Text('$eventEmoji ${widget.album.title}'),
        actions: [
          PopupMenuButton<String>(
            onSelected: (v) {
              if (v == 'edit') _editAlbum();
              if (v == 'delete') _deleteAlbum();
            },
            itemBuilder: (_) => [
              const PopupMenuItem(
                value: 'edit',
                child: Row(children: [
                  Icon(Icons.edit_outlined),
                  SizedBox(width: 8),
                  Text('앨범 편집'),
                ]),
              ),
              const PopupMenuItem(
                value: 'delete',
                child: Row(children: [
                  Icon(Icons.delete_outline, color: Colors.red),
                  SizedBox(width: 8),
                  Text('앨범 삭제', style: TextStyle(color: Colors.red)),
                ]),
              ),
            ],
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _onAddMedia,
        child: const Icon(Icons.add_a_photo),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _items.isEmpty
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.photo_album_outlined,
                          size: 64, color: Colors.grey[300]),
                      const SizedBox(height: 12),
                      Text('아직 사진이 없습니다',
                          style: TextStyle(color: Colors.grey[500])),
                    ],
                  ),
                )
              : GridView.builder(
                  padding: const EdgeInsets.fromLTRB(8, 8, 8, 80),
                  gridDelegate:
                      const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 3,
                    crossAxisSpacing: 4,
                    mainAxisSpacing: 4,
                  ),
                  itemCount: _items.length,
                  itemBuilder: (ctx, index) {
                    final item = _items[index];
                    return MediaThumbnailCard(
                      item: item,
                      onTap: () => Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => MediaViewerScreen(
                              items: _items, initialIndex: index),
                        ),
                      ),
                      onLongPress: () => _showItemMenu(item),
                    );
                  },
                ),
    );
  }

  void _showItemMenu(MediaItem item) {
    showModalBottomSheet(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_outlined),
              title: const Text('커버로 설정'),
              onTap: () {
                Navigator.pop(ctx);
                _setCover(item);
              },
            ),
            ListTile(
              leading: const Icon(Icons.edit_outlined),
              title: const Text('상세 편집'),
              onTap: () async {
                Navigator.pop(ctx);
                await Navigator.push(
                  context,
                  MaterialPageRoute(
                      builder: (_) => MediaDetailScreen(items: [item], initialIndex: 0)),
                );
                _loadItems();
              },
            ),
          ],
        ),
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
}

// ── 앨범 편집 다이얼로그 ──
class _AlbumEditDialog extends StatefulWidget {
  final Album album;
  final Future<void> Function(Album) onSaved;
  const _AlbumEditDialog({required this.album, required this.onSaved});

  @override
  State<_AlbumEditDialog> createState() => _AlbumEditDialogState();
}

class _AlbumEditDialogState extends State<_AlbumEditDialog> {
  late final TextEditingController _titleCtrl;
  late final TextEditingController _memoCtrl;
  late EventType _eventType;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _titleCtrl = TextEditingController(text: widget.album.title);
    _memoCtrl = TextEditingController(text: widget.album.memo);
    _eventType = widget.album.eventType;
  }

  @override
  void dispose() {
    _titleCtrl.dispose();
    _memoCtrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_titleCtrl.text.trim().isEmpty) return;
    setState(() => _saving = true);
    final updated = Album(
      id: widget.album.id,
      title: _titleCtrl.text.trim(),
      eventType: _eventType,
      memo: _memoCtrl.text.trim(),
      coverMediaId: widget.album.coverMediaId,
      dateStart: widget.album.dateStart,
      dateEnd: widget.album.dateEnd,
      createdAt: widget.album.createdAt,
    );
    await widget.onSaved(updated);
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('앨범 편집'),
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
          TextField(
            controller: _memoCtrl,
            maxLines: 2,
            decoration: const InputDecoration(
              labelText: '메모 (선택)',
              border: OutlineInputBorder(),
            ),
          ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('취소')),
        FilledButton(
          onPressed: _saving ? null : _save,
          child: _saving
              ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('저장'),
        ),
      ],
    );
  }
}
