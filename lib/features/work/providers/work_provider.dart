import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/db/media_dao.dart';
import '../../../shared/models/media_item.dart';

final mediaDao = MediaDao();

// Work 미디어 목록 (국가·지역 필터)
class WorkFilter {
  final String? countryCode;
  final String? region;
  final String? mediaType;
  const WorkFilter({this.countryCode, this.region, this.mediaType});
}

final workFilterProvider = StateProvider<WorkFilter>(
  (ref) => const WorkFilter(),
);

final workMediaProvider = FutureProvider<List<MediaItem>>((ref) async {
  final filter = ref.watch(workFilterProvider);
  return mediaDao.findWork(
    countryCode: filter.countryCode,
    region: filter.region,
    mediaType: filter.mediaType,
  );
});
