import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/shared/widgets/media_thumbnail.dart';
import 'package:memorix/shared/models/media_item.dart';

void main() {
  group('MediaThumbnailCard', () {
    final photoItem = MediaItem(
      filePath: '/nonexistent/photo.jpg',
      thumbPath: '/nonexistent/thumb.jpg',
      mediaType: MediaType.photo,
      space: MediaSpace.work,
      takenAt: 1700000000000,
      createdAt: 1700000000000,
    );

    final videoItem = MediaItem(
      filePath: '/nonexistent/video.mp4',
      mediaType: MediaType.video,
      space: MediaSpace.personal,
      takenAt: 1700000000000,
      createdAt: 1700000000000,
    );

    final docItem = MediaItem(
      filePath: '/nonexistent/doc.pdf',
      mediaType: MediaType.document,
      space: MediaSpace.work,
      takenAt: 1700000000000,
      createdAt: 1700000000000,
    );

    testWidgets('renders without error for photo', (tester) async {
      await tester.pumpWidget(MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 100,
            height: 100,
            child: MediaThumbnailCard(item: photoItem, onTap: () {}),
          ),
        ),
      ));
      expect(find.byType(MediaThumbnailCard), findsOneWidget);
    });

    testWidgets('video item shows play icon overlay', (tester) async {
      await tester.pumpWidget(MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 100,
            height: 100,
            child: MediaThumbnailCard(item: videoItem, onTap: () {}),
          ),
        ),
      ));
      expect(find.byIcon(Icons.play_circle_outline), findsOneWidget);
    });

    testWidgets('document item shows description icon', (tester) async {
      await tester.pumpWidget(MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 100,
            height: 100,
            child: MediaThumbnailCard(item: docItem, onTap: () {}),
          ),
        ),
      ));
      expect(find.byIcon(Icons.description), findsOneWidget);
    });

    testWidgets('onTap callback fires on tap', (tester) async {
      bool tapped = false;
      await tester.pumpWidget(MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 100,
            height: 100,
            child: MediaThumbnailCard(
              item: photoItem,
              onTap: () => tapped = true,
            ),
          ),
        ),
      ));
      await tester.tap(find.byType(MediaThumbnailCard));
      expect(tapped, isTrue);
    });
  });
}
