import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../../core/services/secret_vault_service.dart';

/// Secret 보관함의 `.enc` 파일을 임시로 복호화해 화면에 그려준다.
///
/// 작은 썸네일 용도. 화면이 사라지면 메모리도 함께 해제됨.
/// LRU 캐시는 두지 않는다. — 대규모 그리드를 매끄럽게 굴리려면 후속 작업 필요.
class EncryptedImage extends StatefulWidget {
  final String encryptedPath;
  final BoxFit fit;
  final Widget Function(BuildContext context)? placeholderBuilder;
  final Widget Function(BuildContext context, Object error)? errorBuilder;

  const EncryptedImage({
    super.key,
    required this.encryptedPath,
    this.fit = BoxFit.cover,
    this.placeholderBuilder,
    this.errorBuilder,
  });

  @override
  State<EncryptedImage> createState() => _EncryptedImageState();
}

class _EncryptedImageState extends State<EncryptedImage> {
  Uint8List? _bytes;
  Object? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(EncryptedImage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.encryptedPath != widget.encryptedPath) {
      _bytes = null;
      _error = null;
      _load();
    }
  }

  Future<void> _load() async {
    try {
      final f = File(widget.encryptedPath);
      if (!await f.exists()) {
        if (mounted) setState(() => _error = 'missing file');
        return;
      }
      final bytes = await SecretVaultService.decryptToBytes(
        widget.encryptedPath,
      );
      if (!mounted) return;
      setState(() => _bytes = bytes);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_bytes != null) {
      return Image.memory(_bytes!, fit: widget.fit, gaplessPlayback: true);
    }
    if (_error != null) {
      return widget.errorBuilder?.call(context, _error!) ??
          Container(
            color: Theme.of(context).dividerColor,
            child: Center(
              child: Icon(
                Icons.lock_outline,
                color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.6),
                size: 28,
              ),
            ),
          );
    }
    return widget.placeholderBuilder?.call(context) ??
        Container(
          color: Theme.of(context).colorScheme.surfaceContainerHighest,
        );
  }
}
