import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/core/services/media_save_service.dart';
import 'package:memorix/features/work/controllers/work_import_controller.dart';
import 'package:memorix/features/work/models/work_import_draft.dart';
import 'package:memorix/features/work/screens/work_import_screen.dart';
import 'package:memorix/shared/models/media_item.dart';
import 'package:memorix/shared/models/tag.dart';

void main() {
  testWidgets('WorkImportScreen shows selected draft items and save action', (
    tester,
  ) async {
    const draft = WorkImportDraft(
      items: [
        WorkImportDraftItem(
          filePath: '/tmp/photo-a.jpg',
          mediaType: 'photo',
          fileSizeKb: 120,
        ),
        WorkImportDraftItem(
          filePath: '/tmp/video-b.mp4',
          mediaType: 'video',
          fileSizeKb: 2048,
          durationSec: 18,
        ),
      ],
    );

    await tester.pumpWidget(
      const MaterialApp(
        home: WorkImportScreen(draft: draft),
      ),
    );

    expect(find.text('선택한 미디어 2개'), findsOneWidget);
    expect(find.text('/tmp/photo-a.jpg'), findsOneWidget);
    expect(find.text('/tmp/video-b.mp4'), findsOneWidget);
    expect(find.text('저장'), findsOneWidget);
  });

  testWidgets('WorkImportScreen save button forwards the current draft', (
    tester,
  ) async {
    const draft = WorkImportDraft(
      items: [
        WorkImportDraftItem(
          filePath: '/tmp/photo-a.jpg',
          mediaType: 'photo',
          fileSizeKb: 120,
        ),
      ],
    );

    WorkImportDraft? savedDraft;

    await tester.pumpWidget(
      MaterialApp(
        home: WorkImportScreen(
          draft: draft,
          onSave: (nextDraft) async {
            savedDraft = nextDraft;
          },
        ),
      ),
    );

    await tester.tap(find.text('저장'));
    await tester.pump();

    expect(savedDraft, isNotNull);
    expect(savedDraft!.items, hasLength(1));
    expect(savedDraft!.items.single.filePath, '/tmp/photo-a.jpg');
  });

  testWidgets('WorkImportScreen uses controller and returns saved results', (
    tester,
  ) async {
    const draft = WorkImportDraft(
      items: [
        WorkImportDraftItem(
          filePath: '/tmp/photo-a.jpg',
          mediaType: 'photo',
          fileSizeKb: 120,
        ),
      ],
    );

    bool savedCalled = false;

    final controller = WorkImportController(
      saveAll: ({
        required captured,
        required space,
        albumId,
        jobId,
        onProgress,
        onEnhancementComplete,
      }) async {
        return [
          MediaSaveResult(
            item: MediaItem(
              id: 9,
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

    await tester.pumpWidget(
      MaterialApp(
        home: WorkImportScreen(
          draft: draft,
          controller: controller,
          onSaved: (results) {
            savedCalled = true;
            expect(results, hasLength(1));
            expect(results.single.item.id, 9);
          },
        ),
      ),
    );

    await tester.tap(find.text('저장'));
    await tester.pumpAndSettle();

    expect(savedCalled, isTrue);
  });
}
