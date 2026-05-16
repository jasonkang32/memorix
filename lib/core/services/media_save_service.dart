import 'dart:async';
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

  /// Phase 1: 지오코딩 + DB 삽입만 수행 (빠름, ~1초)
  static Future<MediaSaveResult> _fastSave({
    required CapturedMedia captured,
    required MediaSpace space,
    String note = '',
    String countryCode = '',
    String region = '',
    int? albumId,
    int? jobId,
    String batchId = '',
  }) async {
    String finalCountry = countryCode;
    String finalRegion = region;
    final double? lat = captured.latitude;
    final double? lng = captured.longitude;

    if (lat != null && lng != null && finalCountry.isEmpty) {
      final geo = await _reverseGeocode(lat, lng);
      finalCountry = geo.$1;
      finalRegion = geo.$2;
    }

    final takenAt = captured.takenAt ?? DateTime.now().millisecondsSinceEpoch;
    final now = DateTime.now().millisecondsSinceEpoch;

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
      ocrText: '',
      encrypted: captured.encrypted ? 1 : 0,
    );

    final id = await _mediaDao.insert(item);
    return MediaSaveResult(
      item: item.copyWith(id: id),
      suggestedTags: const [],
    );
  }

  /// Phase 2: OCR + AI 태그.
  ///
  /// saveAll이 phase 2를 순차 루프로 돌리므로 다중 폭발 위험은 완화되어 있다.
  /// 추가로 OCR과 태그 사이에 한 번 yield해서 GC 기회를 준다.
  static Future<void> _enhance(
    int mediaId,
    CapturedMedia captured,
    MediaSpace space,
  ) async {
    if (captured.mediaType == 'photo' || captured.mediaType == 'document') {
      // OcrService 내부 timeout이 30초이므로 호출부는 35초로 마진 확보.
      // 이전엔 외부 15초가 OCR 30초보다 짧아 OCR 자체 timeout이 의미 없었다.
      final ocrText = await OcrService.extractText(captured.filePath)
          .timeout(const Duration(seconds: 35), onTimeout: () => '');
      if (ocrText.isNotEmpty) {
        await _mediaDao.updateOcrText(mediaId, ocrText);
      }
    }
    // 다음 microtask로 양보 — ML Kit 리소스 해제 기회.
    await Future<void>.delayed(Duration.zero);
    await _suggestAndApplyTags(captured, space, mediaId);
  }

  /// 다중 저장 (배치)
  /// Phase 1(지오코딩+DB)은 블로킹 — 다이얼로그가 이 시간만 표시
  /// Phase 2(OCR+AI태그)는 완전히 백그라운드 — Future.delayed(zero)로 다음 이벤트 루프 시작
  static Future<List<MediaSaveResult>> saveAll({
    required List<CapturedMedia> captured,
    required MediaSpace space,
    int? albumId,
    int? jobId,
    void Function(int done, int total)? onProgress,
    void Function()? onEnhancementComplete,
    String? overrideBatchId,
  }) async {
    // overrideBatchId가 있으면 인계 (detail 안에서 "기존 work에 추가" 흐름).
    // 없으면 다중 항목일 때만 새 batch uuid 생성, 단일은 빈 문자열.
    final batchId =
        overrideBatchId ?? (captured.length > 1 ? _uuid.v4() : '');
    final results = <MediaSaveResult>[];

    // (mediaId, capturedMedia) 쌍 — Phase 2에서 사용
    final phase2Items = <(int, CapturedMedia)>[];

    for (int i = 0; i < captured.length; i++) {
      try {
        final result = await _fastSave(
          captured: captured[i],
          space: space,
          albumId: albumId,
          jobId: jobId,
          batchId: batchId,
        );
        results.add(result);
        phase2Items.add((result.item.id!, captured[i]));
      } catch (e, stack) {
        developer.log(
          'MediaSaveService: Phase 1 저장 실패: $e',
          error: e,
          stackTrace: stack,
          name: 'memorix',
        );
      }
      onProgress?.call(i + 1, captured.length);
    }

    // Phase 2: Future.delayed(zero)로 완전히 다음 이벤트 루프에서 시작
    // → 이 함수가 return된 후 다이얼로그 dismiss + 화면 전환이 먼저 완료됨
    if (phase2Items.isNotEmpty) {
      unawaited(
        Future.delayed(Duration.zero)
            .then((_) async {
              for (final (id, cap) in phase2Items) {
                try {
                  // _enhance 내부: OCR 35초 + AI 태그 ~5초. 마진 포함 50초.
                  // 이전 20초는 OCR 30초도 못 끝냈다.
                  await _enhance(
                    id,
                    cap,
                    space,
                  ).timeout(const Duration(seconds: 50));
                } catch (e, st) {
                  developer.log(
                    'MediaSaveService: Phase 2 실패 id=$id: $e',
                    error: e,
                    stackTrace: st,
                    name: 'memorix',
                  );
                }
              }
              onEnhancementComplete?.call();
            })
            .catchError((Object _) {}),
      );
    }

    return results;
  }

  // ── 내부 헬퍼 ──────────────────────────────────────────────

  static Future<void> _suggestAndApplyTags(
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
      suggestedKeys = await AiTagService.suggestTags(
        captured.thumbPath!,
        space,
      );
    }

    if (suggestedKeys.isEmpty) return;
    final allTags = await _tagDao.findBySpace(space);
    final matched = allTags
        .where((t) => suggestedKeys.contains(t.key))
        .toList();
    if (matched.isNotEmpty) {
      await _tagDao.setMediaTags(mediaId, matched.map((t) => t.id!).toList());
    }
  }

  /// 역지오코딩 — (countryCode, region)
  static Future<(String, String)> _reverseGeocode(
    double lat,
    double lng,
  ) async {
    try {
      final placemarks = await placemarkFromCoordinates(
        lat,
        lng,
      ).timeout(const Duration(seconds: 5));
      if (placemarks.isNotEmpty) {
        final p = placemarks.first;
        return (
          p.isoCountryCode ?? '',
          p.administrativeArea ?? p.locality ?? '',
        );
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
