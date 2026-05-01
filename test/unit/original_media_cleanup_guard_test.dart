import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/core/services/media_capture_service.dart';
import 'package:memorix/core/services/original_media_cleanup_service.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider_platform_interface/path_provider_platform_interface.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

/// Bug #5 회귀 가드: OriginalMediaCleanupService가 보관함 path를 절대 삭제하면 안 된다.
///
/// 배경:
/// - CapturedMedia.filePath는 StorageService.savePhoto가 반환한 보관함 final 경로다.
/// - native MediaStore 채널이 그 path를 못 찾으면 fallback으로 file.delete()가 돌았다.
/// - 결과: 보관함 파일이 삭제되어 다음 화면 진입 시 사진이 사라졌다.
///
/// 가드 원칙:
/// - path가 메모릭스 보관함 안(`<docs>/memorix/...`)이면 native 호출 자체를 건너뛰고
///   결과를 false (failed)로 반환한다. 절대 file.delete()로 떨어지지 않는다.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const cleanupChannel = MethodChannel('memorix/original_media_cleanup');
  final messenger = TestDefaultBinaryMessengerBinding
      .instance.defaultBinaryMessenger;

  setUp(() {
    PathProviderPlatform.instance = _FakePathProvider();
    // native가 false를 반환해서 fallback 경로로 떨어지는 시나리오를 시뮬레이션.
    // (가드가 없으면 fallback file.delete()가 보관함을 지움)
    messenger.setMockMethodCallHandler(cleanupChannel, (call) async {
      if (call.method == 'deleteOriginal') return false;
      return null;
    });
  });

  tearDown(() {
    messenger.setMockMethodCallHandler(cleanupChannel, null);
  });

  group('OriginalMediaCleanupService — 보관함 path 보호', () {
    test('보관함 안 path는 fallback에서 삭제되지 않는다', () async {
      final tmp = await Directory.systemTemp.createTemp('memorix_test_');
      addTearDown(() async {
        if (await tmp.exists()) await tmp.delete(recursive: true);
      });

      _FakePathProvider.tempDocsRoot = tmp.path;

      // 보관함 안에 진짜 파일을 만든다.
      final photosDir = Directory(
        p.join(tmp.path, 'memorix', 'photos', '2026', '04'),
      );
      await photosDir.create(recursive: true);
      final vaultFile = File(p.join(photosDir.path, 'vault.jpg'));
      await vaultFile.writeAsBytes([0xFF, 0xD8, 0xFF]); // JPEG magic

      final captured = [
        CapturedMedia(
          filePath: vaultFile.path,
          mediaType: 'photo',
          fileSizeKb: 1,
        ),
      ];

      final result = await OriginalMediaCleanupService.deleteOriginals(captured);

      // 1) 진짜로 보관함 파일이 살아있어야 한다 (가장 중요한 가드).
      expect(
        await vaultFile.exists(),
        isTrue,
        reason: '🚨 DATA LOSS: 보관함 파일이 삭제됨. 가드가 깨졌다.',
      );

      // 2) 결과가 failed로 보고되어야 한다 (성공이라고 거짓말 X).
      expect(result.deleted, 0, reason: '보관함 파일은 삭제 대상이 아님 — deleted=0');
      expect(result.failed, 1, reason: '보관함 path는 거부되어 failed=1');
    });

    test('multiple 항목 중 보관함 path는 보호되고 비-보관함은 fallback 처리된다', () async {
      final tmp = await Directory.systemTemp.createTemp('memorix_test_');
      addTearDown(() async {
        if (await tmp.exists()) await tmp.delete(recursive: true);
      });

      _FakePathProvider.tempDocsRoot = tmp.path;

      final photosDir = Directory(p.join(tmp.path, 'memorix', 'photos'));
      await photosDir.create(recursive: true);
      final vaultFile = File(p.join(photosDir.path, 'vault.jpg'));
      await vaultFile.writeAsBytes([0xFF]);

      // 외부 임시 경로(가짜 갤러리 원본 시뮬레이션) — 보관함 밖.
      final extDir = await Directory.systemTemp.createTemp('ext_');
      addTearDown(() async {
        if (await extDir.exists()) await extDir.delete(recursive: true);
      });
      final extFile = File(p.join(extDir.path, 'ext.jpg'));
      await extFile.writeAsBytes([0xFF]);

      final captured = [
        CapturedMedia(
          filePath: vaultFile.path,
          mediaType: 'photo',
          fileSizeKb: 1,
        ),
        CapturedMedia(
          filePath: extFile.path,
          mediaType: 'photo',
          fileSizeKb: 1,
        ),
      ];

      await OriginalMediaCleanupService.deleteOriginals(captured);

      // 보관함 파일은 무조건 살아있어야 한다.
      expect(
        await vaultFile.exists(),
        isTrue,
        reason: '🚨 DATA LOSS: multiple 케이스에서 보관함 파일이 삭제됨',
      );
    });
  });
}

// ── Test fakes ──────────────────────────────────────────────

class _FakePathProvider extends PathProviderPlatform with MockPlatformInterfaceMixin {
  static String? tempDocsRoot;

  @override
  Future<String?> getApplicationDocumentsPath() async => tempDocsRoot;

  @override
  Future<String?> getApplicationSupportPath() async => tempDocsRoot;

  @override
  Future<String?> getTemporaryPath() async => tempDocsRoot;
}
