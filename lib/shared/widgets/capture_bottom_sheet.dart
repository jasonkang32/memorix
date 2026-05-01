import 'package:flutter/material.dart';

import '../../core/services/media_capture_service.dart';
import '../models/media_item.dart';

enum _CaptureAction { camera, gallery, document }

class CaptureBottomSheet extends StatelessWidget {
  final bool allowDocument;
  final MediaSpace? space;

  const CaptureBottomSheet({super.key, this.allowDocument = false, this.space});

  static Future<List<CapturedMedia>?> show(
    BuildContext context, {
    bool allowDocument = false,
    MediaSpace? space,
    VoidCallback? onProcessingStarted,
  }) async {
    final action = await showModalBottomSheet<_CaptureAction>(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) =>
          CaptureBottomSheet(allowDocument: allowDocument, space: space),
    );

    if (action == null || !context.mounted) return null;

    onProcessingStarted?.call();

    final targetSpace = space ?? MediaSpace.work;
    try {
      final results = switch (action) {
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
      return results.isEmpty ? null : results;
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('오류: $e'), backgroundColor: Colors.red),
        );
      }
      return null;
    }
  }

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
            Container(
              margin: const EdgeInsets.fromLTRB(16, 4, 16, 8),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: const Color(0xFF00C896).withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Row(
                children: [
                  Icon(
                    Icons.shield_outlined,
                    size: 14,
                    color: Color(0xFF00C896),
                  ),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      '등록 후 원본 삭제를 선택하면 메모릭스 보관함에만 남깁니다.',
                      style: TextStyle(
                        fontSize: 11,
                        color: Color(0xFF00A87C),
                        height: 1.4,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            _tile(
              context,
              icon: Icons.camera_alt_outlined,
              color: const Color(0xFF00C896),
              label: '카메라로 촬영',
              onTap: () => Navigator.pop(context, _CaptureAction.camera),
            ),
            _tile(
              context,
              icon: Icons.photo_library_outlined,
              color: const Color(0xFF7B61FF),
              label: '갤러리에서 가져오기',
              subtitle: '사진·영상 여러 개 선택 후 "추가"',
              onTap: () => Navigator.pop(context, _CaptureAction.gallery),
            ),
            if (allowDocument)
              _tile(
                context,
                icon: Icons.description_outlined,
                color: const Color(0xFF1A73E8),
                label: '문서 가져오기',
                subtitle: 'PDF, Word, Excel',
                onTap: () => Navigator.pop(context, _CaptureAction.document),
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
