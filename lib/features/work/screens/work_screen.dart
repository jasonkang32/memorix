import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/work_provider.dart';
import '../widgets/work_filter_sheet.dart';
import '../../../core/db/media_dao.dart';
import '../../../core/services/media_capture_service.dart';
import '../../../core/services/media_save_service.dart';
import '../../../core/services/original_media_cleanup_service.dart';
import '../../../core/services/report_service.dart';
import '../../../features/home/providers/home_provider.dart';
import '../../../shared/models/media_item.dart';
import '../../../shared/widgets/capture_bottom_sheet.dart';
import '../../../shared/widgets/media_timeline.dart';
import '../../../shared/screens/media_detail_screen.dart';
import '../../../shared/screens/media_viewer_screen.dart';
import 'report_screen.dart';
import '../../../shared/theme/app_theme.dart';

class WorkScreen extends ConsumerStatefulWidget {
  const WorkScreen({super.key});

  @override
  ConsumerState<WorkScreen> createState() => _WorkScreenState();
}

class _WorkScreenState extends ConsumerState<WorkScreen> {
  bool _searching = false;
  bool _isImporting = false;
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
    final filterActive =
        filter.countryCode != null ||
        filter.region != null ||
        filter.mediaType != null;

    return Scaffold(
      appBar: _searching ? _searchBar() : _normalBar(filterActive),
      floatingActionButton: FloatingActionButton(
        onPressed: _isImporting ? null : () => _onFabPressed(context),
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
                colors: [AppColors.workAccent, AppColors.brandPrimary],
              ),
              borderRadius: BorderRadius.circular(AppRadius.small),
            ),
            child: const Text(
              'Work',
              style: TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w800,
                fontSize: 14,
              ),
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
                    color: AppColors.brandPrimary,
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

  Future<void> _onFabPressed(BuildContext context) async {
    await _onAddMedia(context);
  }

  Future<void> _onAddMedia(BuildContext context) async {
    final capturedList = await CaptureBottomSheet.show(
      context,
      allowDocument: true,
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
      // 다이얼로그 route가 실제 mount되기 전에 닫히며
      // 검은 화면 barrier만 남는 race를 방지한다.
      await dialogReady.future.timeout(
        const Duration(milliseconds: 300),
        onTimeout: () {},
      );

      final results = await MediaSaveService.saveAll(
        captured: capturedList,
        space: MediaSpace.work,
        onProgress: (done, _) => progressNotifier.value = done,
        onEnhancementComplete: () {
          if (context.mounted) {
            ref.invalidate(workMediaProvider);
          }
        },
      );

      closeProgressDialog();

      if (mounted) {
        ref.invalidate(workMediaProvider);
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
        ref.invalidate(workMediaProvider);
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
    if (changed != null) ref.invalidate(workMediaProvider);
  }

  void _showReportMenu() {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
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
                gradient: AppTheme.workGradient,
                borderRadius: BorderRadius.circular(AppRadius.blob),
              ),
              child: const Icon(
                Icons.work_outline,
                size: 44,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 24),
            Text('업무 미디어가 없어요', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            Text(
              '메모릭스에만 보관\n외부에 노출되지 않아요',
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
              '+ 버튼을 눌러 추가하세요',
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
              child: Text(
                '보고서 생성',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
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
        Navigator.push(
          ctx,
          MaterialPageRoute(
            builder: (_) => ReportScreen(reportType: ReportType.values[idx]),
          ),
        );
      },
    );
  }
}
