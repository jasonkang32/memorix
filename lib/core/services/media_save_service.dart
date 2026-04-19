import 'dart:developer' as developer;

import 'package:geocoding/geocoding.dart';
import 'package:uuid/uuid.dart';
import '../../shared/models/media_item.dart';
import '../../shared/models/tag.dart';
import '../db/media_dao.dart';
import '../db/tag_dao.dart';
import 'ai_tag_service.dart';
import 'media_capture_service.dart';
import 'ocr_service.dart';

const _uuid = Uuid();

class MediaSaveResult {
  final MediaItem item;
  final List<Tag> suggestedTags;
  const MediaSaveResult({required this.item, required this.suggestedTags});
}

class MediaSaveService {
  static final _mediaDao = MediaDao();
  static final _tagDao = TagDao();

  /// 단일 저장
  static Future<MediaSaveResult> save({
    required CapturedMedia captured,
    required MediaSpace space,
    String note = '',
    String countryCode = '',
    String region = '',
    int? albumId,
    int? jobId,
    String batchId = '',
  }) async {
    // EXIF 위치 우선, 없으면 파라미터 사용
    String finalCountry = countryCode;
    String finalRegion = region;
    double? lat = captured.latitude;
    double? lng = captured.longitude;

    if (lat != null && lng != null && finalCountry.isEmpty) {
      final geo = await _reverseGeocode(lat, lng);
      finalCountry = geo.$1;
      finalRegion = geo.$2;
    }

    final takenAt = captured.takenAt ?? DateTime.now().millisecondsSinceEpoch;
    final now = DateTime.now().millisecondsSinceEpoch;

    // 사진·문서만 OCR 실행 (영상 제외)
    String ocrText = '';
    if (captured.mediaType == 'photo' || captured.mediaType == 'document') {
      ocrText = await OcrService.extractText(captured.filePath);
    }

    final item = MediaItem(
      space: space,
      mediaType: _parseType(captured.mediaType),
      filePath: captured.filePath,
      thumbPath: captured.thumbPath,
      title: '',
      note: note,
      countryCode: finalCountry,
      region: finalRegion,
      albumId: albumId,
      jobId: jobId,
      latitude: lat,
      longitude: lng,
      takenAt: takenAt,
      createdAt: now,
      fileSizeKb: captured.fileSizeKb,
      durationSec: captured.durationSec,
      batchId: batchId,
      ocrText: ocrText,
    );

    final id = await _mediaDao.insert(item);
    final saved = item.copyWith(id: id);

    // AI 태그 추천
    final suggestedTags = await _suggestAndApplyTags(captured, space, id);

    return MediaSaveResult(item: saved, suggestedTags: suggestedTags);
  }

  /// 다중 저장 (배치) — 같은 배치는 동일한 batchId 공유
  static Future<List<MediaSaveResult>> saveAll({
    required List<CapturedMedia> captured,
    required MediaSpace space,
    int? albumId,
    int? jobId,
    void Function(int done, int total)? onProgress,
  }) async {
    final batchId = captured.length > 1 ? _uuid.v4() : '';
    final results = <MediaSaveResult>[];
    for (int i = 0; i < captured.length; i++) {
      try {
        results.add(await save(
            captured: captured[i], space: space, albumId: albumId, jobId: jobId, batchId: batchId));
        onProgress?.call(i + 1, captured.length);
      } catch (e, stack) {
        developer.log('MediaSaveService: 항목 저장 실패: $e', error: e, stackTrace: stack, name: 'memorix');
        onProgress?.call(i + 1, captured.length);
      }
    }
    return results;
  }

  // ── 내부 헬퍼 ──────────────────────────────────────────────

  static Future<List<Tag>> _suggestAndApplyTags(
    CapturedMedia captured,
    MediaSpace space,
    int mediaId,
  ) async {
    List<String> suggestedKeys = [];
    if (captured.mediaType == 'document') {
      suggestedKeys = AiTagService.suggestForDocument();
    } else if (captured.mediaType == 'photo') {
      suggestedKeys = await AiTagService.suggestTags(captured.filePath, space);
    } else if (captured.mediaType == 'video' && captured.thumbPath != null) {
      suggestedKeys = await AiTagService.suggestTags(captured.thumbPath!, space);
    }

    final allTags = await _tagDao.findBySpace(space);
    final matched = allTags.where((t) => suggestedKeys.contains(t.key)).toList();
    if (matched.isNotEmpty) {
      await _tagDao.setMediaTags(mediaId, matched.map((t) => t.id!).toList());
    }
    return matched;
  }

  /// 역지오코딩 — (countryCode, region)
  static Future<(String, String)> _reverseGeocode(double lat, double lng) async {
    try {
      final placemarks = await placemarkFromCoordinates(lat, lng);
      if (placemarks.isNotEmpty) {
        final p = placemarks.first;
        final country = p.isoCountryCode ?? '';
        final region = p.administrativeArea ?? p.locality ?? '';
        return (country, region);
      }
    } catch (_) {}
    return ('', '');
  }

  static MediaType _parseType(String s) => switch (s) {
        'video' => MediaType.video,
        'document' => MediaType.document,
        _ => MediaType.photo,
      };
}
