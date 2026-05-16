import 'dart:io';

import '../../shared/models/media_item.dart';
import '../db/media_dao.dart';
import 'secret_vault_service.dart';

/// 평문 파일 → .enc 변환 함수. 테스트에서는 fake 주입.
typedef EncryptFn = Future<String> Function(String sourcePath);

/// .enc 파일 → 평문 파일 복호화 함수. 테스트에서는 fake 주입.
typedef DecryptFn = Future<String> Function(String encPath);

/// per-item 잠금 토글 — 평문 ↔ .enc 변환 + DB 갱신.
///
/// 핵심 보장:
///  - **Atomic**: 새 파일 생성에 성공한 뒤에만 기존 파일을 삭제한다. 변환
///    실패(권한, 디스크 공간 등) 시 원본은 보존된다. 기존 파일 삭제는
///    best-effort — 실패해도 DB는 새 경로로 갱신된다.
///  - **Idempotent**: 이미 잠금/해제 상태인 항목은 그대로 반환한다.
///  - **MediaType 인지**: photo/video/document 별로 [SecretVaultService]의
///    적절한 저장 메서드를 호출한다. video는 본체와 썸네일을 분리 처리.
///
/// 테스트용으로 [encryptVideo]/[encryptDocument]/[decrypt] 함수 hook을
/// 주입할 수 있다. 기본값은 [SecretVaultService]의 정적 메서드.
class LockToggleService {
  LockToggleService({
    MediaDao? dao,
    EncryptFn? encryptVideo,
    EncryptFn? encryptDocument,
    DecryptFn? decrypt,
  }) : _dao = dao ?? MediaDao(),
       _encryptVideo = encryptVideo ?? SecretVaultService.saveVideo,
       _encryptDocument = encryptDocument ?? SecretVaultService.saveDocument,
       _decrypt = decrypt ?? SecretVaultService.decryptToFile;

  final MediaDao _dao;
  final EncryptFn _encryptVideo;
  final EncryptFn _encryptDocument;
  final DecryptFn _decrypt;

  /// 평문 → .enc로 잠그기. 이미 잠긴 항목은 그대로 반환.
  Future<MediaItem> lock(MediaItem item) async {
    if (item.isLocked == 1) return item;

    final encFilePath = await _encryptByType(item.mediaType, item.filePath);
    String? encThumbPath;
    if (item.thumbPath != null && item.thumbPath != item.filePath) {
      // 사진/비디오 모두 별도 썸네일이 있으면 동일하게 .enc로 변환.
      // _encryptVideo는 임의 파일을 source 보존한 채 암호화하므로 photo thumb
      // 에도 안전하게 사용 가능 — 확장자는 sourcePath에서 추출된다.
      encThumbPath = await _encryptVideo(item.thumbPath!);
    } else if (item.thumbPath == item.filePath) {
      // thumbPath가 본체와 동일하면 enc 본체 경로를 그대로 사용.
      encThumbPath = encFilePath;
    }

    // 변환 성공 후에만 평문 삭제 (atomic).
    await _safeDelete(item.filePath);
    if (item.thumbPath != null && item.thumbPath != item.filePath) {
      await _safeDelete(item.thumbPath!);
    }

    final updated = item.copyWith(
      filePath: encFilePath,
      thumbPath: encThumbPath,
      isLocked: 1,
      encrypted: 1,
    );
    await _dao.update(updated);
    return updated;
  }

  /// .enc → 평문 복원. 이미 해제된 항목은 그대로 반환.
  Future<MediaItem> unlock(MediaItem item) async {
    if (item.isLocked == 0) return item;

    final plainFilePath = await _decrypt(item.filePath);
    String? plainThumbPath;
    if (item.thumbPath != null && item.thumbPath != item.filePath) {
      plainThumbPath = await _decrypt(item.thumbPath!);
    } else if (item.thumbPath == item.filePath) {
      plainThumbPath = plainFilePath;
    }

    await _safeDelete(item.filePath);
    if (item.thumbPath != null && item.thumbPath != item.filePath) {
      await _safeDelete(item.thumbPath!);
    }

    final updated = item.copyWith(
      filePath: plainFilePath,
      thumbPath: plainThumbPath,
      isLocked: 0,
      encrypted: 0,
    );
    await _dao.update(updated);
    return updated;
  }

  // ── 내부 ─────────────────────────────────────────────────────

  Future<String> _encryptByType(MediaType type, String sourcePath) async {
    // savePhoto는 새 썸네일까지 생성하는 부수효과가 있어 lock 흐름엔 부적절
    // (썸네일은 상위에서 별도 처리). saveVideo/saveDocument는 단일 파일을
    // 원본 보존한 채 .enc로 변환하므로 photo/video/document 모두 saveVideo
    // 또는 saveDocument 중 ext에 적합한 쪽을 사용한다.
    switch (type) {
      case MediaType.photo:
      case MediaType.video:
        return _encryptVideo(sourcePath);
      case MediaType.document:
        return _encryptDocument(sourcePath);
    }
  }

  Future<void> _safeDelete(String path) async {
    try {
      final f = File(path);
      if (await f.exists()) await f.delete();
    } on FileSystemException {
      // best-effort — 실패해도 DB는 새 경로로 갱신된 상태.
    }
  }
}
