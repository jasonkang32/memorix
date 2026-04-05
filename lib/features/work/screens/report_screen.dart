import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:printing/printing.dart';
import '../../../core/db/media_dao.dart';
import '../../../core/services/report_service.dart';
import '../../../shared/models/media_item.dart';

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
  final _titleCtrl = TextEditingController();
  final _subtitleCtrl = TextEditingController();
  List<MediaItem> _selectedItems = [];
  List<MediaItem> _allItems = [];
  bool _loading = true;
  bool _generating = false;

  final _dao = MediaDao();

  @override
  void initState() {
    super.initState();
    _loadItems();
  }

  @override
  void dispose() {
    _titleCtrl.dispose();
    _subtitleCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadItems() async {
    final items = await _dao.findWork(
      countryCode: widget.countryCode,
      region: widget.region,
    );
    setState(() {
      _allItems = items;
      _selectedItems = List.from(items);
      _loading = false;
    });
  }

  Future<void> _generate() async {
    if (_selectedItems.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('미디어를 1개 이상 선택하세요')),
      );
      return;
    }

    setState(() => _generating = true);

    try {
      final pdfPath = await ReportService.generate(
        type: widget.reportType,
        items: _selectedItems,
        title: _titleCtrl.text.trim().isEmpty ? '보고서' : _titleCtrl.text.trim(),
        subtitle: _subtitleCtrl.text.trim(),
      );

      if (!mounted) return;

      // printing 패키지로 미리보기
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (ctx) => _PdfPreviewScreen(
            pdfPath: pdfPath,
            title: _titleCtrl.text.trim().isEmpty ? '보고서' : _titleCtrl.text.trim(),
          ),
        ),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('PDF 생성 오류: $e')));
      }
    } finally {
      if (mounted) setState(() => _generating = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_reportTitle),
        actions: [
          if (_generating)
            const Padding(
              padding: EdgeInsets.all(16),
              child: SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            )
          else
            TextButton.icon(
              icon: const Icon(Icons.picture_as_pdf),
              label: const Text('생성'),
              onPressed: _generate,
            ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                // 보고서 제목
                TextField(
                  controller: _titleCtrl,
                  decoration: const InputDecoration(
                    labelText: '보고서 제목',
                    border: OutlineInputBorder(),
                    prefixIcon: Icon(Icons.title),
                  ),
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
                const SizedBox(height: 20),
                // 미디어 선택
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      '포함할 미디어 (${_selectedItems.length}/${_allItems.length})',
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                    TextButton(
                      onPressed: () => setState(() {
                        _selectedItems = _selectedItems.length == _allItems.length
                            ? []
                            : List.from(_allItems);
                      }),
                      child: Text(
                        _selectedItems.length == _allItems.length ? '전체 해제' : '전체 선택',
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                ..._allItems.map((item) => _MediaSelectTile(
                      item: item,
                      selected: _selectedItems.contains(item),
                      onToggle: (selected) => setState(() {
                        if (selected) {
                          _selectedItems.add(item);
                        } else {
                          _selectedItems.remove(item);
                        }
                      }),
                    )),
              ],
            ),
    );
  }

  String get _reportTitle => switch (widget.reportType) {
        ReportType.tripReport => '출장보고서',
        ReportType.siteReport => '현장분위기',
        ReportType.faultReport => '장애현상',
        ReportType.photoSheet => '사진대지',
      };
}

class _MediaSelectTile extends StatelessWidget {
  final MediaItem item;
  final bool selected;
  final ValueChanged<bool> onToggle;

  const _MediaSelectTile({
    required this.item,
    required this.selected,
    required this.onToggle,
  });

  @override
  Widget build(BuildContext context) {
    return CheckboxListTile(
      value: selected,
      onChanged: (v) => onToggle(v ?? false),
      secondary: ClipRRect(
        borderRadius: BorderRadius.circular(6),
        child: SizedBox(
          width: 48,
          height: 48,
          child: _buildThumb(),
        ),
      ),
      title: Text(
        item.title.isEmpty ? '(제목 없음)' : item.title,
        style: const TextStyle(fontSize: 14),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      subtitle: Text(
        [
          if (item.countryCode.isNotEmpty) item.countryCode,
          if (item.region.isNotEmpty) item.region,
          item.mediaType.name,
        ].join(' · '),
        style: const TextStyle(fontSize: 11),
      ),
    );
  }

  Widget _buildThumb() {
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
      color: Colors.grey[200],
      child: Icon(
        item.mediaType == MediaType.video
            ? Icons.videocam_outlined
            : Icons.image_outlined,
        color: Colors.grey,
      ),
    );
  }
}

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
