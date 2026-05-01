import '../../../core/services/media_capture_service.dart';

class WorkImportDraftItem {
  final String filePath;
  final String? thumbPath;
  final String mediaType;
  final int fileSizeKb;
  final int durationSec;
  final double? latitude;
  final double? longitude;
  final int? takenAt;

  const WorkImportDraftItem({
    required this.filePath,
    this.thumbPath,
    required this.mediaType,
    required this.fileSizeKb,
    this.durationSec = 0,
    this.latitude,
    this.longitude,
    this.takenAt,
  });

  factory WorkImportDraftItem.fromCaptured(CapturedMedia captured) {
    return WorkImportDraftItem(
      filePath: captured.filePath,
      thumbPath: captured.thumbPath,
      mediaType: captured.mediaType,
      fileSizeKb: captured.fileSizeKb,
      durationSec: captured.durationSec,
      latitude: captured.latitude,
      longitude: captured.longitude,
      takenAt: captured.takenAt,
    );
  }

  CapturedMedia toCapturedMedia() {
    return CapturedMedia(
      filePath: filePath,
      thumbPath: thumbPath,
      mediaType: mediaType,
      fileSizeKb: fileSizeKb,
      durationSec: durationSec,
      latitude: latitude,
      longitude: longitude,
      takenAt: takenAt,
    );
  }
}

class WorkImportDraft {
  final List<WorkImportDraftItem> items;

  const WorkImportDraft({required this.items});

  factory WorkImportDraft.fromCaptured(List<CapturedMedia> capturedList) {
    return WorkImportDraft(
      items: capturedList
          .map(WorkImportDraftItem.fromCaptured)
          .toList(growable: false),
    );
  }
}
