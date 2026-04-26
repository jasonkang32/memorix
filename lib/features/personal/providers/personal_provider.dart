import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/db/media_dao.dart';
import '../../../core/db/album_dao.dart';
import '../../../shared/models/media_item.dart';
import '../../../shared/models/album.dart';

final _albumDao = AlbumDao();
final _mediaDao = MediaDao();

final albumListProvider = FutureProvider<List<Album>>((ref) async {
  return _albumDao.findAll();
});

/// Secret 영역 미디어 (구 Personal). 마이그레이션 후에도 legacy row 함께 조회됨.
final secretMediaProvider = FutureProvider<List<MediaItem>>((ref) async {
  return _mediaDao.findSecret();
});

/// 호환용 별칭.
@Deprecated('Use secretMediaProvider')
final personalMediaProvider = secretMediaProvider;
