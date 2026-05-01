import 'dart:io';

import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import 'media_capture_service.dart';

class OriginalMediaDeleteResult {
  const OriginalMediaDeleteResult({
    required this.deleted,
    required this.failed,
  });

  final int deleted;
  final int failed;

  int get total => deleted + failed;
}

class OriginalMediaCleanupService {
  static const MethodChannel _channel = MethodChannel(
    'memorix/original_media_cleanup',
  );

  static Future<OriginalMediaDeleteResult> deleteOriginals(
    List<CapturedMedia> captured,
  ) async {
    var deleted = 0;
    var failed = 0;

    for (final item in captured) {
      final ok = await _deleteOne(item.filePath);
      if (ok) {
        deleted += 1;
      } else {
        failed += 1;
      }
    }

    return OriginalMediaDeleteResult(deleted: deleted, failed: failed);
  }

  static Future<bool> _deleteOne(String path) async {
    if (path.isEmpty) return false;

    // 🚨 DATA LOSS GUARD (Bug #5)
    // CapturedMedia.filePath는 StorageService가 반환한 보관함 final 경로다.
    // native MediaStore 채널이 그 path를 못 찾으면 fallback file.delete()로
    // 보관함 파일이 진짜로 사라진다. 보관함 안 path는 절대 처리하지 않는다.
    if (await _isInVault(path)) {
      return false;
    }

    try {
      final nativeDeleted = await _channel.invokeMethod<bool>(
        'deleteOriginal',
        {'path': path},
      );
      if (nativeDeleted == true) return true;
    } on MissingPluginException {
      // Older builds may not have the native cleanup channel yet.
    } on PlatformException {
      // Fall back to direct file deletion when the path is file-system based.
    }

    try {
      final file = File(path);
      if (!await file.exists()) return false;
      await file.delete();
      return true;
    } catch (_) {
      return false;
    }
  }

  /// path가 메모릭스 보관함(`<docs>/memorix/...` 또는
  /// Android `<external>/memorix/...`) 안인지 검사.
  static Future<bool> _isInVault(String path) async {
    final normalized = p.normalize(path);

    final docs = await getApplicationDocumentsDirectory();
    final docsVault = p.normalize(p.join(docs.path, 'memorix'));
    if (_isUnder(normalized, docsVault)) return true;

    if (Platform.isAndroid) {
      try {
        final ext = await getExternalStorageDirectory();
        if (ext != null) {
          final extVault = p.normalize(p.join(ext.path, 'memorix'));
          if (_isUnder(normalized, extVault)) return true;
        }
      } catch (_) {
        // external storage 미지원 환경 — 무시
      }
    }
    return false;
  }

  static bool _isUnder(String child, String parent) {
    if (child == parent) return true;
    return p.isWithin(parent, child);
  }
}
