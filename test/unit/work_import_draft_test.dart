import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/core/services/media_capture_service.dart';
import 'package:memorix/features/work/models/work_import_draft.dart';

void main() {
  test('WorkImportDraft.fromCaptured preserves captured media order and metadata', () {
    const captured = [
      CapturedMedia(
        filePath: '/tmp/photo-a.jpg',
        thumbPath: '/tmp/photo-a-thumb.jpg',
        mediaType: 'photo',
        fileSizeKb: 120,
        latitude: 37.5,
        longitude: 127.0,
        takenAt: 1700000000000,
      ),
      CapturedMedia(
        filePath: '/tmp/video-b.mp4',
        thumbPath: '/tmp/video-b-thumb.jpg',
        mediaType: 'video',
        fileSizeKb: 2048,
        durationSec: 18,
      ),
    ];

    final draft = WorkImportDraft.fromCaptured(captured);

    expect(draft.items, hasLength(2));
    expect(draft.items.first.filePath, '/tmp/photo-a.jpg');
    expect(draft.items.first.thumbPath, '/tmp/photo-a-thumb.jpg');
    expect(draft.items.first.mediaType, 'photo');
    expect(draft.items.first.fileSizeKb, 120);
    expect(draft.items.first.durationSec, 0);
    expect(draft.items.first.latitude, 37.5);
    expect(draft.items.first.longitude, 127.0);
    expect(draft.items.first.takenAt, 1700000000000);

    expect(draft.items.last.filePath, '/tmp/video-b.mp4');
    expect(draft.items.last.thumbPath, '/tmp/video-b-thumb.jpg');
    expect(draft.items.last.mediaType, 'video');
    expect(draft.items.last.fileSizeKb, 2048);
    expect(draft.items.last.durationSec, 18);
    expect(draft.items.last.latitude, isNull);
    expect(draft.items.last.longitude, isNull);
    expect(draft.items.last.takenAt, isNull);
  });

  test('WorkImportDraftItem can convert back to captured media for saving', () {
    const item = WorkImportDraftItem(
      filePath: '/tmp/photo-a.jpg',
      thumbPath: '/tmp/photo-a-thumb.jpg',
      mediaType: 'photo',
      fileSizeKb: 120,
      latitude: 37.5,
      longitude: 127.0,
      takenAt: 1700000000000,
    );

    final captured = item.toCapturedMedia();

    expect(captured.filePath, '/tmp/photo-a.jpg');
    expect(captured.thumbPath, '/tmp/photo-a-thumb.jpg');
    expect(captured.mediaType, 'photo');
    expect(captured.fileSizeKb, 120);
    expect(captured.durationSec, 0);
    expect(captured.latitude, 37.5);
    expect(captured.longitude, 127.0);
    expect(captured.takenAt, 1700000000000);
  });
}
