import 'dart:io';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:share_plus/share_plus.dart';
import '../../shared/models/media_item.dart';
import 'storage_service.dart';

enum ReportType {
  tripReport, // 출장보고서 (타임라인)
  siteReport, // 현장분위기 (매거진)
  faultReport, // 장애현상 (번호표)
  photoSheet, // 사진대지 (그리드)
}

class ReportService {
  /// PDF 생성 → 저장 → 파일 경로 반환
  static Future<String> generate({
    required ReportType type,
    required List<MediaItem> items,
    required String title,
    String subtitle = '',
  }) async {
    final pdf = pw.Document();

    switch (type) {
      case ReportType.tripReport:
        _buildTripReport(pdf, items, title, subtitle);
      case ReportType.siteReport:
        _buildSiteReport(pdf, items, title, subtitle);
      case ReportType.faultReport:
        _buildFaultReport(pdf, items, title, subtitle);
      case ReportType.photoSheet:
        _buildPhotoSheet(pdf, items, title, subtitle);
    }

    final bytes = await pdf.save();
    return StorageService.saveReport(bytes);
  }

  /// 생성된 PDF를 공유
  static Future<void> share(String pdfPath) async {
    await Share.shareXFiles([
      XFile(pdfPath, mimeType: 'application/pdf'),
    ], subject: 'Memorix 보고서');
  }

  // ── 출장보고서 (타임라인) ──
  static void _buildTripReport(
    pw.Document pdf,
    List<MediaItem> items,
    String title,
    String subtitle,
  ) {
    pdf.addPage(
      pw.MultiPage(
        pageFormat: PdfPageFormat.a4,
        header: (ctx) => _pageHeader(title, subtitle, '출장보고서'),
        footer: (ctx) => _pageFooter(ctx),
        build: (ctx) => [
          ...items.map(
            (item) => pw.Padding(
              padding: const pw.EdgeInsets.only(bottom: 16),
              child: pw.Row(
                crossAxisAlignment: pw.CrossAxisAlignment.start,
                children: [
                  // 타임스탬프
                  pw.SizedBox(
                    width: 80,
                    child: pw.Text(
                      _formatDate(item.takenAt),
                      style: pw.TextStyle(
                        fontSize: 9,
                        color: PdfColors.grey600,
                      ),
                    ),
                  ),
                  pw.SizedBox(width: 12),
                  // 썸네일
                  if (item.thumbPath != null &&
                      File(item.thumbPath!).existsSync())
                    pw.Image(
                      pw.MemoryImage(File(item.thumbPath!).readAsBytesSync()),
                      width: 100,
                      height: 75,
                      fit: pw.BoxFit.cover,
                    ),
                  pw.SizedBox(width: 12),
                  // 내용
                  pw.Expanded(
                    child: pw.Column(
                      crossAxisAlignment: pw.CrossAxisAlignment.start,
                      children: [
                        if (item.title.isNotEmpty)
                          pw.Text(
                            item.title,
                            style: pw.TextStyle(
                              fontWeight: pw.FontWeight.bold,
                              fontSize: 11,
                            ),
                          ),
                        if (item.note.isNotEmpty)
                          pw.Text(
                            item.note,
                            style: const pw.TextStyle(fontSize: 10),
                          ),
                        if (item.countryCode.isNotEmpty ||
                            item.region.isNotEmpty)
                          pw.Text(
                            '📍 ${item.countryCode} ${item.region}'.trim(),
                            style: const pw.TextStyle(
                              fontSize: 9,
                              color: PdfColors.grey700,
                            ),
                          ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ── 현장분위기 (매거진 레이아웃) ──
  static void _buildSiteReport(
    pw.Document pdf,
    List<MediaItem> items,
    String title,
    String subtitle,
  ) {
    final photoItems = items
        .where(
          (i) =>
              i.mediaType == MediaType.photo &&
              i.thumbPath != null &&
              File(i.thumbPath!).existsSync(),
        )
        .toList();

    pdf.addPage(
      pw.MultiPage(
        pageFormat: PdfPageFormat.a4,
        header: (ctx) => _pageHeader(title, subtitle, '현장분위기'),
        footer: (ctx) => _pageFooter(ctx),
        build: (ctx) {
          final widgets = <pw.Widget>[];
          for (var i = 0; i < photoItems.length; i += 2) {
            final row = photoItems.sublist(
              i,
              i + 2 > photoItems.length ? photoItems.length : i + 2,
            );
            widgets.add(
              pw.Padding(
                padding: const pw.EdgeInsets.only(bottom: 12),
                child: pw.Row(
                  children: row
                      .map(
                        (item) => pw.Expanded(
                          child: pw.Padding(
                            padding: const pw.EdgeInsets.symmetric(
                              horizontal: 4,
                            ),
                            child: pw.Column(
                              crossAxisAlignment: pw.CrossAxisAlignment.start,
                              children: [
                                pw.Image(
                                  pw.MemoryImage(
                                    File(item.thumbPath!).readAsBytesSync(),
                                  ),
                                  height: 140,
                                  fit: pw.BoxFit.cover,
                                ),
                                pw.SizedBox(height: 4),
                                if (item.title.isNotEmpty)
                                  pw.Text(
                                    item.title,
                                    style: pw.TextStyle(
                                      fontWeight: pw.FontWeight.bold,
                                      fontSize: 9,
                                    ),
                                  ),
                                if (item.note.isNotEmpty)
                                  pw.Text(
                                    item.note,
                                    style: const pw.TextStyle(
                                      fontSize: 8,
                                      color: PdfColors.grey700,
                                    ),
                                  ),
                              ],
                            ),
                          ),
                        ),
                      )
                      .toList(),
                ),
              ),
            );
          }
          return widgets;
        },
      ),
    );
  }

  // ── 장애현상 (번호 레이블) ──
  static void _buildFaultReport(
    pw.Document pdf,
    List<MediaItem> items,
    String title,
    String subtitle,
  ) {
    pdf.addPage(
      pw.MultiPage(
        pageFormat: PdfPageFormat.a4,
        header: (ctx) => _pageHeader(title, subtitle, '장애현상 보고서'),
        footer: (ctx) => _pageFooter(ctx),
        build: (ctx) => items.asMap().entries.map((entry) {
          final idx = entry.key + 1;
          final item = entry.value;
          return pw.Padding(
            padding: const pw.EdgeInsets.only(bottom: 20),
            child: pw.Column(
              crossAxisAlignment: pw.CrossAxisAlignment.start,
              children: [
                // 번호 + 제목
                pw.Container(
                  padding: const pw.EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 6,
                  ),
                  decoration: const pw.BoxDecoration(color: PdfColors.grey200),
                  child: pw.Row(
                    children: [
                      pw.Container(
                        width: 24,
                        height: 24,
                        decoration: const pw.BoxDecoration(
                          color: PdfColors.red,
                          shape: pw.BoxShape.circle,
                        ),
                        child: pw.Center(
                          child: pw.Text(
                            '$idx',
                            style: pw.TextStyle(
                              color: PdfColors.white,
                              fontWeight: pw.FontWeight.bold,
                              fontSize: 11,
                            ),
                          ),
                        ),
                      ),
                      pw.SizedBox(width: 10),
                      pw.Text(
                        item.title.isEmpty ? '현상 $idx' : item.title,
                        style: pw.TextStyle(
                          fontWeight: pw.FontWeight.bold,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
                pw.SizedBox(height: 8),
                if (item.thumbPath != null &&
                    File(item.thumbPath!).existsSync())
                  pw.Image(
                    pw.MemoryImage(File(item.thumbPath!).readAsBytesSync()),
                    height: 200,
                    fit: pw.BoxFit.contain,
                  ),
                pw.SizedBox(height: 6),
                if (item.note.isNotEmpty)
                  pw.Text(item.note, style: const pw.TextStyle(fontSize: 10)),
              ],
            ),
          );
        }).toList(),
      ),
    );
  }

  // ── 사진대지 (그리드 3×N) ──
  static void _buildPhotoSheet(
    pw.Document pdf,
    List<MediaItem> items,
    String title,
    String subtitle,
  ) {
    final photoItems = items
        .where((i) => i.thumbPath != null && File(i.thumbPath!).existsSync())
        .toList();

    const cols = 3;
    final rows = <List<MediaItem>>[];
    for (var i = 0; i < photoItems.length; i += cols) {
      rows.add(
        photoItems.sublist(
          i,
          i + cols > photoItems.length ? photoItems.length : i + cols,
        ),
      );
    }

    pdf.addPage(
      pw.MultiPage(
        pageFormat: PdfPageFormat.a4,
        header: (ctx) => _pageHeader(title, subtitle, '사진대지'),
        footer: (ctx) => _pageFooter(ctx),
        build: (ctx) => rows.map((row) {
          return pw.Padding(
            padding: const pw.EdgeInsets.only(bottom: 8),
            child: pw.Row(
              children: row.map((item) {
                final imageBytes = File(item.thumbPath!).readAsBytesSync();
                return pw.Expanded(
                  child: pw.Padding(
                    padding: const pw.EdgeInsets.all(3),
                    child: pw.Column(
                      children: [
                        pw.Image(
                          pw.MemoryImage(imageBytes),
                          height: 90,
                          fit: pw.BoxFit.cover,
                        ),
                        pw.SizedBox(height: 2),
                        pw.Text(
                          item.title.isEmpty ? '' : item.title,
                          style: const pw.TextStyle(fontSize: 7),
                          maxLines: 1,
                        ),
                      ],
                    ),
                  ),
                );
              }).toList(),
            ),
          );
        }).toList(),
      ),
    );
  }

  static pw.Widget _pageHeader(
    String title,
    String subtitle,
    String reportType,
  ) {
    return pw.Column(
      crossAxisAlignment: pw.CrossAxisAlignment.start,
      children: [
        pw.Row(
          mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
          children: [
            pw.Text(
              title,
              style: pw.TextStyle(fontWeight: pw.FontWeight.bold, fontSize: 16),
            ),
            pw.Container(
              padding: const pw.EdgeInsets.symmetric(
                horizontal: 8,
                vertical: 3,
              ),
              decoration: pw.BoxDecoration(
                color: PdfColors.grey800,
                borderRadius: const pw.BorderRadius.all(pw.Radius.circular(4)),
              ),
              child: pw.Text(
                reportType,
                style: const pw.TextStyle(color: PdfColors.white, fontSize: 9),
              ),
            ),
          ],
        ),
        if (subtitle.isNotEmpty)
          pw.Text(
            subtitle,
            style: const pw.TextStyle(fontSize: 10, color: PdfColors.grey700),
          ),
        pw.Divider(),
        pw.SizedBox(height: 4),
      ],
    );
  }

  static pw.Widget _pageFooter(pw.Context ctx) {
    return pw.Row(
      mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
      children: [
        pw.Text(
          'Generated by Memorix',
          style: const pw.TextStyle(fontSize: 8, color: PdfColors.grey500),
        ),
        pw.Text(
          '${ctx.pageNumber} / ${ctx.pagesCount}',
          style: const pw.TextStyle(fontSize: 8, color: PdfColors.grey500),
        ),
      ],
    );
  }

  static String _formatDate(int timestampMs) {
    final dt = DateTime.fromMillisecondsSinceEpoch(timestampMs);
    return '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')}\n${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }
}
