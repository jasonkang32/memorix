import 'dart:io';
import 'dart:ui';
import 'package:flutter/material.dart';
import '../models/media_item.dart';
import 'encrypted_image.dart';

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
            _buildThumbnail(context),
            if (item.mediaType == MediaType.video)
              const Center(
                child: Icon(
                  Icons.play_circle_outline,
                  color: Colors.white,
                  size: 32,
                ),
              ),
            if (item.mediaType == MediaType.document)
              Container(
                color: Theme.of(context).colorScheme.surfaceContainerHighest,
                child: Center(
                  child: Icon(
                    Icons.description,
                    color: Theme.of(
                      context,
                    ).colorScheme.onSurface.withValues(alpha: 0.6),
                    size: 32,
                  ),
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
            // 잠긴 항목은 그리드에서 블러 + 자물쇠 오버레이로 가려진다.
            // 인증 후 풀스크린에서만 선명하게 보여야 하므로, 다른 모든
            // 오버레이(타입 아이콘·동기화 배지) 위에 마지막으로 덮는다.
            if (item.isLocked == 1) ...[
              ClipRect(
                child: BackdropFilter(
                  filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
                  child: Container(
                    color: Colors.black.withValues(alpha: 0.25),
                  ),
                ),
              ),
              const Center(
                child: Icon(
                  Icons.lock_rounded,
                  size: 36,
                  color: Colors.white,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  // 그리드 셀(작은 이미지)에서 디코딩 캐시 폭. 셀 너비의 ~2배(retina) 가정.
  // 너무 작으면 흐리고, 너무 크면 메모리 낭비.
  static const int _gridDecodeSize = 600;

  Widget _buildThumbnail(BuildContext context) {
    final thumb = item.thumbPath;
    if (item.isEncrypted) {
      // Secret 보관함: thumbPath 우선, 없으면 원본
      final encPath = (thumb != null && thumb.isNotEmpty)
          ? thumb
          : item.filePath;
      if (item.mediaType == MediaType.document) {
        return Container(
          color: Theme.of(context).colorScheme.surfaceContainerHighest,
          child: Center(
            child: Icon(
              Icons.description,
              color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.6),
              size: 32,
            ),
          ),
        );
      }
      return EncryptedImage(encryptedPath: encPath);
    }
    // 사진: 원본 filePath + cacheWidth로 디코딩 다운샘플 (Bug #1 회귀 방지).
    // 압축된 thumbPath를 stretch하면 그리드에서도 화질 저하가 발생한다.
    if (item.mediaType == MediaType.photo) {
      final file = item.filePath;
      if (File(file).existsSync()) {
        return Image.file(
          File(file),
          fit: BoxFit.cover,
          cacheWidth: _gridDecodeSize,
          cacheHeight: _gridDecodeSize,
          filterQuality: FilterQuality.medium,
        );
      }
      // 원본이 없을 때만 thumbPath fallback.
      if (thumb != null && File(thumb).existsSync()) {
        return Image.file(
          File(thumb),
          fit: BoxFit.cover,
          cacheWidth: _gridDecodeSize,
          cacheHeight: _gridDecodeSize,
          filterQuality: FilterQuality.medium,
        );
      }
    } else {
      // 비디오: mp4는 Image.file로 못 띄움 → thumbPath 사용.
      if (thumb != null && File(thumb).existsSync()) {
        return Image.file(
          File(thumb),
          fit: BoxFit.cover,
          cacheWidth: _gridDecodeSize,
          cacheHeight: _gridDecodeSize,
          filterQuality: FilterQuality.medium,
        );
      }
    }
    return Container(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
    );
  }
}
