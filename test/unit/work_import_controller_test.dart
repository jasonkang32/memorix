import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/core/services/media_capture_service.dart';
import 'package:memorix/core/services/media_save_service.dart';
import 'package:memorix/features/work/controllers/work_import_controller.dart';
import 'package:memorix/features/work/models/work_import_draft.dart';
import 'package:memorix/shared/models/media_item.dart';
import 'package:memorix/shared/models/tag.dart';

void main() {
  test('WorkImportController.saveDraft converts draft items and uses work space', () async {
    const draft = WorkImportDraft(
      items: [
        WorkImportDraftItem(
          filePath: '/tmp/photo-a.jpg',
          thumbPath: '/tmp/photo-a-thumb.jpg',
          mediaType: 'photo',
          fileSizeKb: 120,
          latitude: 37.5,
          longitude: 127.0,
          takenAt: 1700000000000,
        ),
      ],
    );

    List<CapturedMedia>? savedCaptured;
    MediaSpace? savedSpace;

    final controller = WorkImportController(
      saveAll: ({
        required List<CapturedMedia> captured,
        required MediaSpace space,
        int? albumId,
        int? jobId,
        void Function(int done, int total)? onProgress,
        void Function()? onEnhancementComplete,
      }) async {
        savedCaptured = captured;
        savedSpace = space;
        return [
          MediaSaveResult(
            item: MediaItem(
              id: 1,
              space: MediaSpace.work,
              mediaType: MediaType.photo,
              filePath: '/saved/photo-a.jpg',
              thumbPath: '/saved/photo-a-thumb.jpg',
              takenAt: 1700000000000,
              createdAt: 1700000000100,
            ),
            suggestedTags: const <Tag>[],
          ),
        ];
      },
    );

    final results = await controller.saveDraft(draft);

    expect(savedSpace, MediaSpace.work);
    expect(savedCaptured, isNotNull);
    expect(savedCaptured, hasLength(1));
    expect(savedCaptured!.single.filePath, '/tmp/photo-a.jpg');
    expect(savedCaptured!.single.thumbPath, '/tmp/photo-a-thumb.jpg');
    expect(savedCaptured!.single.mediaType, 'photo');
    expect(savedCaptured!.single.latitude, 37.5);
    expect(savedCaptured!.single.longitude, 127.0);
    expect(savedCaptured!.single.takenAt, 1700000000000);
    expect(results, hasLength(1));
    expect(results.single.item.id, 1);
  });
}
