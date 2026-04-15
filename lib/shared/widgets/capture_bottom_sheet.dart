import 'package:flutter/material.dart';
import '../../core/services/media_capture_service.dart';
import '../models/media_item.dart';

class CaptureBottomSheet extends StatelessWidget {
  final bool allowDocument;
  final MediaSpace? space;

  const CaptureBottomSheet({
    super.key,
    this.allowDocument = false,
    this.space,
  });

  static Future<List<CapturedMedia>?> show(
    BuildContext context, {
    bool allowDocument = false,
    MediaSpace? space,
  }) {
    return showModalBottomSheet<List<CapturedMedia>>(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => CaptureBottomSheet(
        allowDocument: allowDocument,
        space: space,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // 핸들
            Container(
              width: 36,
              height: 4,
              margin: const EdgeInsets.only(bottom: 12),
              decoration: BoxDecoration(
                color: Colors.grey[300],
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
            // 원본 보관 안내 문구
            Container(
              margin: const EdgeInsets.fromLTRB(16, 4, 16, 8),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: const Color(0xFF00C896).withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(10),
              ),
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
              onTap: () async {
                try {
                  final results = await MediaCaptureService.capturePhoto();
                  if (context.mounted) {
                    Navigator.pop(context, results.isEmpty ? null : results);
                  }
                } catch (e) {
                  if (context.mounted) {
                    Navigator.pop(context, null);
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                      content: Text('카메라 오류: $e'),
                      backgroundColor: Colors.red,
                    ));
                  }
                }
              },
            ),
            _tile(
              context,
              icon: Icons.photo_library_outlined,
              color: const Color(0xFF7B61FF),
              label: '갤러리에서 가져오기',
              subtitle: '사진·영상 여러 개 선택 후 "추가"',
              onTap: () async {
                try {
                  final results = await MediaCaptureService.pickGallery(context);
                  if (context.mounted) {
                    Navigator.pop(context, results.isEmpty ? null : results);
                  }
                } catch (e) {
                  if (context.mounted) {
                    Navigator.pop(context, null);
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                      content: Text('갤러리 오류: $e'),
                      backgroundColor: Colors.red,
                    ));
                  }
                }
              },
            ),
            if (allowDocument)
              _tile(
                context,
                icon: Icons.description_outlined,
                color: const Color(0xFF1A73E8),
                label: '문서 가져오기',
                subtitle: 'PDF, Word, Excel',
                onTap: () async {
                  try {
                    final results =
                        await MediaCaptureService.pickDocument(context);
                    if (context.mounted) {
                      Navigator.pop(context, results.isEmpty ? null : results);
                    }
                  } catch (e) {
                    if (context.mounted) {
                      Navigator.pop(context, null);
                      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                        content: Text('문서 오류: $e'),
                        backgroundColor: Colors.red,
                      ));
                    }
                  }
                },
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
      title: Text(label,
          style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
      subtitle: subtitle != null
          ? Text(subtitle,
              style: const TextStyle(fontSize: 12, color: Colors.grey))
          : null,
      onTap: onTap,
    );
  }
}
