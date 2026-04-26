import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:cryptography/cryptography.dart';
import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';

/// Secret 영역 전용 암호화 보관함.
///
/// - 원본 미디어는 AES-GCM으로 암호화하여 `secret_vault/`에 저장
/// - 썸네일도 동일하게 암호화 (300x200, 75% 품질)
/// - 디렉터리에 `.nomedia` 마커를 두어 Android MediaScanner가 무시하게 함
/// - 마스터 키는 256bit, `flutter_secure_storage` (iOS Keychain / Android Keystore)
///
/// 파일 형식: `[12B nonce][16B mac][ciphertext]` — 단일 청크
class SecretVaultService {
  static const _keyAlias = 'memorix_secret_vault_key_v1';
  static final _algorithm = AesGcm.with256bits();
  static const _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );
  static const _uuid = Uuid();
  static final _rand = Random.secure();

  static SecretKey? _cachedKey;

  // ── 키 관리 ───────────────────────────────────────────────

  /// 마스터 키를 안전 저장소에서 가져오거나 새로 생성한다.
  static Future<SecretKey> _getOrCreateKey() async {
    if (_cachedKey != null) return _cachedKey!;
    final stored = await _storage.read(key: _keyAlias);
    if (stored != null && stored.isNotEmpty) {
      final bytes = base64Decode(stored);
      _cachedKey = SecretKey(bytes);
      return _cachedKey!;
    }
    final bytes = Uint8List(32);
    for (var i = 0; i < bytes.length; i++) {
      bytes[i] = _rand.nextInt(256);
    }
    await _storage.write(key: _keyAlias, value: base64Encode(bytes));
    _cachedKey = SecretKey(bytes);
    return _cachedKey!;
  }

  // ── 경로 ──────────────────────────────────────────────────

  static Future<String> _vaultDir() async {
    final base = await getApplicationDocumentsDirectory();
    final dir = Directory(p.join(base.path, 'memorix', 'secret_vault'));
    if (!await dir.exists()) {
      await dir.create(recursive: true);
      // Android MediaScanner가 이 디렉터리를 인덱싱하지 않도록 마커 생성
      final nomedia = File(p.join(dir.path, '.nomedia'));
      if (!await nomedia.exists()) {
        await nomedia.create();
      }
    }
    return dir.path;
  }

  // ── 저장 ──────────────────────────────────────────────────

  /// 사진을 암호화하여 vault에 저장하고 [filePath, thumbPath]를 반환.
  /// 두 경로 모두 `.enc` 확장자.
  static Future<({String filePath, String thumbPath})> savePhoto(
    String sourcePath,
  ) async {
    final dir = await _vaultDir();
    final id = _uuid.v4();
    final filePath = p.join(dir, '$id.jpg.enc');
    final thumbPath = p.join(dir, '${id}_thumb.jpg.enc');

    final originalBytes = await File(sourcePath).readAsBytes();
    await _encryptToFile(originalBytes, filePath);

    // 썸네일 — 압축 후 암호화
    Uint8List? thumbBytes;
    try {
      thumbBytes = await FlutterImageCompress.compressWithFile(
        sourcePath,
        minWidth: 300,
        minHeight: 200,
        quality: 75,
      ).timeout(const Duration(seconds: 5));
    } catch (_) {
      thumbBytes = null;
    }
    if (thumbBytes == null || thumbBytes.isEmpty) {
      // 썸네일 생성 실패 시 원본 바이트로 대체
      await _encryptToFile(originalBytes, thumbPath);
    } else {
      await _encryptToFile(thumbBytes, thumbPath);
    }

    return (filePath: filePath, thumbPath: thumbPath);
  }

  /// 영상 원본을 암호화하여 vault에 저장. 썸네일은 별도 메서드 필요.
  static Future<String> saveVideo(String sourcePath) async {
    final dir = await _vaultDir();
    final id = _uuid.v4();
    final ext = p.extension(sourcePath).isEmpty
        ? '.mp4'
        : p.extension(sourcePath);
    final destPath = p.join(dir, '$id$ext.enc');

    final bytes = await File(sourcePath).readAsBytes();
    await _encryptToFile(bytes, destPath);
    return destPath;
  }

  /// 이미 디스크에 만들어진 비디오 썸네일을 vault로 옮겨 암호화.
  /// 원본 썸네일은 삭제한다.
  static Future<String> saveVideoThumb(String sourcePath) async {
    final dir = await _vaultDir();
    final id = _uuid.v4();
    final destPath = p.join(dir, '${id}_thumb.jpg.enc');
    final bytes = await File(sourcePath).readAsBytes();
    await _encryptToFile(bytes, destPath);
    try {
      await File(sourcePath).delete();
    } catch (_) {}
    return destPath;
  }

  /// 문서 파일 암호화 저장.
  static Future<String> saveDocument(String sourcePath) async {
    final dir = await _vaultDir();
    final id = _uuid.v4();
    final ext = p.extension(sourcePath);
    final destPath = p.join(dir, '$id$ext.enc');
    final bytes = await File(sourcePath).readAsBytes();
    await _encryptToFile(bytes, destPath);
    return destPath;
  }

  // ── 복호화 ────────────────────────────────────────────────

  /// 암호화 파일을 메모리로 복호화. 작은 미디어/썸네일에 적합.
  static Future<Uint8List> decryptToBytes(String encryptedPath) async {
    final raw = await File(encryptedPath).readAsBytes();
    return _decryptBytes(raw);
  }

  /// 큰 파일(영상 등)은 임시 디렉터리에 평문 복호화. 호출자가 정리 책임.
  /// `tmpExtension`은 `.mp4` 같은 식으로 player가 인식하도록 지정.
  static Future<File> decryptToTempFile(
    String encryptedPath, {
    String tmpExtension = '.bin',
  }) async {
    final tmpDir = await getTemporaryDirectory();
    final dir = Directory(p.join(tmpDir.path, 'memorix_secret_decrypt'));
    if (!await dir.exists()) await dir.create(recursive: true);
    final outPath = p.join(dir.path, '${_uuid.v4()}$tmpExtension');
    final bytes = await decryptToBytes(encryptedPath);
    final file = File(outPath);
    await file.writeAsBytes(bytes, flush: true);
    return file;
  }

  /// 임시 복호화 디렉터리 비우기 (앱 잠금/백그라운드 진입 시 호출).
  static Future<void> purgeTempDecrypted() async {
    try {
      final tmpDir = await getTemporaryDirectory();
      final dir = Directory(p.join(tmpDir.path, 'memorix_secret_decrypt'));
      if (await dir.exists()) {
        await dir.delete(recursive: true);
      }
    } catch (_) {}
  }

  // ── 삭제 ──────────────────────────────────────────────────

  static Future<void> deleteVaultFile(String? path) async {
    if (path == null || path.isEmpty) return;
    try {
      final f = File(path);
      if (await f.exists()) await f.delete();
    } catch (_) {}
  }

  // ── 내부: AES-GCM ─────────────────────────────────────────

  static Future<void> _encryptToFile(
    List<int> plaintext,
    String destPath,
  ) async {
    final key = await _getOrCreateKey();
    final nonce = _algorithm.newNonce();
    final box = await _algorithm.encrypt(
      plaintext,
      secretKey: key,
      nonce: nonce,
    );
    final mac = box.mac.bytes;
    final out = BytesBuilder()
      ..add(nonce)
      ..add(mac)
      ..add(box.cipherText);
    await File(destPath).writeAsBytes(out.toBytes(), flush: true);
  }

  static Future<Uint8List> _decryptBytes(Uint8List raw) async {
    if (raw.length < 12 + 16) {
      throw const FormatException('Encrypted blob too short');
    }
    final nonce = raw.sublist(0, 12);
    final mac = Mac(raw.sublist(12, 28));
    final cipher = raw.sublist(28);
    final key = await _getOrCreateKey();
    final clear = await _algorithm.decrypt(
      SecretBox(cipher, nonce: nonce, mac: mac),
      secretKey: key,
    );
    return Uint8List.fromList(clear);
  }
}
