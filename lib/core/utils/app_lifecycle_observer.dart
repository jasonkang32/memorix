import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../features/auth/providers/lock_provider.dart';
import '../../features/auth/providers/secret_lock_provider.dart';
import '../../core/services/auth_service.dart';
import '../../core/services/secret_vault_service.dart';

class AppLifecycleObserver extends WidgetsBindingObserver {
  final WidgetRef ref;
  DateTime? _backgroundAt;
  static const _lockTimeout = Duration(seconds: 30);

  AppLifecycleObserver(this.ref);

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.paused:
      case AppLifecycleState.hidden:
        _backgroundAt = DateTime.now();
        // 백그라운드 진입 즉시 평문 임시 파일 정리
        SecretVaultService.purgeTempDecrypted();
      case AppLifecycleState.resumed:
        _checkLock();
      default:
        break;
    }
  }

  Future<void> _checkLock() async {
    final bg = _backgroundAt;
    if (bg == null) return;

    final beenAway = DateTime.now().difference(bg);
    _backgroundAt = null;

    // Secret은 항상 재잠금. 30초 미만이라도 백그라운드 다녀오면 다시 인증.
    ref.read(secretLockProvider.notifier).lock();

    final hasPin = await AuthService.hasPin();
    final biometricEnabled = await AuthService.isBiometricEnabled();
    if (!hasPin && !biometricEnabled) return;

    if (beenAway >= _lockTimeout) {
      ref.read(lockProvider.notifier).lock();
    }
  }
}
