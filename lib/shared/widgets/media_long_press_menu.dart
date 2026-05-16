import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/auth/services/lock_toggle_helper.dart';
import '../models/media_item.dart';

/// 미디어 셀 long-press 시 표시되는 컨텍스트 메뉴.
///
/// 옵션:
///  - 잠금 / 잠금 해제 (인증 필요)
///  - 전체 화면 보기 (기존 long-press 흐름 보존)
class MediaLongPressMenu {
  const MediaLongPressMenu._();

  /// [onAfterToggle]: 잠금 토글 성공 후 호출 — 호출처에서 ref.invalidate 등.
  static Future<void> show({
    required BuildContext context,
    required WidgetRef ref,
    required MediaItem item,
    required VoidCallback onOpenViewer,
    VoidCallback? onAfterToggle,
  }) async {
    await showModalBottomSheet<void>(
      context: context,
      builder: (sheetCtx) => SafeArea(
        child: Wrap(
          children: [
            ListTile(
              leading: Icon(
                item.isLocked == 1 ? Icons.lock_open : Icons.lock,
                color: item.isLocked == 1 ? null : Colors.amber.shade700,
              ),
              title: Text(item.isLocked == 1 ? '잠금 해제' : '잠금'),
              subtitle: Text(
                item.isLocked == 1
                    ? '평문으로 복원합니다'
                    : '암호화하여 보호합니다',
                style: const TextStyle(fontSize: 11),
              ),
              onTap: () async {
                Navigator.pop(sheetCtx);
                if (!context.mounted) return;
                final ok = await handleLockToggle(context, ref, item);
                if (ok) onAfterToggle?.call();
              },
            ),
            ListTile(
              leading: const Icon(Icons.open_in_new),
              title: const Text('전체 화면 보기'),
              onTap: () {
                Navigator.pop(sheetCtx);
                onOpenViewer();
              },
            ),
          ],
        ),
      ),
    );
  }
}
