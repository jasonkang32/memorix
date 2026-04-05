import 'dart:io';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:permission_handler/permission_handler.dart';
import '../../../core/services/media_capture_service.dart';
import '../../../shared/models/media_item.dart';
import '../services/messenger_scan_service.dart';

class MessengerImportScreen extends StatefulWidget {
  final MediaSpace? space;
  const MessengerImportScreen({super.key, this.space});

  @override
  State<MessengerImportScreen> createState() => _MessengerImportScreenState();
}

class _MessengerImportScreenState extends State<MessengerImportScreen>
    with SingleTickerProviderStateMixin {
  TabController? _tabCtrl;
  List<MessengerApp> _apps = [];
  bool _loading = true;
  bool _hasPermission = false;

  // 앱별 파일 캐시
  final Map<String, List<MessengerFile>> _fileCache = {};
  final Map<String, bool> _scanning = {};

  // 선택 상태
  final Set<String> _selected = {};

  bool _importing = false;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    // 권한 확인
    final status = await _requestPermission();
    if (!status) {
      setState(() {
        _hasPermission = false;
        _loading = false;
      });
      return;
    }

    final apps = await MessengerScanService.detectInstalled();
    setState(() {
      _hasPermission = true;
      _apps = apps;
      _loading = false;
      if (apps.isNotEmpty) {
        _tabCtrl = TabController(length: apps.length, vsync: this);
        _tabCtrl!.addListener(() {
          if (!_tabCtrl!.indexIsChanging) {
            _loadFiles(_apps[_tabCtrl!.index]);
          }
        });
        _loadFiles(apps.first);
      }
    });
  }

  Future<bool> _requestPermission() async {
    // Android 11+: MANAGE_EXTERNAL_STORAGE
    if (Platform.isAndroid) {
      var status = await Permission.manageExternalStorage.status;
      if (!status.isGranted) {
        status = await Permission.manageExternalStorage.request();
      }
      if (status.isGranted) return true;

      // fallback: READ_EXTERNAL_STORAGE
      var readStatus = await Permission.storage.status;
      if (!readStatus.isGranted) {
        readStatus = await Permission.storage.request();
      }
      return readStatus.isGranted;
    }
    return false; // iOS 미지원
  }

  Future<void> _loadFiles(MessengerApp app) async {
    if (_fileCache.containsKey(app.id)) return;
    setState(() => _scanning[app.id] = true);
    final files = await MessengerScanService.scan(app);
    setState(() {
      _fileCache[app.id] = files;
      _scanning[app.id] = false;
    });
  }

  Future<void> _import() async {
    if (_selected.isEmpty) return;
    setState(() => _importing = true);

    final paths = _selected.toList();
    final captured = await MediaCaptureService.importFromPaths(paths);

    setState(() => _importing = false);
    if (mounted) {
      Navigator.pop(context, captured);
    }
  }

  @override
  void dispose() {
    _tabCtrl?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('메신저에서 가져오기'),
        actions: [
          if (_selected.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: FilledButton(
                onPressed: _importing ? null : _import,
                style: FilledButton.styleFrom(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                ),
                child: _importing
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white),
                      )
                    : Text('${_selected.length}개 가져오기'),
              ),
            ),
        ],
        bottom: _apps.isNotEmpty && _tabCtrl != null
            ? TabBar(
                controller: _tabCtrl,
                isScrollable: true,
                tabAlignment: TabAlignment.start,
                tabs: _apps
                    .map((a) => Tab(text: '${a.emoji} ${a.name}'))
                    .toList(),
              )
            : null,
      ),
      body: _buildBody(),
      // 원본 보관 안내 배너
      bottomNavigationBar: SafeArea(
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          color: const Color(0xFF00C896).withValues(alpha: 0.08),
          child: const Row(
            children: [
              Icon(Icons.shield_outlined, size: 14, color: Color(0xFF00C896)),
              SizedBox(width: 8),
              Expanded(
                child: Text(
                  '원본 파일이 삭제되어도 메모릭스에는 삭제되지 않습니다.',
                  style: TextStyle(
                    fontSize: 11,
                    color: Color(0xFF00A87C),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (!_hasPermission) {
      return _PermissionDeniedView(onRetry: _init);
    }

    if (Platform.isIOS) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.lock_outline, size: 48, color: Colors.grey),
              SizedBox(height: 16),
              Text(
                'iOS에서는 메신저 파일 직접 접근이 제한됩니다.\n갤러리에서 가져오기를 이용해주세요.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.grey, height: 1.6),
              ),
            ],
          ),
        ),
      );
    }

    if (_apps.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.chat_bubble_outline, size: 56, color: Colors.grey),
              SizedBox(height: 16),
              Text(
                '지원하는 메신저의 파일을 찾을 수 없습니다.\n카카오톡, 라인, 텔레그램이 설치되어 있는지 확인하세요.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.grey, height: 1.6),
              ),
            ],
          ),
        ),
      );
    }

    return TabBarView(
      controller: _tabCtrl,
      children: _apps.map((app) => _AppFileList(
        app: app,
        files: _fileCache[app.id],
        isScanning: _scanning[app.id] ?? false,
        selected: _selected,
        onToggle: (path) {
          setState(() {
            if (_selected.contains(path)) {
              _selected.remove(path);
            } else if (_selected.length < 20) {
              _selected.add(path);
            } else {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('최대 20개까지 선택할 수 있습니다')),
              );
            }
          });
        },
      )).toList(),
    );
  }
}

// ── 앱별 파일 목록 ────────────────────────────────────────────

class _AppFileList extends StatelessWidget {
  final MessengerApp app;
  final List<MessengerFile>? files;
  final bool isScanning;
  final Set<String> selected;
  final ValueChanged<String> onToggle;

  const _AppFileList({
    required this.app,
    required this.files,
    required this.isScanning,
    required this.selected,
    required this.onToggle,
  });

  @override
  Widget build(BuildContext context) {
    if (isScanning || files == null) {
      return const Center(child: CircularProgressIndicator());
    }
    if (files!.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(app.emoji, style: const TextStyle(fontSize: 48)),
              const SizedBox(height: 16),
              Text(
                '${app.name}에서 받은 파일이 없습니다',
                style: const TextStyle(color: Colors.grey),
              ),
            ],
          ),
        ),
      );
    }

    return ListView.builder(
      itemCount: files!.length,
      itemBuilder: (context, i) {
        final file = files![i];
        final isSelected = selected.contains(file.path);
        return _FileTile(
          file: file,
          isSelected: isSelected,
          onTap: () => onToggle(file.path),
        );
      },
    );
  }
}

class _FileTile extends StatelessWidget {
  final MessengerFile file;
  final bool isSelected;
  final VoidCallback onTap;

  const _FileTile({
    required this.file,
    required this.isSelected,
    required this.onTap,
  });

  static final _dateFmt = DateFormat('yyyy.MM.dd HH:mm');

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 120),
        color: isSelected
            ? const Color(0xFF00C896).withValues(alpha: 0.1)
            : Colors.transparent,
        child: ListTile(
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          leading: Stack(
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: SizedBox(
                  width: 56,
                  height: 56,
                  child: _buildThumb(file),
                ),
              ),
              if (isSelected)
                Positioned(
                  bottom: 0,
                  right: 0,
                  child: Container(
                    width: 20,
                    height: 20,
                    decoration: const BoxDecoration(
                      color: Color(0xFF00C896),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.check, color: Colors.white, size: 13),
                  ),
                ),
            ],
          ),
          title: Text(
            file.name,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 13,
              fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
            ),
          ),
          subtitle: Text(
            '${_dateFmt.format(file.modifiedAt)}  ·  ${_sizeLabel(file.sizeKb)}',
            style: const TextStyle(fontSize: 11, color: Colors.grey),
          ),
          trailing: _typeChip(file.type, isDark),
        ),
      ),
    );
  }

  Widget _buildThumb(MessengerFile file) {
    if (file.type == 'document') {
      return Container(
        color: Colors.blue[50],
        child: const Center(
          child: Icon(Icons.description, color: Colors.blueGrey, size: 28),
        ),
      );
    }
    final f = File(file.path);
    if (f.existsSync()) {
      if (file.type == 'photo') {
        return Image.file(f, fit: BoxFit.cover,
            errorBuilder: (ctx, err, st) => _placeholder());
      }
      // 영상은 아이콘
      return Container(
        color: Colors.grey[200],
        child: const Center(
          child: Icon(Icons.videocam_outlined, color: Colors.grey, size: 28),
        ),
      );
    }
    return _placeholder();
  }

  Widget _placeholder() => Container(
      color: Colors.grey[200],
      child: const Icon(Icons.image_outlined, color: Colors.grey));

  Widget _typeChip(String type, bool isDark) {
    final labels = {'photo': '사진', 'video': '영상', 'document': '문서'};
    final colors = {
      'photo': const Color(0xFF1A73E8),
      'video': const Color(0xFFFF6B9D),
      'document': const Color(0xFFFFB800),
    };
    final color = colors[type] ?? Colors.grey;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        labels[type] ?? type,
        style: TextStyle(fontSize: 11, color: color, fontWeight: FontWeight.w600),
      ),
    );
  }

  String _sizeLabel(int kb) {
    if (kb >= 1024) return '${(kb / 1024).toStringAsFixed(1)} MB';
    return '$kb KB';
  }
}

// ── 권한 거부 화면 ────────────────────────────────────────────

class _PermissionDeniedView extends StatelessWidget {
  final VoidCallback onRetry;
  const _PermissionDeniedView({required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.folder_off_outlined, size: 56, color: Colors.grey),
            const SizedBox(height: 16),
            const Text(
              '파일 접근 권한이 필요합니다',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 8),
            const Text(
              '메신저 파일을 가져오려면\n저장소 접근 권한을 허용해주세요.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.grey, height: 1.6),
            ),
            const SizedBox(height: 24),
            FilledButton.icon(
              onPressed: () async {
                await openAppSettings();
                onRetry();
              },
              icon: const Icon(Icons.settings_outlined),
              label: const Text('설정에서 권한 허용'),
            ),
          ],
        ),
      ),
    );
  }
}
