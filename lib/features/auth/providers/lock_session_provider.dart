import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/services/lock_toggle_service.dart';
import '../services/lock_auth_service.dart';
import '../services/lock_session_manager.dart';

/// per-item lock 세션 매니저 — 앱 단일 인스턴스.
final lockSessionProvider = Provider<LockSessionManager>(
  (ref) => LockSessionManager(),
);

/// per-item lock 인증 서비스.
final lockAuthServiceProvider = Provider<LockAuthService>((ref) {
  final session = ref.watch(lockSessionProvider);
  return LockAuthService(session: session);
});

/// per-item 잠금 토글 서비스 (평문 ↔ .enc).
final lockToggleServiceProvider = Provider<LockToggleService>(
  (ref) => LockToggleService(),
);
