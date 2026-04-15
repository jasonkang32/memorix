import 'dart:developer' as developer;
import 'dart:io';
import 'package:exif/exif.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:file_picker/file_picker.dart';
import 'package:video_thumbnail/video_thumbnail.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';
import 'storage_service.dart';

const _pickerChannel = MethodChannel('memorix/picker');

const _uuid = Uuid();

enum CaptureSource { camera, gallery, file }

class CapturedMedia {
  final String filePath;
  final String? thumbPath;
  final String mediaType; // 'photo' | 'video' | 'document'
  final int fileSizeKb;
  final int durationSec;
  final double? latitude;
  final double? longitude;
  final int? takenAt; // EXIF 촬영일시 (milliseconds)

  const CapturedMedia({
    required this.filePath,
    this.thumbPath,
    required this.mediaType,
    required this.fileSizeKb,
    this.durationSec = 0,
    this.latitude,
    this.longitude,
    this.takenAt,
  });
}

class MediaCaptureService {
  static final _picker = ImagePicker();

  /// 카메라 촬영 (단일) — 임시 파일만 정리, 갤러리 원본은 유지
  static Future<List<CapturedMedia>> capturePhoto() async {
    final xfile = await _picker.pickImage(
      source: ImageSource.camera,
      imageQuality: 95,
    );
    if (xfile == null) return [];
    // Android에서도 content:// URI가 올 수 있으므로 saveTo()로 복사
    final tmpDir = await getTemporaryDirectory();
    final tmpPath = p.join(tmpDir.path, '${_uuid.v4()}.jpg');
    await xfile.saveTo(tmpPath);
    final result = await _processPhoto(tmpPath, deleteSource: true);
    return [result];
  }

  /// 갤러리에서 사진+영상 다중선택
  /// 네이티브 MethodChannel 로 ACTION_PICK_IMAGES 인텐트 직접 호출 —
  /// Android 13+ 시스템 포토 피커는 탭만으로 다중선택 모드 활성화 (long-press 불필요)
  static Future<List<CapturedMedia>> pickGallery(BuildContext context) async {
    List<String> paths = const <String>[];
    try {
      final List<dynamic>? raw = await _pickerChannel
          .invokeMethod<List<dynamic>>('pickMedia', {'maxItems': 50});
      paths = raw?.cast<String>() ?? const <String>[];
      developer.log('pickGallery: native returned ${paths.length} paths',
          name: 'memorix.picker');
    } catch (e, st) {
      developer.log('pickGallery: invokeMethod failed: $e',
          error: e, stackTrace: st, name: 'memorix.picker');
      return [];
    }
    if (paths.isEmpty) return [];

    // 네이티브가 이미 캐시 디렉토리로 복사한 파일 경로 반환 — 처리 후 삭제
    final results = <CapturedMedia>[];
    for (final path in paths) {
      try {
        final ext = p.extension(path).toLowerCase();
        final isVideo = _videoExtensions.contains(ext);
        if (isVideo) {
          results.add(await _processVideo(path, deleteSource: true));
        } else {
          results.add(await _processPhoto(path, deleteSource: true));
        }
      } catch (_) {
        try { File(path).deleteSync(); } catch (_) {}
      }
    }
    return results;
  }

  static const _videoExtensions = {
    '.mp4', '.mov', '.avi', '.mkv', '.webm',
    '.3gp', '.3g2', '.m4v', '.wmv', '.ts', '.flv', '.f4v',
  };

  /// 문서 가져오기
  static Future<List<CapturedMedia>> pickDocument(BuildContext context) async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['pdf', 'doc', 'docx', 'xls', 'xlsx'],
    );
    if (result == null || result.files.single.path == null) return [];
    return [await _processDocument(result.files.single.path!, deleteSource: false)];
  }

  /// 외부 파일 경로로 직접 가져오기 (메신저 파일 등) — 원본 삭제 없음
  static Future<List<CapturedMedia>> importFromPaths(List<String> paths) async {
    final results = <CapturedMedia>[];
    for (final path in paths) {
      final ext = p.extension(path).toLowerCase();
      final isVideo = ['.mp4', '.mov', '.avi', '.mkv', '.webm'].contains(ext);
      final isDoc = ['.pdf', '.doc', '.docx', '.xls', '.xlsx'].contains(ext);
      if (isDoc) {
        results.add(await _processDocument(path, deleteSource: false));
      } else if (isVideo) {
        results.add(await _processVideo(path, deleteSource: false));
      } else {
        results.add(await _processPhoto(path, deleteSource: false));
      }
    }
    return results;
  }

  // ── 내부 처리 메서드 ──────────────────────────────────────

  static Future<CapturedMedia> _processPhoto(
    String sourcePath, {
    required bool deleteSource,
  }) async {
    // EXIF 먼저 읽기 (저장 전, 원본에서)
    final exif = await _readExif(sourcePath);

    final saved = await StorageService.savePhoto(sourcePath);
    if (deleteSource) {
      try { File(sourcePath).deleteSync(); } catch (_) {}
    }
    return CapturedMedia(
      filePath: saved.filePath,
      thumbPath: saved.thumbPath,
      mediaType: 'photo',
      fileSizeKb: StorageService.fileSizeKb(saved.filePath),
      latitude: exif.$1,
      longitude: exif.$2,
      takenAt: exif.$3,
    );
  }

  static Future<CapturedMedia> _processVideo(
    String sourcePath, {
    required bool deleteSource,
  }) async {
    final destPath = await StorageService.saveVideo(sourcePath);
    if (deleteSource) {
      try { File(sourcePath).deleteSync(); } catch (_) {}
    }

    String? thumbPath;
    try {
      final dir = await getApplicationDocumentsDirectory();
      final thumbDir = p.join(dir.path, 'memorix', 'videos');
      final thumbName = '${_uuid.v4()}_thumb.jpg';
      thumbPath = await VideoThumbnail.thumbnailFile(
        video: destPath,
        thumbnailPath: p.join(thumbDir, thumbName),
        imageFormat: ImageFormat.JPEG,
        maxWidth: 300,
        quality: 75,
      );
    } catch (_) {}

    return CapturedMedia(
      filePath: destPath,
      thumbPath: thumbPath,
      mediaType: 'video',
      fileSizeKb: StorageService.fileSizeKb(destPath),
    );
  }

  static Future<CapturedMedia> _processDocument(
    String sourcePath, {
    required bool deleteSource,
  }) async {
    final destPath = await StorageService.saveDocument(sourcePath);
    if (deleteSource) {
      try { File(sourcePath).deleteSync(); } catch (_) {}
    }
    return CapturedMedia(
      filePath: destPath,
      thumbPath: null,
      mediaType: 'document',
      fileSizeKb: StorageService.fileSizeKb(destPath),
    );
  }

  // ── EXIF 파싱 ──────────────────────────────────────────────

  /// (latitude, longitude, takenAt) — 읽기 실패 시 null
  static Future<(double?, double?, int?)> _readExif(String path) async {
    try {
      final bytes = await File(path).readAsBytes();
      final tags = await readExifFromBytes(bytes);

      final lat = _parseGps(
        tags['GPS GPSLatitude'],
        tags['GPS GPSLatitudeRef']?.printable,
      );
      final lng = _parseGps(
        tags['GPS GPSLongitude'],
        tags['GPS GPSLongitudeRef']?.printable,
      );

      int? takenAt;
      final dtTag = tags['Image DateTime'] ?? tags['EXIF DateTimeOriginal'];
      if (dtTag != null) {
        takenAt = _parseExifDateTime(dtTag.printable);
      }

      return (lat, lng, takenAt);
    } catch (_) {
      return (null, null, null);
    }
  }

  static double? _parseGps(IfdTag? tag, String? ref) {
    if (tag == null) return null;
    try {
      final vals = tag.values;
      if (vals is IfdRatios) {
        final ratios = vals.ratios;
        if (ratios.length < 3) return null;
        final d = ratios[0].numerator / ratios[0].denominator;
        final m = ratios[1].numerator / ratios[1].denominator;
        final s = ratios[2].numerator / ratios[2].denominator;
        double decimal = d + m / 60 + s / 3600;
        if (ref == 'S' || ref == 'W') decimal = -decimal;
        return decimal;
      }
    } catch (_) {}
    return null;
  }

  static int? _parseExifDateTime(String raw) {
    // "2024:03:15 14:30:00"
    try {
      final cleaned = raw.trim().replaceFirst(':', '-').replaceFirst(':', '-');
      return DateTime.parse(cleaned).millisecondsSinceEpoch;
    } catch (_) {
      return null;
    }
  }

}
