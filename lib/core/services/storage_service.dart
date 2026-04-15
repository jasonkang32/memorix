import 'dart:io';
import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';

// ── 저장소 위치 ───────────────────────────────────────────────
enum StorageLocation { internal, external }

// ── 사진 저장 품질 ────────────────────────────────────────────
enum PhotoQuality {
  original(label: '원본', desc: '품질 손실 없음', quality: 100),
  balanced(label: '균형', desc: '85% 품질 · 약 40% 절약', quality: 85),
  compact(label: '절약', desc: '65% 품질 · 약 70% 절약', quality: 65);

  final String label;
  final String desc;
  final int quality;
  const PhotoQuality({required this.label, required this.desc, required this.quality});
}

class StorageService {
  static const _uuid = Uuid();
  static const _storage = FlutterSecureStorage();
  static const _keyLocation = 'storage_location';
  static const _keyQuality = 'photo_quality';

  // ── 설정 읽기/저장 ─────────────────────────────────────────

  static Future<StorageLocation> getStorageLocation() async {
    final v = await _storage.read(key: _keyLocation);
    if (v == 'external' && Platform.isAndroid) return StorageLocation.external;
    return StorageLocation.internal;
  }

  static Future<void> setStorageLocation(StorageLocation loc) async {
    await _storage.write(key: _keyLocation, value: loc.name);
  }

  static Future<PhotoQuality> getPhotoQuality() async {
    final v = await _storage.read(key: _keyQuality);
    return PhotoQuality.values.firstWhere(
      (q) => q.name == v,
      orElse: () => PhotoQuality.original,
    );
  }

  static Future<void> setPhotoQuality(PhotoQuality quality) async {
    await _storage.write(key: _keyQuality, value: quality.name);
  }

  // ── 저장소 기본 경로 ────────────────────────────────────────

  static Future<String> get _baseDir async {
    final loc = await getStorageLocation();
    if (loc == StorageLocation.external && Platform.isAndroid) {
      final ext = await getExternalStorageDirectory();
      if (ext != null) return p.join(ext.path, 'memorix');
    }
    final dir = await getApplicationDocumentsDirectory();
    return p.join(dir.path, 'memorix');
  }

  /// 내부 저장소 기본 경로 (DB 전용 — 항상 내부)
  static Future<String> get internalBaseDir async {
    final dir = await getApplicationDocumentsDirectory();
    return p.join(dir.path, 'memorix');
  }

  static Future<String> _ensureDir(String subPath) async {
    final base = await _baseDir;
    final dir = Directory(p.join(base, subPath));
    if (!dir.existsSync()) dir.createSync(recursive: true);
    return dir.path;
  }

  // ── 파일 저장 ───────────────────────────────────────────────

  /// 사진 파일을 저장소로 복사하고 [filePath, thumbPath]를 반환
  static Future<({String filePath, String thumbPath})> savePhoto(
    String sourcePath,
  ) async {
    final now = DateTime.now();
    final sub = p.join(
        'photos', now.year.toString(), now.month.toString().padLeft(2, '0'));
    final dir = await _ensureDir(sub);
    final id = _uuid.v4();
    final quality = await getPhotoQuality();

    final destPath = p.join(dir, '$id.jpg');
    if (quality == PhotoQuality.original) {
      await File(sourcePath).copy(destPath);
    } else {
      // 지정 품질로 압축 저장
      try {
        final result = await FlutterImageCompress.compressAndGetFile(
          sourcePath,
          destPath,
          quality: quality.quality,
        );
        if (result == null) {
          // 압축 결과 null → 원본 복사
          await File(sourcePath).copy(destPath);
        }
      } catch (_) {
        // 압축 실패 시 원본 복사
        await File(sourcePath).copy(destPath);
      }
    }

    // 썸네일 생성 (항상 압축)
    String thumbPath = p.join(dir, '${id}_thumb.jpg');
    try {
      final result = await FlutterImageCompress.compressAndGetFile(
        destPath,
        thumbPath,
        minWidth: 300,
        minHeight: 200,
        quality: 75,
      );
      if (result == null) thumbPath = destPath; // 실패 시 원본 경로 사용
    } catch (_) {
      thumbPath = destPath; // 압축 실패 시 원본 경로 사용
    }

    return (filePath: destPath, thumbPath: thumbPath);
  }

  /// 영상 파일을 저장소로 복사하고 [filePath]를 반환
  static Future<String> saveVideo(String sourcePath) async {
    final now = DateTime.now();
    final sub = p.join(
        'videos', now.year.toString(), now.month.toString().padLeft(2, '0'));
    final dir = await _ensureDir(sub);
    final id = _uuid.v4();
    final ext =
        p.extension(sourcePath).isEmpty ? '.mp4' : p.extension(sourcePath);
    final destPath = p.join(dir, '$id$ext');
    await File(sourcePath).copy(destPath);
    return destPath;
  }

  /// 문서 파일을 저장소로 복사하고 [filePath]를 반환
  static Future<String> saveDocument(String sourcePath) async {
    final now = DateTime.now();
    final sub = p.join('documents', now.year.toString(),
        now.month.toString().padLeft(2, '0'));
    final dir = await _ensureDir(sub);
    final id = _uuid.v4();
    final ext = p.extension(sourcePath);
    final destPath = p.join(dir, '$id$ext');
    await File(sourcePath).copy(destPath);
    return destPath;
  }

  /// 보고서 PDF 저장 (항상 내부 저장소)
  static Future<String> saveReport(List<int> bytes) async {
    final base = await internalBaseDir;
    final dir = Directory(p.join(base, 'reports'));
    if (!dir.existsSync()) dir.createSync(recursive: true);
    final id = _uuid.v4();
    final path = p.join(dir.path, '${id}_report.pdf');
    await File(path).writeAsBytes(bytes);
    return path;
  }

  static Future<void> deleteFile(String path) async {
    final f = File(path);
    if (f.existsSync()) await f.delete();
  }

  static int fileSizeKb(String path) {
    final f = File(path);
    return f.existsSync() ? (f.lengthSync() / 1024).ceil() : 0;
  }

  // ── 저장소 사용량 분석 ──────────────────────────────────────

  /// 현재 저장소 위치의 사용량 상세 분석
  static Future<StorageBreakdown> calcBreakdown() async {
    try {
      final base = await _baseDir;
      return _calcForPath(base);
    } catch (_) {
      return StorageBreakdown.zero();
    }
  }

  /// 내부 저장소 사용량 (설정 화면 전용)
  static Future<StorageBreakdown> calcInternalBreakdown() async {
    try {
      final base = await internalBaseDir;
      return _calcForPath(base);
    } catch (_) {
      return StorageBreakdown.zero();
    }
  }

  static Future<StorageBreakdown> _calcForPath(String base) async {
    final baseDir = Directory(base);
    if (!baseDir.existsSync()) return StorageBreakdown.zero();

    int photos = 0, videos = 0, documents = 0, reports = 0, db = 0;

    await for (final entity in baseDir.list(recursive: true)) {
      if (entity is! File) continue;
      final size = entity.lengthSync();
      final rel = p.relative(entity.path, from: base);
      if (rel.startsWith('photos')) {
        photos += size;
      } else if (rel.startsWith('videos')) {
        videos += size;
      } else if (rel.startsWith('documents')) {
        documents += size;
      } else if (rel.startsWith('reports')) {
        reports += size;
      } else if (rel.startsWith('db')) {
        db += size;
      }
    }
    return StorageBreakdown(
      photos: photos,
      videos: videos,
      documents: documents,
      reports: reports,
      db: db,
    );
  }
}

// ── 저장소 사용량 모델 ────────────────────────────────────────

class StorageBreakdown {
  final int photos;
  final int videos;
  final int documents;
  final int reports;
  final int db;

  const StorageBreakdown({
    required this.photos,
    required this.videos,
    required this.documents,
    required this.reports,
    required this.db,
  });

  factory StorageBreakdown.zero() => const StorageBreakdown(
      photos: 0, videos: 0, documents: 0, reports: 0, db: 0);

  int get total => photos + videos + documents + reports + db;

  String _fmt(int bytes) {
    if (bytes == 0) return '0 KB';
    if (bytes < 1024 * 1024)
      return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024)
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }

  String get totalLabel => _fmt(total);
  String get photosLabel => _fmt(photos);
  String get videosLabel => _fmt(videos);
  String get documentsLabel => _fmt(documents);
  String get reportsLabel => _fmt(reports);
  String get dbLabel => _fmt(db);
}
