import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../db/media_dao.dart';

/// Drive 미동기화 미디어 건수
final pendingSyncCountProvider = FutureProvider<int>((ref) async {
  final dao = MediaDao();
  final items = await dao.findPendingSync();
  return items.length;
});
