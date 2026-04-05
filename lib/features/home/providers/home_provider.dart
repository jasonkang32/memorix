import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/db/media_dao.dart';
import '../../../core/db/album_dao.dart';
import '../../../core/db/people_dao.dart';
import '../../../core/services/storage_service.dart';
import '../../../shared/models/media_item.dart';

// ── 데이터 모델 ─────────────────────────────────────────────

class HomeSummary {
  final int workCount;
  final int personalCount;
  final Map<String, int> workByType;   // photo/video/document
  final Map<String, int> personalByType;
  final int albumCount;
  final int peopleCount;
  final int countryCount;
  final int totalSizeKb;
  final int pendingSync;
  final List<MediaItem> recentItems;
  final Map<String, int> activityByDay; // yyyy-MM-dd → count
  final List<Map<String, dynamic>> topTags;
  final StorageBreakdown storageBreakdown;

  const HomeSummary({
    required this.workCount,
    required this.personalCount,
    required this.workByType,
    required this.personalByType,
    required this.albumCount,
    required this.peopleCount,
    required this.countryCount,
    required this.totalSizeKb,
    required this.pendingSync,
    required this.recentItems,
    required this.activityByDay,
    required this.topTags,
    required this.storageBreakdown,
  });

  int get totalCount => workCount + personalCount;

  String get totalSizeLabel {
    if (totalSizeKb >= 1024 * 1024) {
      return '${(totalSizeKb / (1024 * 1024)).toStringAsFixed(1)} GB';
    }
    if (totalSizeKb >= 1024) {
      return '${(totalSizeKb / 1024).toStringAsFixed(1)} MB';
    }
    return '$totalSizeKb KB';
  }
}

// ── Providers ───────────────────────────────────────────────

final homeSummaryProvider = FutureProvider<HomeSummary>((ref) async {
  final dao = MediaDao();
  final albumDao = AlbumDao();
  final peopleDao = PeopleDao();

  final results = await Future.wait([
    dao.countGroupsBySpace('work'),
    dao.countGroupsBySpace('personal'),
    dao.countByTypeForSpace('work'),
    dao.countByTypeForSpace('personal'),
    albumDao.findAll(),
    peopleDao.findAll(),
    dao.countDistinctCountries(),
    dao.sumFileSizeKb(),
    dao.findPendingSync(),
    dao.findRecent(limit: 10),
    dao.activityByDay(days: 30),
    dao.topTags(limit: 6),
    StorageService.calcBreakdown(),
  ]);

  return HomeSummary(
    workCount: results[0] as int,
    personalCount: results[1] as int,
    workByType: results[2] as Map<String, int>,
    personalByType: results[3] as Map<String, int>,
    albumCount: (results[4] as List).length,
    peopleCount: (results[5] as List).length,
    countryCount: results[6] as int,
    totalSizeKb: results[7] as int,
    pendingSync: (results[8] as List).length,
    recentItems: results[9] as List<MediaItem>,
    activityByDay: results[10] as Map<String, int>,
    topTags: results[11] as List<Map<String, dynamic>>,
    storageBreakdown: results[12] as StorageBreakdown,
  );
});
