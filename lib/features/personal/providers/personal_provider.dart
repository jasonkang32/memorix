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

final personalMediaProvider = FutureProvider<List<MediaItem>>((ref) async {
  return _mediaDao.findPersonal();
});
