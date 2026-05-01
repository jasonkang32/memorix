import '../../../core/services/media_capture_service.dart';
import '../../../core/services/media_save_service.dart';
import '../../../shared/models/media_item.dart';
import '../models/work_import_draft.dart';

typedef WorkImportSaveAll =
    Future<List<MediaSaveResult>> Function({
      required List<CapturedMedia> captured,
      required MediaSpace space,
      int? albumId,
      int? jobId,
      void Function(int done, int total)? onProgress,
      void Function()? onEnhancementComplete,
    });

class WorkImportController {
  final WorkImportSaveAll saveAll;

  WorkImportController({WorkImportSaveAll? saveAll})
    : saveAll = saveAll ?? MediaSaveService.saveAll;

  Future<List<MediaSaveResult>> saveDraft(
    WorkImportDraft draft, {
    void Function(int done, int total)? onProgress,
    void Function()? onEnhancementComplete,
  }) {
    return saveAll(
      captured: draft.items
          .map((item) => item.toCapturedMedia())
          .toList(growable: false),
      space: MediaSpace.work,
      onProgress: onProgress,
      onEnhancementComplete: onEnhancementComplete,
    );
  }
}
