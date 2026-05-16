import 'package:flutter/material.dart';

import '../../core/services/media_capture_service.dart';
import '../models/media_item.dart';

enum _CaptureAction { camera, gallery, document }

/// Capture sheet에서 사용자가 고른 액션 + 옵션을 함께 전달하기 위한 내부 record.
class _SheetSelection {
  final _CaptureAction action;
  final bool deleteOriginal;
  const _SheetSelection(this.action, this.deleteOriginal);
}

/// CaptureBottomSheet의 호출 결과.
/// - [items]: 실제 import된 미디어 목록 (비어있으면 사용자 취소 또는 실패)
/// - [deleteOriginal]: 시트의 "원본 삭제" toggle 상태 (default OFF)
class CaptureSheetResult {
  final List<CapturedMedia> items;
  final bool deleteOriginal;
  const CaptureSheetResult({
    required this.items,
    required this.deleteOriginal,
  });
}

class CaptureBottomSheet extends StatefulWidget {
  final bool allowDocument;
  final MediaSpace? space;

  const CaptureBottomSheet({super.key, this.allowDocument = false, this.space});

  static Future<CaptureSheetResult?> show(
    BuildContext context, {
    bool allowDocument = false,
    MediaSpace? space,
    VoidCallback? onProcessingStarted,
  }) async {
    final selection = await showModalBottomSheet<_SheetSelection>(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) =>
          CaptureBottomSheet(allowDocument: allowDocument, space: space),
    );

    if (selection == null || !context.mounted) return null;

    onProcessingStarted?.call();

    final targetSpace = space ?? MediaSpace.work;
    try {
      final results = switch (selection.action) {
        _CaptureAction.camera => await MediaCaptureService.capturePhoto(
          space: targetSpace,
        ),
        _CaptureAction.gallery => await MediaCaptureService.pickGallery(
          space: targetSpace,
        ),
        _CaptureAction.document => await MediaCaptureService.pickDocument(
          space: targetSpace,
        ),
      };
      if (results.isEmpty) return null;
      return CaptureSheetResult(
        items: results,
        deleteOriginal: selection.deleteOriginal,
      );
    } on Exception catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('오류: $e'), backgroundColor: Colors.red),
        );
      }
      return null;
    }
  }

  @override
  State<CaptureBottomSheet> createState() => _CaptureBottomSheetState();
}

class _CaptureBottomSheetState extends State<CaptureBottomSheet> {
  /// "원본 삭제" toggle 상태. default OFF — 사용자가 명시 ON 했을 때만 실행.
  bool _deleteOriginal = false;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 36,
              height: 4,
              margin: const EdgeInsets.only(bottom: 12),
              decoration: BoxDecoration(
                color: Theme.of(context).dividerColor,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const Padding(
              padding: EdgeInsets.fromLTRB(20, 0, 20, 4),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text(
                  '미디어 추가',
                  style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
                ),
              ),
            ),
            // ── 원본 삭제 toggle (default OFF) ──
            _DeleteOriginalToggle(
              value: _deleteOriginal,
              onChanged: (v) => setState(() => _deleteOriginal = v),
            ),
            _tile(
              context,
              icon: Icons.camera_alt_outlined,
              color: const Color(0xFF00C896),
              label: '카메라로 촬영',
              onTap: () => Navigator.pop(
                context,
                _SheetSelection(_CaptureAction.camera, _deleteOriginal),
              ),
            ),
            _tile(
              context,
              icon: Icons.photo_library_outlined,
              color: const Color(0xFF7B61FF),
              label: '갤러리에서 가져오기',
              subtitle: '사진·영상 여러 개 선택 후 "추가"',
              onTap: () => Navigator.pop(
                context,
                _SheetSelection(_CaptureAction.gallery, _deleteOriginal),
              ),
            ),
            if (widget.allowDocument)
              _tile(
                context,
                icon: Icons.description_outlined,
                color: const Color(0xFF1A73E8),
                label: '문서 가져오기',
                subtitle: 'PDF, Word, Excel',
                onTap: () => Navigator.pop(
                  context,
                  _SheetSelection(_CaptureAction.document, _deleteOriginal),
                ),
              ),
            const SizedBox(height: 4),
          ],
        ),
      ),
    );
  }

  Widget _tile(
    BuildContext context, {
    required IconData icon,
    required Color color,
    required String label,
    String? subtitle,
    required VoidCallback onTap,
  }) {
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 2),
      leading: Container(
        width: 44,
        height: 44,
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Icon(icon, color: color, size: 22),
      ),
      title: Text(
        label,
        style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
      ),
      subtitle: subtitle != null
          ? Text(
              subtitle,
              style: TextStyle(
                fontSize: 12,
                color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.6),
              ),
            )
          : null,
      onTap: onTap,
    );
  }
}

/// 시트 상단의 "원본 삭제" toggle row.
class _DeleteOriginalToggle extends StatelessWidget {
  final bool value;
  final ValueChanged<bool> onChanged;
  const _DeleteOriginalToggle({required this.value, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 4, 16, 8),
      padding: const EdgeInsets.fromLTRB(12, 4, 8, 4),
      decoration: BoxDecoration(
        color: const Color(0xFF00C896).withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        children: [
          const Icon(
            Icons.delete_outline,
            size: 18,
            color: Color(0xFF00A87C),
          ),
          const SizedBox(width: 10),
          const Expanded(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '가져온 후 원본 삭제',
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF005C42),
                  ),
                ),
                SizedBox(height: 2),
                Text(
                  '메모릭스 보관함에만 남기고 갤러리 원본은 자동으로 삭제',
                  style: TextStyle(
                    fontSize: 11,
                    color: Color(0xFF00A87C),
                    height: 1.3,
                  ),
                ),
              ],
            ),
          ),
          Switch(
            value: value,
            onChanged: onChanged,
            activeThumbColor: const Color(0xFF00C896),
          ),
        ],
      ),
    );
  }
}
