import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../features/auth/providers/lock_provider.dart';
import '../../core/services/auth_service.dart';

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
      case AppLifecycleState.resumed:
        _checkLock();
      default:
        break;
    }
  }

  Future<void> _checkLock() async {
    final hasPin = await AuthService.hasPin();
    final biometricEnabled = await AuthService.isBiometricEnabled();
    if (!hasPin && !biometricEnabled) return;

    final bg = _backgroundAt;
    if (bg == null) return;

    if (DateTime.now().difference(bg) >= _lockTimeout) {
      ref.read(lockProvider.notifier).lock();
    }
    _backgroundAt = null;
  }
}
