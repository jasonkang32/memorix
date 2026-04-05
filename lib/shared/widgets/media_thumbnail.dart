import 'dart:io';
import 'package:flutter/material.dart';
import '../models/media_item.dart';

class MediaThumbnailCard extends StatelessWidget {
  final MediaItem item;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  const MediaThumbnailCard({
    super.key,
    required this.item,
    this.onTap,
    this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      onLongPress: onLongPress,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(2),
        child: Stack(
          fit: StackFit.expand,
          children: [
            _buildThumbnail(),
            if (item.mediaType == MediaType.video)
              const Center(
                child: Icon(Icons.play_circle_outline,
                    color: Colors.white, size: 32),
              ),
            if (item.mediaType == MediaType.document)
              Container(
                color: Colors.grey[200],
                child: const Center(
                  child: Icon(Icons.description, color: Colors.grey, size: 32),
                ),
              ),
            if (item.driveSynced == 0)
              Positioned(
                bottom: 4,
                right: 4,
                child: Container(
                  padding: const EdgeInsets.all(2),
                  decoration: BoxDecoration(
                    color: Colors.black54,
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: const Icon(
                    Icons.cloud_upload_outlined,
                    color: Colors.white,
                    size: 14,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildThumbnail() {
    final thumb = item.thumbPath;
    if (thumb != null && File(thumb).existsSync()) {
      return Image.file(File(thumb), fit: BoxFit.cover);
    }
    final file = item.filePath;
    if (item.mediaType == MediaType.photo && File(file).existsSync()) {
      return Image.file(File(file), fit: BoxFit.cover);
    }
    return Container(color: Colors.grey[200]);
  }
}
