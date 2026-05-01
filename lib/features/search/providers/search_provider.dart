import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/db/media_dao.dart';
import '../../../core/db/album_dao.dart';
import '../../../core/db/people_dao.dart';
import '../../../core/db/tag_dao.dart';
import '../../../shared/models/album.dart';
import '../../../shared/models/media_item.dart';
import '../../../shared/models/person.dart';
import '../../../shared/models/tag.dart';

final _mediaDao = MediaDao();
final _albumDao = AlbumDao();
final _peopleDao = PeopleDao();
final _tagDao = TagDao();

final searchQueryProvider = StateProvider<String>((ref) => '');
final searchSpaceProvider = StateProvider<MediaSpace?>((ref) => null);

/// Personal 필터: 선택된 앨범 id
final searchAlbumIdProvider = StateProvider<int?>((ref) => null);

/// Personal 필터: 선택된 인물 id
final searchPersonIdProvider = StateProvider<int?>((ref) => null);

/// 공통 태그 필터: 선택된 tag id
final searchTagIdProvider = StateProvider<int?>((ref) => null);

/// 앨범 목록 (Personal 필터용)
final searchAlbumListProvider = FutureProvider<List<Album>>((ref) async {
  return _albumDao.findAll();
});

/// 인물 목록 (Personal 필터용)
final searchPersonListProvider = FutureProvider<List<Person>>((ref) async {
  return _peopleDao.findAll();
});

/// 태그 목록 (space 기준 필터용)
final searchTagListProvider = FutureProvider.family<List<Tag>, MediaSpace?>((
  ref,
  space,
) async {
  if (space == null) return _tagDao.findAll();
  return _tagDao.findBySpace(space);
});

final searchResultProvider = FutureProvider<List<MediaItem>>((ref) async {
  final query = ref.watch(searchQueryProvider);
  final space = ref.watch(searchSpaceProvider);
  final albumId = ref.watch(searchAlbumIdProvider);
  final personId = ref.watch(searchPersonIdProvider);
  final tagId = ref.watch(searchTagIdProvider);
  if (query.trim().isEmpty) return [];
  return _mediaDao.search(
    query: query.trim(),
    space: space,
    albumId: albumId,
    personId: personId,
    tagIds: tagId != null ? [tagId] : null,
  );
});
