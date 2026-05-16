import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/models/media_item.dart';
import '../providers/lock_session_provider.dart';

/// per-item 잠금 토글 공통 흐름.
///
/// 3개 진입점(Detail 액션, Long-press 컨텍스트 메뉴, 미디어 추가 시점)에서
/// 같은 인증 → 변환 → DB 갱신 → snackbar 흐름을 공유한다.
///
/// 호출 후 UI 갱신은 호출처에서 `ref.invalidate(workMediaProvider)` 또는
/// `ref.invalidate(secretMediaProvider)` 등으로 책임진다.
///
/// 반환:
///  - true: 인증 성공 + 토글 성공
///  - false: 인증 실패 또는 토글 실패 (snackbar로 사용자에게 알림)
Future<bool> handleLockToggle(
  BuildContext context,
  WidgetRef ref,
  MediaItem item,
) async {
  final auth = ref.read(lockAuthServiceProvider);
  final ok = await auth.authenticate(context);
  if (!ok || !context.mounted) return false;

  final toggleService = ref.read(lockToggleServiceProvider);
  final wasLocked = item.isLocked == 1;

  // progress dialog
  showDialog<void>(
    context: context,
    barrierDismissible: false,
    builder: (_) => const Center(child: CircularProgressIndicator()),
  );

  try {
    if (wasLocked) {
      await toggleService.unlock(item);
    } else {
      await toggleService.lock(item);
    }
    if (context.mounted) {
      Navigator.of(context, rootNavigator: true).pop();
    }
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(wasLocked ? '잠금 해제됨' : '잠금됨'),
          duration: const Duration(seconds: 2),
        ),
      );
    }
    return true;
  } on Exception catch (e) {
    if (context.mounted) {
      Navigator.of(context, rootNavigator: true).pop();
    }
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('잠금 토글 실패: $e'),
          backgroundColor: Colors.red,
        ),
      );
    }
    return false;
  }
}

/// 미디어 추가 시점 잠금 옵션 헬퍼.
///
/// `MediaSaveService.saveAll(...)` 직후 호출 — 사용자에게 "이 항목들을
/// 잠그시겠어요?" 다이얼로그(default No)를 표시하고, 동의 시 인증 후
/// 일괄 lock. 인증/저장 실패는 silent (이미 저장은 완료된 상태이므로).
Future<void> offerLockAfterAdd(
  BuildContext context,
  WidgetRef ref,
  List<MediaItem> savedItems,
) async {
  if (savedItems.isEmpty || !context.mounted) return;

  final shouldLock = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: const Text('이 항목 잠금?'),
      content: Text('${savedItems.length}개 항목을 즉시 잠그시겠어요?'),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx, false),
          child: const Text('아니오'),
        ),
        FilledButton.icon(
          onPressed: () => Navigator.pop(ctx, true),
          icon: const Icon(Icons.lock),
          label: const Text('잠금'),
        ),
      ],
    ),
  );

  if (shouldLock != true || !context.mounted) return;

  final auth = ref.read(lockAuthServiceProvider);
  final ok = await auth.authenticate(context);
  if (!ok || !context.mounted) return;

  final toggleService = ref.read(lockToggleServiceProvider);

  showDialog<void>(
    context: context,
    barrierDismissible: false,
    builder: (_) => const Center(child: CircularProgressIndicator()),
  );

  var failed = 0;
  for (final item in savedItems) {
    try {
      await toggleService.lock(item);
    } on Exception {
      failed++;
    }
  }

  if (context.mounted) {
    Navigator.of(context, rootNavigator: true).pop();
  }
  if (context.mounted) {
    final ok = savedItems.length - failed;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          failed == 0
              ? '$ok개 항목을 잠갔습니다'
              : '$ok개 잠금 / $failed개 실패',
        ),
        backgroundColor: failed == 0 ? null : Colors.orange,
        duration: const Duration(seconds: 2),
      ),
    );
  }
}
