import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/work_provider.dart';
import '../widgets/work_filter_sheet.dart';
import '../../../core/db/media_dao.dart';
import '../../../core/services/media_capture_service.dart';
import '../../../core/services/media_save_service.dart';
import '../../../core/services/report_service.dart';
import '../../../shared/models/media_item.dart';
import '../../../shared/widgets/capture_bottom_sheet.dart';
import '../../../shared/widgets/media_timeline.dart';
import '../../../shared/screens/media_detail_screen.dart';
import '../../../shared/screens/media_viewer_screen.dart';
import 'report_screen.dart';

class WorkScreen extends ConsumerStatefulWidget {
  const WorkScreen({super.key});

  @override
  ConsumerState<WorkScreen> createState() => _WorkScreenState();
}

class _WorkScreenState extends ConsumerState<WorkScreen> {
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
    final results = await _dao.quickSearch(q, 'work');
    setState(() => _searchResults = results);
  }

  @override
  Widget build(BuildContext context) {
    final mediaAsync = ref.watch(workMediaProvider);
    final filter = ref.watch(workFilterProvider);
    final filterActive = filter.countryCode != null ||
        filter.region != null ||
        filter.mediaType != null;

    return Scaffold(
      appBar: _searching ? _searchBar() : _normalBar(filterActive),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _onAddMedia(context),
        child: const Icon(Icons.add_a_photo_outlined),
      ),
      body: _searching
          ? _buildSearchBody()
          : mediaAsync.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => Center(child: Text('오류: $e')),
              data: (items) => items.isEmpty
                  ? const _EmptyWorkView()
                  : MediaTimeline(
                      items: items,
                      onTap: (group, idx) => _openDetail(context, group, idx),
                      onLongPress: (item) => _openViewer(context, [item], 0),
                      onRefresh: () async => ref.invalidate(workMediaProvider),
                    ),
            ),
    );
  }

  AppBar _normalBar(bool filterActive) {
    return AppBar(
      title: Row(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                  colors: [Color(0xFF1A73E8), Color(0xFF00C896)]),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Text('Work',
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
          icon: const Icon(Icons.picture_as_pdf_outlined),
          tooltip: '보고서 생성',
          onPressed: () => _showReportMenu(),
        ),
        Stack(
          clipBehavior: Clip.none,
          children: [
            IconButton(
              icon: const Icon(Icons.tune_rounded),
              onPressed: () => _showFilterSheet(),
            ),
            if (filterActive)
              Positioned(
                right: 8,
                top: 8,
                child: Container(
                  width: 8,
                  height: 8,
                  decoration: const BoxDecoration(
                    color: Color(0xFF00C896),
                    shape: BoxShape.circle,
                  ),
                ),
              ),
          ],
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
          hintText: 'Work 검색...',
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
            style: TextStyle(color: Colors.grey, fontSize: 14)),
      );
    }
    final results = _searchResults ?? [];
    if (results.isEmpty) {
      return const Center(
        child: Text('검색 결과가 없습니다',
            style: TextStyle(color: Colors.grey, fontSize: 14)),
      );
    }
    return MediaTimeline(
      items: results,
      onTap: (group, idx) => _openDetail(context, group, idx),
      onLongPress: (item) => _openViewer(context, [item], 0),
    );
  }

  Future<void> _onAddMedia(BuildContext context) async {
    List<CapturedMedia>? capturedList =
        await CaptureBottomSheet.show(context, allowDocument: true);

    if (capturedList == null || capturedList.isEmpty || !context.mounted) return;

    try {
      final results = await MediaSaveService.saveAll(
        captured: capturedList,
        space: MediaSpace.work,
      );
      ref.invalidate(workMediaProvider);

      if (!context.mounted) return;

      if (results.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('저장된 항목이 없습니다.'),
          backgroundColor: Colors.orange,
        ));
        return;
      }

      final savedCount = results.length;
      final selectedCount = capturedList.length;
      if (savedCount < selectedCount) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(
              '$selectedCount개 중 $savedCount개 저장됨 (${selectedCount - savedCount}개 실패)'),
          backgroundColor: Colors.orange,
          duration: const Duration(seconds: 3),
        ));
      } else if (savedCount > 1) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('$savedCount개 저장됨. 첫 번째 항목을 편집합니다.'),
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
      // 편집 여부와 무관하게 복귀 시 무조건 새로고침
      if (context.mounted) ref.invalidate(workMediaProvider);
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('미디어 저장 실패: $e'),
        backgroundColor: Colors.red,
      ));
    }
  }

  void _openViewer(BuildContext context, List<MediaItem> items, int index) {
    Navigator.push(
      context,
      MaterialPageRoute(
          builder: (_) => MediaViewerScreen(items: items, initialIndex: index)),
    );
  }

  Future<void> _openDetail(BuildContext context, List<MediaItem> group, int index) async {
    final changed = await Navigator.push<dynamic>(
      context,
      MaterialPageRoute(
        builder: (_) => MediaDetailScreen(items: group, initialIndex: index),
      ),
    );
    if (changed != null) ref.invalidate(workMediaProvider);
  }

  void _showReportMenu() {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (ctx) => const _ReportMenuSheet(),
    );
  }

  void _showFilterSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => const WorkFilterSheet(),
    );
  }
}

// ── 빈 화면 ──────────────────────────────────────────────────

class _EmptyWorkView extends StatelessWidget {
  const _EmptyWorkView();

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
                    colors: [Color(0xFF1A73E8), Color(0xFF00C896)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight),
                borderRadius: BorderRadius.circular(28),
              ),
              child: const Icon(Icons.work_outline, size: 44, color: Colors.white),
            ),
            const SizedBox(height: 24),
            Text('업무 미디어가 없어요',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            Text('메모릭스에만 보관\n외부에 노출되지 않아요',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 13, color: Colors.grey[500], height: 1.6)),
            const SizedBox(height: 4),
            Text('+ 버튼을 눌러 추가하세요',
                style: TextStyle(fontSize: 12, color: Colors.grey[400])),
          ],
        ),
      ),
    );
  }
}

// ── 보고서 메뉴 ───────────────────────────────────────────────

class _ReportMenuSheet extends ConsumerWidget {
  const _ReportMenuSheet();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(height: 12),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 20),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text('보고서 생성',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            ),
          ),
          const SizedBox(height: 4),
          _reportTile(context, Icons.timeline, '출장보고서 (타임라인)', 0),
          _reportTile(context, Icons.photo_album_outlined, '현장분위기 (매거진)', 1),
          _reportTile(context, Icons.warning_amber_outlined, '장애현상 (번호표)', 2),
          _reportTile(context, Icons.grid_view, '사진대지 (그리드)', 3),
          const SizedBox(height: 8),
        ],
      ),
    );
  }

  Widget _reportTile(BuildContext ctx, IconData icon, String label, int idx) {
    return ListTile(
      leading: Icon(icon),
      title: Text(label),
      onTap: () {
        Navigator.pop(ctx);
        Navigator.push(ctx, MaterialPageRoute(
          builder: (_) => ReportScreen(reportType: ReportType.values[idx]),
        ));
      },
    );
  }
}
