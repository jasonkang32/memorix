import 'dart:async';
import 'package:connectivity_plus/connectivity_plus.dart';

class ConnectivityService {
  static final Connectivity _connectivity = Connectivity();

  /// 네트워크 연결 복구 시 [onConnected] 콜백 호출
  /// 반환된 StreamSubscription은 사용 후 cancel() 필요
  static StreamSubscription<List<ConnectivityResult>> listen(
    Future<void> Function() onConnected,
  ) {
    bool wasOffline = false;

    return _connectivity.onConnectivityChanged.listen((results) {
      final isOnline = results.any((r) => r != ConnectivityResult.none);
      if (isOnline && wasOffline) {
        onConnected();
      }
      wasOffline = !isOnline;
    });
  }

  static Future<bool> isOnline() async {
    final results = await _connectivity.checkConnectivity();
    return results.any((r) => r != ConnectivityResult.none);
  }
}
