import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:photo_view/photo_view.dart';
import 'package:photo_view/photo_view_gallery.dart';
import 'package:video_player/video_player.dart';
import 'package:chewie/chewie.dart';
import 'package:share_plus/share_plus.dart';
import '../models/media_item.dart';
import '../../core/db/media_dao.dart';
import '../../core/services/storage_service.dart';

class MediaViewerScreen extends StatefulWidget {
  final List<MediaItem> items;
  final int initialIndex;

  const MediaViewerScreen({
    super.key,
    required this.items,
    this.initialIndex = 0,
  });

  @override
  State<MediaViewerScreen> createState() => _MediaViewerScreenState();
}

class _MediaViewerScreenState extends State<MediaViewerScreen> {
  late int _current;
  late PageController _pageCtrl;
  late List<MediaItem> _items;
  bool _uiVisible = true;
  bool _deleting = false;

  final _mediaDao = MediaDao();

  @override
  void initState() {
    super.initState();
    _items = List.from(widget.items);
    _current = widget.initialIndex.clamp(0, _items.length - 1);
    _pageCtrl = PageController(initialPage: _current);
    // edgeToEdge: 네비게이션 바를 항상 표시 (immersiveSticky는 백버튼 영역을 가림)
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  }

  @override
  void dispose() {
    _pageCtrl.dispose();
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    super.dispose();
  }

  void _toggleUI() => setState(() => _uiVisible = !_uiVisible);

  Future<void> _deleteCurrentItem() async {
    final item = _items[_current];
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('삭제'),
        content: const Text('이 미디어를 삭제할까요?\n파일도 함께 삭제됩니다.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('취소'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('삭제'),
          ),
        ],
      ),
    );
    if (ok != true || !mounted) return;

    setState(() => _deleting = true);
    await StorageService.deleteFile(item.filePath);
    if (item.thumbPath != null) {
      await StorageService.deleteFile(item.thumbPath!);
    }
    if (item.id != null) await _mediaDao.delete(item.id!);

    if (!mounted) return;
    setState(() {
      _items.removeAt(_current);
      _deleting = false;
      if (_items.isEmpty) {
        Navigator.pop(context, 'deleted');
        return;
      }
      _current = _current.clamp(0, _items.length - 1);
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_items.isEmpty) return const SizedBox.shrink();
    final item = _items[_current];

    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          // ── 미디어 콘텐츠 ──
          GestureDetector(
            onTap: _toggleUI,
            child: item.mediaType == MediaType.video
                ? _VideoView(item: item)
                : PhotoViewGallery.builder(
                    pageController: _pageCtrl,
                    itemCount: _items.length,
                    onPageChanged: (i) => setState(() => _current = i),
                    builder: (ctx, index) {
                      final it = _items[index];
                      return PhotoViewGalleryPageOptions(
                        imageProvider: FileImage(File(it.filePath)),
                        minScale: PhotoViewComputedScale.contained,
                        maxScale: PhotoViewComputedScale.covered * 3,
                        heroAttributes: PhotoViewHeroAttributes(
                          tag: 'media_${it.id}',
                        ),
                      );
                    },
                    backgroundDecoration: const BoxDecoration(
                      color: Colors.black,
                    ),
                    loadingBuilder: (ctx, event) => const Center(
                      child: CircularProgressIndicator(color: Colors.white54),
                    ),
                  ),
          ),

          // ── 상단 AppBar ──
          AnimatedSlide(
            duration: const Duration(milliseconds: 200),
            offset: _uiVisible ? Offset.zero : const Offset(0, -1),
            child: AnimatedOpacity(
              duration: const Duration(milliseconds: 200),
              opacity: _uiVisible ? 1 : 0,
              child: SafeArea(
                child: Container(
                  height: 56,
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [Colors.black54, Colors.transparent],
                    ),
                  ),
                  child: Row(
                    children: [
                      IconButton(
                        icon: const Icon(Icons.arrow_back, color: Colors.white),
                        onPressed: () => Navigator.pop(context),
                      ),
                      const Spacer(),
                      if (item.title.isNotEmpty)
                        Expanded(
                          child: Text(
                            item.title,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 14,
                            ),
                            overflow: TextOverflow.ellipsis,
                            textAlign: TextAlign.center,
                          ),
                        ),
                      const Spacer(),
                      IconButton(
                        icon: const Icon(Icons.share, color: Colors.white),
                        onPressed: () => _shareItem(item),
                      ),
                      IconButton(
                        icon: _deleting
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : const Icon(
                                Icons.delete_outline,
                                color: Colors.redAccent,
                              ),
                        onPressed: _deleting ? null : _deleteCurrentItem,
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),

          // ── 하단 정보 ──
          AnimatedSlide(
            duration: const Duration(milliseconds: 200),
            offset: _uiVisible ? Offset.zero : const Offset(0, 1),
            child: AnimatedOpacity(
              duration: const Duration(milliseconds: 200),
              opacity: _uiVisible ? 1 : 0,
              child: Align(
                alignment: Alignment.bottomCenter,
                child: SafeArea(
                  child: Container(
                    decoration: const BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.bottomCenter,
                        end: Alignment.topCenter,
                        colors: [Colors.black54, Colors.transparent],
                      ),
                    ),
                    padding: const EdgeInsets.fromLTRB(16, 24, 16, 16),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // 페이지 인디케이터
                        if (_items.length > 1)
                          Center(
                            child: Text(
                              '${_current + 1} / ${_items.length}',
                              style: const TextStyle(
                                color: Colors.white70,
                                fontSize: 12,
                              ),
                            ),
                          ),
                        if (item.note.isNotEmpty) ...[
                          const SizedBox(height: 8),
                          Text(
                            item.note,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 13,
                            ),
                          ),
                        ],
                        if (item.countryCode.isNotEmpty ||
                            item.region.isNotEmpty) ...[
                          const SizedBox(height: 4),
                          Row(
                            children: [
                              const Icon(
                                Icons.location_on,
                                size: 12,
                                color: Colors.white70,
                              ),
                              const SizedBox(width: 4),
                              Text(
                                '${item.countryCode} ${item.region}'.trim(),
                                style: const TextStyle(
                                  color: Colors.white70,
                                  fontSize: 12,
                                ),
                              ),
                            ],
                          ),
                        ],
                        const SizedBox(height: 4),
                        Text(
                          _formatDate(item.takenAt),
                          style: const TextStyle(
                            color: Colors.white54,
                            fontSize: 11,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _shareItem(MediaItem item) async {
    await Share.shareXFiles([
      XFile(item.filePath),
    ], text: item.title.isNotEmpty ? item.title : null);
  }

  static String _formatDate(int ms) {
    final dt = DateTime.fromMillisecondsSinceEpoch(ms);
    return '${dt.year}.${dt.month.toString().padLeft(2, '0')}.${dt.day.toString().padLeft(2, '0')} '
        '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }
}

// ── 영상 플레이어 ──
class _VideoView extends StatefulWidget {
  final MediaItem item;
  const _VideoView({required this.item});

  @override
  State<_VideoView> createState() => _VideoViewState();
}

class _VideoViewState extends State<_VideoView> {
  VideoPlayerController? _videoCtrl;
  ChewieController? _chewieCtrl;
  bool _initialized = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _initPlayer();
  }

  Future<void> _initPlayer() async {
    final file = File(widget.item.filePath);
    if (!await file.exists()) {
      if (mounted) {
        setState(() => _errorMessage = '영상 파일을 찾을 수 없습니다.');
      }
      return;
    }

    final ctrl = VideoPlayerController.file(file);
    try {
      await ctrl.initialize();
      if (!mounted) {
        await ctrl.dispose();
        return;
      }

      // 일부 단말에서는 Chewie의 autoPlay만으로 재생이 시작되지 않아
      // 초기화 직후 명시적으로 재생을 요청한다.
      await ctrl.setLooping(false);
      await ctrl.play();

      final aspectRatio = ctrl.value.aspectRatio > 0
          ? ctrl.value.aspectRatio
          : 16 / 9;
      final chewie = ChewieController(
        videoPlayerController: ctrl,
        autoPlay: false,
        looping: false,
        aspectRatio: aspectRatio,
        materialProgressColors: ChewieProgressColors(
          playedColor: Colors.white,
          bufferedColor: Colors.white38,
          backgroundColor: Colors.white12,
          handleColor: Colors.white,
        ),
      );
      setState(() {
        _videoCtrl = ctrl;
        _chewieCtrl = chewie;
        _initialized = true;
      });
    } catch (e) {
      await ctrl.dispose();
      if (mounted) {
        setState(() => _errorMessage = '이 영상 형식은 재생할 수 없습니다.');
      }
    }
  }

  @override
  void dispose() {
    _chewieCtrl?.dispose();
    _videoCtrl?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_errorMessage != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.error_outline, color: Colors.white70, size: 42),
              const SizedBox(height: 12),
              Text(
                _errorMessage!,
                style: const TextStyle(color: Colors.white70),
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      );
    }
    if (!_initialized) {
      return const Center(
        child: CircularProgressIndicator(color: Colors.white54),
      );
    }
    return Center(child: Chewie(controller: _chewieCtrl!));
  }
}
