import 'dart:async';
import 'dart:developer' as developer;
import 'dart:io';
import 'package:exif/exif.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:file_picker/file_picker.dart';
import 'package:video_thumbnail/video_thumbnail.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';
import '../../shared/models/media_item.dart';
import 'secret_vault_service.dart';
import 'storage_service.dart';

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
  final bool encrypted;

  const CapturedMedia({
    required this.filePath,
    this.thumbPath,
    required this.mediaType,
    required this.fileSizeKb,
    this.durationSec = 0,
    this.latitude,
    this.longitude,
    this.takenAt,
    this.encrypted = false,
  });
}

class MediaCaptureService {
  static final _picker = ImagePicker();
  static const _androidPickerChannel = MethodChannel('memorix/picker');

  /// 카메라 촬영 (단일) — 임시 파일만 정리, 갤러리 원본은 유지
  static Future<List<CapturedMedia>> capturePhoto({
    MediaSpace space = MediaSpace.work,
  }) async {
    final xfile = await _picker.pickImage(
      source: ImageSource.camera,
      imageQuality: 95,
    );
    if (xfile == null) return [];
    // Android에서도 content:// URI가 올 수 있으므로 saveTo()로 복사
    final tmpDir = await getTemporaryDirectory();
    final tmpPath = p.join(tmpDir.path, '${_uuid.v4()}.jpg');
    await xfile.saveTo(tmpPath);
    final result = await _processPhoto(
      tmpPath,
      deleteSource: true,
      space: space,
    );
    return [result];
  }

  /// 갤러리에서 사진+영상 다중선택.
  ///
  /// Android는 네이티브 Photo Picker를 최우선 사용한다.
  /// 기기/플러그인 문제로 채널이 없거나 실패할 때만 Flutter picker로 fallback 한다.
  static Future<List<CapturedMedia>> pickGallery({
    MediaSpace space = MediaSpace.work,
  }) async {
    if (!kIsWeb && Platform.isAndroid) {
      final nativeResult = await _pickGalleryWithAndroidPicker(space: space);
      if (nativeResult != null) return nativeResult;
    }

    try {
      final files = await _picker
          .pickMultipleMedia(limit: 50, requestFullMetadata: false)
          .timeout(const Duration(minutes: 3));
      if (files.isEmpty) return [];
      return importFromXFiles(files, space: space);
    } catch (e, st) {
      developer.log(
        'pickGallery: image_picker 실패 — $e',
        error: e,
        stackTrace: st,
        name: 'memorix.capture',
      );
      return [];
    }
  }

  static Future<List<CapturedMedia>?> _pickGalleryWithAndroidPicker({
    required MediaSpace space,
  }) async {
    try {
      final paths = await _androidPickerChannel
          .invokeListMethod<String>('pickMedia')
          .timeout(const Duration(minutes: 3));
      if (paths == null || paths.isEmpty) return [];
      return importFromPaths(paths, deleteSource: true, space: space);
    } on MissingPluginException catch (e, st) {
      developer.log(
        'pickGallery: Android 네이티브 picker 미등록, fallback 사용 — $e',
        error: e,
        stackTrace: st,
        name: 'memorix.capture',
      );
      return null;
    } catch (e, st) {
      developer.log(
        'pickGallery: Android 네이티브 picker 실패, fallback 사용 — $e',
        error: e,
        stackTrace: st,
        name: 'memorix.capture',
      );
      return null;
    }
  }

  static Future<List<CapturedMedia>> importFromXFiles(
    List<XFile> files, {
    MediaSpace space = MediaSpace.work,
  }) async {
    final results = <CapturedMedia>[];
    final tmpDir = await getTemporaryDirectory();

    for (final file in files) {
      String? tmpPath;
      try {
        final ext = _extensionForXFile(file);
        tmpPath = p.join(tmpDir.path, '${_uuid.v4()}$ext');
        await file.saveTo(tmpPath).timeout(const Duration(seconds: 60));

        final item = _isVideoXFile(file)
            ? await _processVideo(
                tmpPath,
                deleteSource: true,
                space: space,
              ).timeout(const Duration(seconds: 60))
            : await _processPhoto(
                tmpPath,
                deleteSource: true,
                space: space,
              ).timeout(const Duration(seconds: 30));
        results.add(item);
      } catch (e, st) {
        developer.log(
          'importFromXFiles: 항목 처리 실패 — ${file.name}: $e',
          error: e,
          stackTrace: st,
          name: 'memorix.capture',
        );
        if (tmpPath != null) {
          try {
            File(tmpPath).deleteSync();
          } catch (_) {}
        }
      }
    }
    return results;
  }

  static const _videoExtensions = {
    '.mp4',
    '.mov',
    '.avi',
    '.mkv',
    '.webm',
    '.3gp',
    '.3g2',
    '.m4v',
    '.wmv',
    '.ts',
    '.flv',
    '.f4v',
  };

  static bool _isVideoXFile(XFile file) {
    final mimeType = file.mimeType?.toLowerCase();
    if (mimeType != null && mimeType.startsWith('video/')) return true;
    final ext = p.extension(file.name).toLowerCase().isNotEmpty
        ? p.extension(file.name).toLowerCase()
        : p.extension(file.path).toLowerCase();
    return _videoExtensions.contains(ext);
  }

  static String _extensionForXFile(XFile file) {
    final byName = p.extension(file.name).toLowerCase();
    if (byName.isNotEmpty) return byName;

    final byPath = p.extension(file.path).toLowerCase();
    if (byPath.isNotEmpty) return byPath;

    final mimeType = file.mimeType?.toLowerCase() ?? '';
    if (mimeType.contains('jpeg')) return '.jpg';
    if (mimeType.contains('png')) return '.png';
    if (mimeType.contains('webp')) return '.webp';
    if (mimeType.contains('gif')) return '.gif';
    if (mimeType.contains('heic')) return '.heic';
    if (mimeType.contains('heif')) return '.heif';
    if (mimeType.contains('bmp')) return '.bmp';
    if (mimeType.contains('mp4')) return '.mp4';
    if (mimeType.contains('quicktime')) return '.mov';
    if (mimeType.startsWith('video/')) return '.mp4';
    return '.jpg';
  }

  /// 문서 가져오기
  static Future<List<CapturedMedia>> pickDocument({
    MediaSpace space = MediaSpace.work,
  }) async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['pdf', 'doc', 'docx', 'xls', 'xlsx'],
    );
    if (result == null || result.files.single.path == null) return [];
    return [
      await _processDocument(
        result.files.single.path!,
        deleteSource: false,
        space: space,
      ),
    ];
  }

  /// 외부 파일 경로로 직접 가져오기 (메신저 파일 등).
  /// 기본값은 원본 유지이며, 임시 복사본 경로에는 deleteSource=true를 사용한다.
  /// 항목별 30~60초 타임아웃 적용. 실패 항목은 스킵, 성공 항목만 반환.
  static Future<List<CapturedMedia>> importFromPaths(
    List<String> paths, {
    bool deleteSource = false,
    MediaSpace space = MediaSpace.work,
  }) async {
    final results = <CapturedMedia>[];
    for (final path in paths) {
      try {
        final ext = p.extension(path).toLowerCase();
        final isDoc = ['.pdf', '.doc', '.docx', '.xls', '.xlsx'].contains(ext);
        final isVideo = _videoExtensions.contains(ext);
        final CapturedMedia item;
        if (isDoc) {
          item = await _processDocument(
            path,
            deleteSource: deleteSource,
            space: space,
          ).timeout(const Duration(seconds: 30));
        } else if (isVideo) {
          item = await _processVideo(
            path,
            deleteSource: deleteSource,
            space: space,
          ).timeout(const Duration(seconds: 60));
        } else {
          item = await _processPhoto(
            path,
            deleteSource: deleteSource,
            space: space,
          ).timeout(const Duration(seconds: 30));
        }
        results.add(item);
      } catch (e, st) {
        developer.log(
          'importFromPaths: 항목 처리 실패 — $path: $e',
          error: e,
          stackTrace: st,
          name: 'memorix.capture',
        );
      }
    }
    return results;
  }

  // ── 내부 처리 메서드 ──────────────────────────────────────

  static Future<CapturedMedia> _processPhoto(
    String sourcePath, {
    required bool deleteSource,
    required MediaSpace space,
  }) async {
    // EXIF 먼저 읽기 (저장 전, 원본에서)
    final exif = await _readExif(
      sourcePath,
    ).timeout(const Duration(seconds: 5), onTimeout: () => (null, null, null));

    if (space == MediaSpace.personal) {
      final saved = await SecretVaultService.savePhoto(
        sourcePath,
      ).timeout(const Duration(seconds: 25));
      if (deleteSource) {
        try {
          File(sourcePath).deleteSync();
        } catch (_) {}
      }
      return CapturedMedia(
        filePath: saved.filePath,
        thumbPath: saved.thumbPath,
        mediaType: 'photo',
        fileSizeKb: StorageService.fileSizeKb(saved.filePath),
        latitude: exif.$1,
        longitude: exif.$2,
        takenAt: exif.$3,
        encrypted: true,
      );
    }

    final saved = await StorageService.savePhoto(
      sourcePath,
    ).timeout(const Duration(seconds: 25));
    if (deleteSource) {
      try {
        File(sourcePath).deleteSync();
      } catch (_) {}
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
    required MediaSpace space,
  }) async {
    if (space == MediaSpace.personal) {
      // 1. 원본 영상을 암호화 보관함에 저장
      final destPath = await SecretVaultService.saveVideo(sourcePath);

      // 2. 평문 임시 썸네일 생성 → 암호화 후 평문 파일 삭제
      String? encryptedThumb;
      try {
        final tmpDir = await getTemporaryDirectory();
        final tmpThumbPath = p.join(tmpDir.path, '${_uuid.v4()}_thumb.jpg');
        final generated = await VideoThumbnail.thumbnailFile(
          video: sourcePath,
          thumbnailPath: tmpThumbPath,
          imageFormat: ImageFormat.JPEG,
          maxWidth: 300,
          quality: 75,
        ).timeout(const Duration(seconds: 15));
        if (generated != null) {
          encryptedThumb = await SecretVaultService.saveVideoThumb(generated);
        }
      } catch (_) {}

      if (deleteSource) {
        try {
          File(sourcePath).deleteSync();
        } catch (_) {}
      }
      return CapturedMedia(
        filePath: destPath,
        thumbPath: encryptedThumb,
        mediaType: 'video',
        fileSizeKb: StorageService.fileSizeKb(destPath),
        encrypted: true,
      );
    }

    final destPath = await StorageService.saveVideo(sourcePath);
    if (deleteSource) {
      try {
        File(sourcePath).deleteSync();
      } catch (_) {}
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
      ).timeout(const Duration(seconds: 15));
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
    required MediaSpace space,
  }) async {
    if (space == MediaSpace.personal) {
      final destPath = await SecretVaultService.saveDocument(sourcePath);
      if (deleteSource) {
        try {
          File(sourcePath).deleteSync();
        } catch (_) {}
      }
      return CapturedMedia(
        filePath: destPath,
        thumbPath: null,
        mediaType: 'document',
        fileSizeKb: StorageService.fileSizeKb(destPath),
        encrypted: true,
      );
    }

    final destPath = await StorageService.saveDocument(sourcePath);
    if (deleteSource) {
      try {
        File(sourcePath).deleteSync();
      } catch (_) {}
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
  /// EXIF는 파일 앞부분에만 있으므로 최대 512KB만 읽어 메모리 절약
  static Future<(double?, double?, int?)> _readExif(String path) async {
    try {
      final file = File(path);
      final fileSize = await file.length();
      final readSize = fileSize < 524288 ? fileSize : 524288; // 최대 512KB
      final raf = await file.open();
      final bytes = await raf.read(readSize);
      await raf.close();
      // compute()로 Isolate에서 실행 — 동기 연산이 UI 스레드를 블로킹하지 않도록 방지
      final tags = await compute(readExifFromBytes, bytes);

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
