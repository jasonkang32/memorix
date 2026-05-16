import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:printing/printing.dart';
import '../../../core/db/media_dao.dart';
import '../../../core/services/report_service.dart';
import '../../../shared/models/media_item.dart';

/// 보고서 생성 화면 — 4단계 Wizard.
/// Step 1: 사진 검색 (키워드/필터로 후보 좁히기)
/// Step 2: 사진 추가 (검색 결과에서 multi-select)
/// Step 3: 보고서 작성 (제목·부제 등 메타 입력)
/// Step 4: 생성 (미리보기 후 PDF 생성)
class ReportScreen extends ConsumerStatefulWidget {
  final ReportType reportType;
  final String? countryCode;
  final String? region;

  const ReportScreen({
    super.key,
    required this.reportType,
    this.countryCode,
    this.region,
  });

  @override
  ConsumerState<ReportScreen> createState() => _ReportScreenState();
}

class _ReportScreenState extends ConsumerState<ReportScreen> {
  // 진행 단계 (0-based: 0..3 → 4 steps)
  int _currentStep = 0;

  // ── Step 1: 검색 ─────────────────────────────────────────
  final _searchCtrl = TextEditingController();
  String _searchQuery = '';
  List<MediaItem> _baseItems = []; // findWork()로 받은 전체 (잠금 제외)
  List<MediaItem> _searchResults = [];
  bool _loadingBase = true;
  bool _hasSearched = false;

  // ── Step 2: 선택 ─────────────────────────────────────────
  final Set<int> _selectedIds = <int>{};

  // ── Step 3: 폼 ─────────────────────────────────────────
  final _titleCtrl = TextEditingController();
  final _subtitleCtrl = TextEditingController();

  // ── Step 4: 생성 ─────────────────────────────────────────
  bool _generating = false;

  final _dao = MediaDao();

  @override
  void initState() {
    super.initState();
    _loadBase();
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    _titleCtrl.dispose();
    _subtitleCtrl.dispose();
    super.dispose();
  }

  // ── 데이터 로드 ─────────────────────────────────────────

  Future<void> _loadBase() async {
    // PDF 보고서는 외부 공유 가능성이 있으므로 잠긴 항목은 자동 제외 (spec 8b).
    final items = await _dao.findWork(
      countryCode: widget.countryCode,
      region: widget.region,
      includeLocked: false,
      limit: 500,
    );
    if (!mounted) return;
    setState(() {
      _baseItems = items;
      _searchResults = items; // 초기에는 전체가 후보
      _loadingBase = false;
    });
  }

  void _runSearch(String q) {
    final query = q.trim().toLowerCase();
    setState(() {
      _searchQuery = q;
      _hasSearched = true;
      if (query.isEmpty) {
        _searchResults = List.from(_baseItems);
        return;
      }
      _searchResults = _baseItems.where((m) {
        final hay = [
          m.title,
          m.note,
          m.countryCode,
          m.region,
          m.ocrText,
        ].join(' ').toLowerCase();
        return hay.contains(query);
      }).toList();
    });
  }

  // ── Step 진행 가드 ─────────────────────────────────────────

  bool get _canAdvanceFromSearch {
    // 검색을 했거나(빈 쿼리로 "전체 보기" 포함) 결과가 있으면 진행 가능
    return _searchResults.isNotEmpty || _hasSearched;
  }

  bool get _canAdvanceFromSelect => _selectedIds.isNotEmpty;

  bool get _canAdvanceFromForm => _titleCtrl.text.trim().isNotEmpty;

  void _goNext() {
    final ok = switch (_currentStep) {
      0 => _canAdvanceFromSearch,
      1 => _canAdvanceFromSelect,
      2 => _canAdvanceFromForm,
      _ => false,
    };
    if (!ok) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_blockedMessage(_currentStep))),
      );
      return;
    }
    if (_currentStep < 3) {
      setState(() => _currentStep += 1);
    }
  }

  void _goPrev() {
    if (_currentStep > 0) {
      setState(() => _currentStep -= 1);
    }
  }

  String _blockedMessage(int step) {
    return switch (step) {
      0 => '먼저 검색하거나 결과를 확인하세요',
      1 => '사진을 1장 이상 선택하세요',
      2 => '보고서 제목을 입력하세요',
      _ => '',
    };
  }

  // ── 생성 ─────────────────────────────────────────

  Future<void> _generate() async {
    final selected = _baseItems.where((m) => _selectedIds.contains(m.id)).toList();
    if (selected.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('미디어를 1개 이상 선택하세요')),
      );
      return;
    }

    setState(() => _generating = true);

    try {
      final title = _titleCtrl.text.trim().isEmpty ? '보고서' : _titleCtrl.text.trim();
      final pdfPath = await ReportService.generate(
        type: widget.reportType,
        items: selected,
        title: title,
        subtitle: _subtitleCtrl.text.trim(),
      );

      if (!mounted) return;

      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => _PdfPreviewScreen(pdfPath: pdfPath, title: title),
        ),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('PDF 생성 오류: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _generating = false);
    }
  }

  // ── UI ─────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(_reportTitle)),
      body: _loadingBase
          ? const Center(child: CircularProgressIndicator())
          : Stepper(
              type: StepperType.horizontal,
              currentStep: _currentStep,
              onStepContinue: _currentStep == 3 ? null : _goNext,
              onStepCancel: _currentStep == 0 ? null : _goPrev,
              onStepTapped: (i) {
                // 뒤로 점프는 자유, 앞으로 점프는 가드 통과해야
                if (i <= _currentStep) {
                  setState(() => _currentStep = i);
                  return;
                }
                // 앞으로 한 칸씩 이동하며 가드 검증
                while (_currentStep < i) {
                  final ok = switch (_currentStep) {
                    0 => _canAdvanceFromSearch,
                    1 => _canAdvanceFromSelect,
                    2 => _canAdvanceFromForm,
                    _ => false,
                  };
                  if (!ok) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text(_blockedMessage(_currentStep))),
                    );
                    return;
                  }
                  setState(() => _currentStep += 1);
                }
              },
              controlsBuilder: (ctx, details) {
                return Padding(
                  padding: const EdgeInsets.only(top: 12),
                  child: Row(
                    children: [
                      if (_currentStep < 3)
                        FilledButton(
                          onPressed: details.onStepContinue,
                          child: const Text('다음'),
                        )
                      else
                        FilledButton.icon(
                          icon: _generating
                              ? const SizedBox(
                                  width: 16,
                                  height: 16,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                              : const Icon(Icons.picture_as_pdf),
                          label: Text(_generating ? '생성 중...' : '생성'),
                          onPressed: _generating ? null : _generate,
                        ),
                      const SizedBox(width: 8),
                      if (_currentStep > 0)
                        TextButton(
                          onPressed: details.onStepCancel,
                          child: const Text('이전'),
                        ),
                    ],
                  ),
                );
              },
              steps: [
                Step(
                  title: const Text('사진 검색'),
                  isActive: _currentStep >= 0,
                  state: _currentStep > 0 ? StepState.complete : StepState.indexed,
                  content: _buildSearchStep(),
                ),
                Step(
                  title: const Text('사진 추가'),
                  isActive: _currentStep >= 1,
                  state: _currentStep > 1 ? StepState.complete : StepState.indexed,
                  content: _buildSelectStep(),
                ),
                Step(
                  title: const Text('보고서 작성'),
                  isActive: _currentStep >= 2,
                  state: _currentStep > 2 ? StepState.complete : StepState.indexed,
                  content: _buildFormStep(),
                ),
                Step(
                  title: const Text('생성'),
                  isActive: _currentStep >= 3,
                  state: StepState.indexed,
                  content: _buildGenerateStep(),
                ),
              ],
            ),
    );
  }

  // ── Step 1: 검색 ─────────────────────────────────────────
  Widget _buildSearchStep() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextField(
          controller: _searchCtrl,
          decoration: InputDecoration(
            hintText: '제목·메모·지역·태그 검색',
            prefixIcon: const Icon(Icons.search),
            suffixIcon: _searchQuery.isEmpty
                ? null
                : IconButton(
                    icon: const Icon(Icons.clear),
                    onPressed: () {
                      _searchCtrl.clear();
                      _runSearch('');
                    },
                  ),
            border: const OutlineInputBorder(),
          ),
          onChanged: _runSearch,
          onSubmitted: _runSearch,
        ),
        const SizedBox(height: 8),
        Align(
          alignment: Alignment.centerLeft,
          child: Text(
            _hasSearched
                ? '검색 결과: ${_searchResults.length}개'
                : '전체 후보: ${_baseItems.length}개 (검색하거나 다음으로 진행)',
            style: const TextStyle(fontSize: 12, color: Colors.grey),
          ),
        ),
        const SizedBox(height: 8),
        if (_searchResults.isEmpty && _hasSearched)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 24),
            child: Text(
              '검색 결과가 없습니다',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.grey),
            ),
          )
        else
          SizedBox(
            height: 220,
            child: GridView.builder(
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 4,
                mainAxisSpacing: 4,
                crossAxisSpacing: 4,
              ),
              itemCount: _searchResults.length,
              itemBuilder: (_, i) {
                final m = _searchResults[i];
                return ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: _Thumb(item: m),
                );
              },
            ),
          ),
      ],
    );
  }

  // ── Step 2: 선택 (multi-select) ─────────────────────────────────────────
  Widget _buildSelectStep() {
    final candidates = _searchResults;
    if (candidates.isEmpty) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 24),
        child: Text(
          '선택할 사진이 없습니다. 이전 단계로 돌아가 검색을 조정하세요.',
          textAlign: TextAlign.center,
        ),
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              '선택: ${_selectedIds.length} / ${candidates.length}',
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
            TextButton(
              onPressed: () => setState(() {
                final allIds = candidates
                    .map((m) => m.id)
                    .whereType<int>()
                    .toSet();
                final allSelected = allIds.every(_selectedIds.contains);
                if (allSelected) {
                  _selectedIds.removeAll(allIds);
                } else {
                  _selectedIds.addAll(allIds);
                }
              }),
              child: Text(
                candidates.every((m) => _selectedIds.contains(m.id))
                    ? '전체 해제'
                    : '전체 선택',
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        SizedBox(
          height: 320,
          child: GridView.builder(
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 3,
              mainAxisSpacing: 6,
              crossAxisSpacing: 6,
            ),
            itemCount: candidates.length,
            itemBuilder: (_, i) {
              final m = candidates[i];
              final selected = _selectedIds.contains(m.id);
              return GestureDetector(
                onTap: () => setState(() {
                  if (selected) {
                    _selectedIds.remove(m.id);
                  } else if (m.id != null) {
                    _selectedIds.add(m.id!);
                  }
                }),
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(6),
                      child: _Thumb(item: m),
                    ),
                    if (selected)
                      Container(
                        decoration: BoxDecoration(
                          color: Colors.blue.withValues(alpha: 0.35),
                          borderRadius: BorderRadius.circular(6),
                          border: Border.all(color: Colors.blue, width: 3),
                        ),
                      ),
                    Positioned(
                      top: 4,
                      right: 4,
                      child: Container(
                        width: 24,
                        height: 24,
                        decoration: BoxDecoration(
                          color: selected ? Colors.blue : Colors.black54,
                          shape: BoxShape.circle,
                          border: Border.all(color: Colors.white, width: 2),
                        ),
                        child: selected
                            ? const Icon(
                                Icons.check,
                                size: 16,
                                color: Colors.white,
                              )
                            : null,
                      ),
                    ),
                  ],
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  // ── Step 3: 폼 ─────────────────────────────────────────
  Widget _buildFormStep() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextField(
          controller: _titleCtrl,
          decoration: const InputDecoration(
            labelText: '보고서 제목 *',
            border: OutlineInputBorder(),
            prefixIcon: Icon(Icons.title),
          ),
          onChanged: (_) => setState(() {}),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _subtitleCtrl,
          decoration: const InputDecoration(
            labelText: '부제목 (선택)',
            border: OutlineInputBorder(),
            prefixIcon: Icon(Icons.subtitles_outlined),
          ),
        ),
        const SizedBox(height: 12),
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: Colors.grey.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Row(
            children: [
              const Icon(Icons.info_outline, size: 18),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  '보고서 종류: $_reportTitle\n포함 사진: ${_selectedIds.length}장',
                  style: const TextStyle(fontSize: 12),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  // ── Step 4: 생성 미리보기 ─────────────────────────────────────────
  Widget _buildGenerateStep() {
    final selected = _baseItems.where((m) => _selectedIds.contains(m.id)).toList();
    final title = _titleCtrl.text.trim().isEmpty ? '(제목 없음)' : _titleCtrl.text.trim();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                if (_subtitleCtrl.text.trim().isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(
                    _subtitleCtrl.text.trim(),
                    style: const TextStyle(fontSize: 13, color: Colors.grey),
                  ),
                ],
                const SizedBox(height: 8),
                Row(
                  children: [
                    const Icon(Icons.photo_library_outlined, size: 16),
                    const SizedBox(width: 4),
                    Text('${selected.length}장 포함'),
                    const SizedBox(width: 16),
                    const Icon(Icons.description_outlined, size: 16),
                    const SizedBox(width: 4),
                    Text(_reportTitle),
                  ],
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 8),
        SizedBox(
          height: 120,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: selected.length,
            separatorBuilder: (_, _) => const SizedBox(width: 6),
            itemBuilder: (_, i) {
              return SizedBox(
                width: 100,
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: _Thumb(item: selected[i]),
                ),
              );
            },
          ),
        ),
        const SizedBox(height: 12),
        const Text(
          '아래 [생성] 버튼을 눌러 PDF를 만듭니다.',
          style: TextStyle(fontSize: 12, color: Colors.grey),
        ),
      ],
    );
  }

  String get _reportTitle => switch (widget.reportType) {
    ReportType.tripReport => '출장보고서',
    ReportType.siteReport => '현장분위기',
    ReportType.faultReport => '장애현상',
    ReportType.photoSheet => '사진대지',
  };
}

// ── 썸네일 위젯 ─────────────────────────────────────────

class _Thumb extends StatelessWidget {
  final MediaItem item;
  const _Thumb({required this.item});

  @override
  Widget build(BuildContext context) {
    if (item.mediaType == MediaType.document) {
      return Container(
        color: Colors.blue[50],
        child: const Icon(Icons.description, color: Colors.blueGrey),
      );
    }
    final thumbPath = item.thumbPath ?? item.filePath;
    final f = File(thumbPath);
    if (f.existsSync()) {
      return Image.file(f, fit: BoxFit.cover);
    }
    return Container(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: Icon(
        item.mediaType == MediaType.video
            ? Icons.videocam_outlined
            : Icons.image_outlined,
        color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.6),
      ),
    );
  }
}

// ── PDF 미리보기 ─────────────────────────────────────────

class _PdfPreviewScreen extends StatelessWidget {
  final String pdfPath;
  final String title;

  const _PdfPreviewScreen({required this.pdfPath, required this.title});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(title),
        actions: [
          IconButton(
            icon: const Icon(Icons.share),
            onPressed: () => ReportService.share(pdfPath),
          ),
        ],
      ),
      body: PdfPreview(
        build: (format) => File(pdfPath).readAsBytes(),
        canChangePageFormat: false,
        canChangeOrientation: false,
        allowPrinting: true,
        allowSharing: false,
        pdfFileName: '$title.pdf',
      ),
    );
  }
}
