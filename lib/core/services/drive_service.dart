import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:googleapis/drive/v3.dart' as drive;
import 'package:http/http.dart' as http;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../shared/models/media_item.dart';
import '../db/media_dao.dart';

// ── OAuth ──
final _googleSignIn = GoogleSignIn(
  scopes: ['https://www.googleapis.com/auth/drive.file'],
);
const _storage = FlutterSecureStorage();
const _signedInKey = 'drive_signed_in';
const _accountEmailKey = 'drive_account_email';
const _accountNameKey = 'drive_account_name';

/// Access token을 매 요청마다 fresh하게 가져오는 HTTP 클라이언트
/// (GoogleSignInAccount.authentication은 자동으로 토큰 갱신 처리)
class _AuthClient extends http.BaseClient {
  final GoogleSignInAccount _account;
  final http.Client _inner = http.Client();
  _AuthClient(this._account);

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    final auth = await _account.authentication;
    request.headers['Authorization'] = 'Bearer ${auth.accessToken}';
    return _inner.send(request);
  }
}

class DriveService {
  static drive.DriveApi? _api;
  static GoogleSignInAccount? _account;

  // ── 인증 ──
  static Future<String?> signIn() async {
    try {
      final account = await _googleSignIn.signIn();
      if (account == null) return '로그인 취소됨';
      _account = account;
      _api = drive.DriveApi(_AuthClient(account));
      await Future.wait([
        _storage.write(key: _signedInKey, value: '1'),
        _storage.write(key: _accountEmailKey, value: account.email),
        _storage.write(key: _accountNameKey, value: account.displayName ?? ''),
      ]);
      return null; // null = 성공
    } on Exception catch (e) {
      final msg = e.toString();
      if (msg.contains('10') || msg.contains('DEVELOPER_ERROR')) {
        return 'Google Cloud 설정 필요 (DEVELOPER_ERROR)\n'
            '→ google-services.json 파일을 android/app/ 에 추가하세요.';
      }
      if (msg.contains('network') || msg.contains('NetworkError')) {
        return '네트워크 연결을 확인하세요';
      }
      return '로그인 실패: $msg';
    }
  }

  static Future<void> signOut() async {
    await _googleSignIn.signOut();
    _api = null;
    _account = null;
    await Future.wait([
      _storage.delete(key: _signedInKey),
      _storage.delete(key: _accountEmailKey),
      _storage.delete(key: _accountNameKey),
    ]);
  }

  static Future<bool> get isSignedIn async {
    final v = await _storage.read(key: _signedInKey);
    return v == '1';
  }

  static Future<DriveAccountInfo?> get accountInfo async {
    final email = await _storage.read(key: _accountEmailKey);
    if (email == null) return null;
    final name = await _storage.read(key: _accountNameKey) ?? '';
    return DriveAccountInfo(email: email, displayName: name);
  }

  /// API 사용 준비 (없으면 자동 재연결 시도)
  static Future<bool> _ensureApi() async {
    if (_api != null && _account != null) return true;
    try {
      final account = await _googleSignIn.signInSilently();
      if (account == null) return false;
      _account = account;
      _api = drive.DriveApi(_AuthClient(account));
      return true;
    } catch (_) {
      _api = null;
      _account = null;
      return false;
    }
  }

  // ── 폴더 생성 또는 조회 ──
  static Future<String?> _ensureFolder(String name, {String? parentId}) async {
    final api = _api!;
    final q = StringBuffer(
      "mimeType='application/vnd.google-apps.folder' and name='$name' and trashed=false",
    );
    if (parentId != null) q.write(" and '$parentId' in parents");

    final list = await api.files.list(
      q: q.toString(),
      spaces: 'drive',
      $fields: 'files(id,name)',
    );
    if (list.files != null && list.files!.isNotEmpty) {
      return list.files!.first.id;
    }

    final folder = drive.File()
      ..name = name
      ..mimeType = 'application/vnd.google-apps.folder'
      ..parents = parentId != null ? [parentId] : null;

    final created = await api.files.create(folder, $fields: 'id');
    return created.id;
  }

  // ── 미디어 파일 1개 업로드 ──
  static Future<String?> uploadMedia(MediaItem item) async {
    if (!await _ensureApi()) return null;
    final api = _api!;
    final file = File(item.filePath);
    if (!file.existsSync()) return null;

    // 폴더 경로 구성
    final rootId = await _ensureFolder('Memorix');
    if (rootId == null) return null;

    String parentId;
    if (item.space == MediaSpace.work) {
      final workId = await _ensureFolder('Work', parentId: rootId);
      if (workId == null) return null;
      String locationParentId;
      if (item.countryCode.isNotEmpty) {
        final countryId = await _ensureFolder(
          item.countryCode,
          parentId: workId,
        );
        if (countryId == null) return null;
        if (item.region.isNotEmpty) {
          locationParentId =
              (await _ensureFolder(item.region, parentId: countryId)) ??
              countryId;
        } else {
          locationParentId = countryId;
        }
      } else {
        locationParentId = workId;
      }
      // 문서는 docs/ 서브폴더로 분리
      if (item.mediaType == MediaType.document) {
        parentId =
            (await _ensureFolder('docs', parentId: locationParentId)) ??
            locationParentId;
      } else {
        parentId = locationParentId;
      }
    } else {
      final personalId = await _ensureFolder('Personal', parentId: rootId);
      if (personalId == null) return null;
      // 문서는 docs/ 서브폴더로 분리
      if (item.mediaType == MediaType.document) {
        parentId =
            (await _ensureFolder('docs', parentId: personalId)) ?? personalId;
      } else {
        parentId = personalId;
      }
    }

    final mime = _mimeType(item.filePath);
    final driveFile = drive.File()
      ..name = file.path.split('/').last
      ..parents = [parentId];

    final media = drive.Media(
      file.openRead(),
      file.lengthSync(),
      contentType: mime,
    );
    final result = await api.files.create(
      driveFile,
      uploadMedia: media,
      $fields: 'id',
    );
    return result.id;
  }

  // ── 동기화 대기 큐 처리 ──
  static Future<SyncResult> syncPending() async {
    if (!await _ensureApi()) {
      return SyncResult(success: 0, failed: 0, skipped: 0, notSignedIn: true);
    }
    final dao = MediaDao();
    final pending = await dao.findPendingSync();
    int success = 0, failed = 0;

    for (final item in pending) {
      try {
        final driveId = await uploadMedia(item);
        if (driveId != null) {
          await dao.markSynced(item.id!, driveId);
          success++;
        } else {
          failed++;
        }
      } on Exception catch (_) {
        // 401 등 인증 오류 시 API 리셋하여 다음 ensureApi에서 재연결
        _api = null;
        failed++;
      }
    }
    return SyncResult(success: success, failed: failed, skipped: 0);
  }

  static String _mimeType(String path) {
    final ext = path.split('.').last.toLowerCase();
    return switch (ext) {
      'jpg' || 'jpeg' => 'image/jpeg',
      'png' => 'image/png',
      'mp4' => 'video/mp4',
      'mov' => 'video/quicktime',
      'pdf' => 'application/pdf',
      'doc' => 'application/msword',
      'docx' =>
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      'xls' => 'application/vnd.ms-excel',
      'xlsx' =>
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      _ => 'application/octet-stream',
    };
  }
}

class SyncResult {
  final int success;
  final int failed;
  final int skipped;
  final bool notSignedIn;
  const SyncResult({
    required this.success,
    required this.failed,
    required this.skipped,
    this.notSignedIn = false,
  });

  bool get hasFailures => failed > 0;
  String get summary {
    if (notSignedIn) return 'Drive 미연결 — 설정에서 Google 계정을 연결하세요';
    if (failed > 0) return '동기화: $success개 완료, $failed개 실패';
    if (success == 0) return '동기화할 파일이 없습니다';
    return '동기화 완료: $success개 업로드';
  }
}

class DriveAccountInfo {
  final String email;
  final String displayName;
  const DriveAccountInfo({required this.email, required this.displayName});
}

// ── Riverpod Provider ──
final driveSignedInProvider = FutureProvider<bool>(
  (ref) => DriveService.isSignedIn,
);
final driveAccountInfoProvider = FutureProvider<DriveAccountInfo?>(
  (ref) => DriveService.accountInfo,
);
final driveSyncProvider = StateProvider<SyncResult?>((_) => null);
