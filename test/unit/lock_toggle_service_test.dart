import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/core/db/media_dao.dart';
import 'package:memorix/core/services/lock_toggle_service.dart';
import 'package:memorix/shared/models/media_item.dart';

/// LockToggleService 단위 테스트.
///
/// 외부 의존성(`SecretVaultService` 정적 + `MediaDao`의 sqflite)은
/// fake 함수/서브클래스로 대체. 테스트 초점:
///  1. lock/unlock 분기 결과: copyWith로 isLocked, encrypted, filePath 갱신
///  2. Idempotency: 이미 잠금/해제 상태면 변환 함수 호출 없이 그대로 반환
///  3. DB 업데이트가 정확히 한 번 호출되며 갱신된 항목이 전달된다
///  4. 평문 파일 atomic 정리 (fake 파일로 검증)
void main() {
  group('LockToggleService', () {
    test('lock(): 평문 항목을 .enc로 변환하고 DB 업데이트', () async {
      // Arrange
      final tmp = await Directory.systemTemp.createTemp('lock_toggle_');
      addTearDown(() async {
        if (await tmp.exists()) await tmp.delete(recursive: true);
      });
      final plain = File('${tmp.path}/photo.jpg')..writeAsBytesSync([0xFF]);
      final plainThumb = File('${tmp.path}/photo_thumb.jpg')
        ..writeAsBytesSync([0xFE]);

      final dao = _FakeMediaDao();
      final service = LockToggleService(
        dao: dao,
        encryptVideo: (src) async => '$src.enc',
        encryptDocument: (src) async => '$src.enc',
        decrypt: (enc) async => enc.replaceAll('.enc', ''),
      );
      final item = _photoItem(filePath: plain.path, thumbPath: plainThumb.path);

      // Act
      final updated = await service.lock(item);

      // Assert — DB 업데이트 1회
      expect(dao.updateCount, 1);
      expect(dao.lastUpdated, equals(updated));

      // Assert — 잠금 상태 + .enc 경로
      expect(updated.isLocked, 1);
      expect(updated.encrypted, 1);
      expect(updated.filePath, '${plain.path}.enc');
      expect(updated.thumbPath, '${plainThumb.path}.enc');

      // Assert — 평문 파일은 atomic 삭제됨
      expect(await plain.exists(), isFalse);
      expect(await plainThumb.exists(), isFalse);
    });

    test('lock(): 이미 잠긴 항목은 no-op (변환 함수 호출 X)', () async {
      // Arrange
      final dao = _FakeMediaDao();
      var encryptCalled = false;
      final service = LockToggleService(
        dao: dao,
        encryptVideo: (_) async {
          encryptCalled = true;
          return 'never';
        },
        encryptDocument: (_) async => 'never',
        decrypt: (_) async => 'never',
      );
      final locked = _photoItem(
        filePath: '/x/a.jpg.enc',
        thumbPath: '/x/a_thumb.jpg.enc',
      ).copyWith(isLocked: 1, encrypted: 1);

      // Act
      final result = await service.lock(locked);

      // Assert
      expect(identical(result, locked), isTrue, reason: '동일 인스턴스 반환');
      expect(encryptCalled, isFalse);
      expect(dao.updateCount, 0);
    });

    test('unlock(): .enc 항목을 평문으로 복원하고 DB 업데이트', () async {
      // Arrange
      final tmp = await Directory.systemTemp.createTemp('lock_toggle_');
      addTearDown(() async {
        if (await tmp.exists()) await tmp.delete(recursive: true);
      });
      final enc = File('${tmp.path}/a.jpg.enc')..writeAsBytesSync([0x01]);
      final encThumb = File('${tmp.path}/a_thumb.jpg.enc')
        ..writeAsBytesSync([0x02]);

      final dao = _FakeMediaDao();
      final service = LockToggleService(
        dao: dao,
        encryptVideo: (src) async => '$src.enc',
        encryptDocument: (src) async => '$src.enc',
        decrypt: (encPath) async => encPath.replaceAll('.enc', ''),
      );
      final item = _photoItem(filePath: enc.path, thumbPath: encThumb.path)
          .copyWith(isLocked: 1, encrypted: 1);

      // Act
      final updated = await service.unlock(item);

      // Assert
      expect(dao.updateCount, 1);
      expect(updated.isLocked, 0);
      expect(updated.encrypted, 0);
      expect(updated.filePath, enc.path.replaceAll('.enc', ''));
      expect(updated.thumbPath, encThumb.path.replaceAll('.enc', ''));

      // Assert — .enc 파일은 atomic 삭제됨
      expect(await enc.exists(), isFalse);
      expect(await encThumb.exists(), isFalse);
    });

    test('unlock(): 이미 해제된 항목은 no-op (복호화 함수 호출 X)', () async {
      // Arrange
      final dao = _FakeMediaDao();
      var decryptCalled = false;
      final service = LockToggleService(
        dao: dao,
        encryptVideo: (_) async => 'never',
        encryptDocument: (_) async => 'never',
        decrypt: (_) async {
          decryptCalled = true;
          return 'never';
        },
      );
      final unlocked = _photoItem(filePath: '/x/a.jpg', thumbPath: null);

      // Act
      final result = await service.unlock(unlocked);

      // Assert
      expect(identical(result, unlocked), isTrue);
      expect(decryptCalled, isFalse);
      expect(dao.updateCount, 0);
    });

    test('lock(): 변환 실패 시 평문 파일 보존 (atomic)', () async {
      // Arrange — 평문 파일 존재. encrypt 함수가 throw.
      final tmp = await Directory.systemTemp.createTemp('lock_toggle_');
      addTearDown(() async {
        if (await tmp.exists()) await tmp.delete(recursive: true);
      });
      final plain = File('${tmp.path}/photo.jpg')..writeAsBytesSync([0xAA]);

      final dao = _FakeMediaDao();
      final service = LockToggleService(
        dao: dao,
        encryptVideo: (_) async => throw const FileSystemException('disk full'),
        encryptDocument: (_) async => 'never',
        decrypt: (_) async => 'never',
      );
      final item = _photoItem(filePath: plain.path, thumbPath: null);

      // Act + Assert — 예외 전파
      await expectLater(
        service.lock(item),
        throwsA(isA<FileSystemException>()),
      );

      // 평문 파일 보존, DB 업데이트 안 됨
      expect(await plain.exists(), isTrue);
      expect(dao.updateCount, 0);
    });

    test('lock(): 비디오는 본체와 썸네일을 별도로 변환', () async {
      // Arrange
      final tmp = await Directory.systemTemp.createTemp('lock_toggle_');
      addTearDown(() async {
        if (await tmp.exists()) await tmp.delete(recursive: true);
      });
      final video = File('${tmp.path}/v.mp4')..writeAsBytesSync([0x00]);
      final thumb = File('${tmp.path}/v_thumb.jpg')..writeAsBytesSync([0x01]);

      final encryptedSources = <String>[];
      final dao = _FakeMediaDao();
      final service = LockToggleService(
        dao: dao,
        encryptVideo: (src) async {
          encryptedSources.add(src);
          return '$src.enc';
        },
        encryptDocument: (_) async => 'never',
        decrypt: (_) async => 'never',
      );
      final item = _photoItem(
        filePath: video.path,
        thumbPath: thumb.path,
      ).copyWith(mediaType: MediaType.video);

      // Act
      final updated = await service.lock(item);

      // Assert — 본체와 썸네일 모두 변환됨
      expect(encryptedSources, [video.path, thumb.path]);
      expect(updated.filePath, '${video.path}.enc');
      expect(updated.thumbPath, '${thumb.path}.enc');
    });
  });
}

// ── helpers ────────────────────────────────────────────────────

MediaItem _photoItem({required String filePath, String? thumbPath}) {
  return MediaItem(
    id: 1,
    space: MediaSpace.personal,
    mediaType: MediaType.photo,
    filePath: filePath,
    thumbPath: thumbPath,
    takenAt: 0,
    createdAt: 0,
  );
}

class _FakeMediaDao extends MediaDao {
  int updateCount = 0;
  MediaItem? lastUpdated;

  @override
  Future<int> update(MediaItem item) async {
    updateCount++;
    lastUpdated = item;
    return 1;
  }
}
