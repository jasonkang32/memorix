import 'package:sqflite/sqflite.dart';
import '../../shared/models/media_item.dart';
import 'database.dart';

class MediaDao {
  Future<Database> get _db => AppDatabase.instance;

  Future<int> insert(MediaItem item) async {
    final db = await _db;
    return db.insert('media', item.toMap());
  }

  Future<int> update(MediaItem item) async {
    final db = await _db;
    return db.update(
      'media',
      item.toMap(),
      where: 'id = ?',
      whereArgs: [item.id],
    );
  }

  Future<int> delete(int id) async {
    final db = await _db;
    return db.delete('media', where: 'id = ?', whereArgs: [id]);
  }

  Future<MediaItem?> findById(int id) async {
    final db = await _db;
    final rows = await db.query('media', where: 'id = ?', whereArgs: [id]);
    if (rows.isEmpty) return null;
    return MediaItem.fromMap(rows.first);
  }

  /// Work Space 목록 (국가·지역 필터)
  Future<List<MediaItem>> findWork({
    String? countryCode,
    String? region,
    String? mediaType,
    int limit = 50,
    int offset = 0,
  }) async {
    final db = await _db;
    final where = <String>["space = 'work'"];
    final args = <dynamic>[];
    if (countryCode != null) {
      where.add('country_code = ?');
      args.add(countryCode);
    }
    if (region != null) {
      where.add('region = ?');
      args.add(region);
    }
    if (mediaType != null) {
      where.add('media_type = ?');
      args.add(mediaType);
    }
    final rows = await db.query(
      'media',
      where: where.join(' AND '),
      whereArgs: args.isEmpty ? null : args,
      orderBy: 'created_at DESC',
      limit: limit,
      offset: offset,
    );
    return rows.map(MediaItem.fromMap).toList();
  }

  /// Secret Space 목록 (앨범 필터). 구버전 호환을 위해 'personal'도 포함.
  Future<List<MediaItem>> findSecret({
    int? albumId,
    String? mediaType,
    int limit = 50,
    int offset = 0,
  }) async {
    final db = await _db;
    final where = <String>["space IN ('secret','personal')"];
    final args = <dynamic>[];
    if (albumId != null) {
      where.add('album_id = ?');
      args.add(albumId);
    }
    if (mediaType != null) {
      where.add('media_type = ?');
      args.add(mediaType);
    }
    final rows = await db.query(
      'media',
      where: where.join(' AND '),
      whereArgs: args.isEmpty ? null : args,
      orderBy: 'created_at DESC',
      limit: limit,
      offset: offset,
    );
    return rows.map(MediaItem.fromMap).toList();
  }

  /// 호환용 별칭 — 신규 코드는 [findSecret]을 사용.
  @Deprecated('Use findSecret')
  Future<List<MediaItem>> findPersonal({
    int? albumId,
    String? mediaType,
    int limit = 50,
    int offset = 0,
  }) => findSecret(
    albumId: albumId,
    mediaType: mediaType,
    limit: limit,
    offset: offset,
  );

  /// FTS5 전문 검색 (Work + Personal 통합)
  Future<List<MediaItem>> search({
    required String query,
    MediaSpace? space,
    List<int>? tagIds,
    String? countryCode,
    String? region,
    int? albumId,
    int? personId,
    int limit = 50,
  }) async {
    final db = await _db;
    final conditions = <String>['media_fts MATCH ?'];
    final args = <dynamic>[query];

    if (space != null) {
      // secret 검색 시에는 legacy 'personal' row도 포함
      if (space == MediaSpace.secret) {
        conditions.add("m.space IN ('secret','personal')");
      } else {
        conditions.add("m.space = '${space.dbValue}'");
      }
    }
    if (countryCode != null) {
      conditions.add("m.country_code = ?");
      args.add(countryCode);
    }
    if (region != null) {
      conditions.add("m.region = ?");
      args.add(region);
    }
    if (albumId != null) {
      conditions.add("m.album_id = ?");
      args.add(albumId);
    }

    String sql = '''
      SELECT m.* FROM media m
      JOIN media_fts fts ON m.id = fts.rowid
    ''';
    if (tagIds != null && tagIds.isNotEmpty) {
      sql += ' JOIN media_tags mt ON m.id = mt.media_id';
      conditions.add('mt.tag_id IN (${tagIds.map((_) => '?').join(',')})');
      args.addAll(tagIds);
    }
    if (personId != null) {
      sql += ' JOIN media_people mp ON m.id = mp.media_id';
      conditions.add('mp.person_id = ?');
      args.add(personId);
    }
    sql += ' WHERE ${conditions.join(' AND ')}';
    sql += ' GROUP BY m.id ORDER BY m.taken_at DESC LIMIT $limit';

    final rows = await db.rawQuery(sql, args);
    return rows.map(MediaItem.fromMap).toList();
  }

  /// 드라이브 미동기화 목록
  Future<List<MediaItem>> findPendingSync() async {
    final db = await _db;
    final rows = await db.query('media', where: 'drive_synced = 0');
    return rows.map(MediaItem.fromMap).toList();
  }

  Future<void> updateOcrText(int id, String ocrText) async {
    final db = await _db;
    await db.update(
      'media',
      {'ocr_text': ocrText},
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<void> moveToAlbum(int mediaId, int? albumId) async {
    final db = await _db;
    await db.update(
      'media',
      {'album_id': albumId},
      where: 'id = ?',
      whereArgs: [mediaId],
    );
  }

  Future<void> markSynced(int id, String driveFileId) async {
    final db = await _db;
    await db.update(
      'media',
      {'drive_synced': 1, 'drive_file_id': driveFileId},
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  // ── 통계 쿼리 ──────────────────────────────────────────────

  /// space가 'secret'/'work' 같은 단일 값일 수도 있고, secret 영역 통합을 위해
  /// legacy 'personal'까지 함께 묶어야 할 수도 있다. 이 헬퍼가 SQL 단편을 만든다.
  static (String, List<dynamic>) _spaceClause(String space) {
    if (space == 'secret') {
      return ("space IN ('secret','personal')", const []);
    }
    return ('space = ?', [space]);
  }

  Future<int> countBySpace(String space) async {
    final db = await _db;
    final (where, args) = _spaceClause(space);
    final r = await db.rawQuery(
      'SELECT COUNT(*) as c FROM media WHERE $where',
      args,
    );
    return (r.first['c'] as int?) ?? 0;
  }

  /// 배치 그룹 기준 등록 수 (타임라인 카드 수와 동일)
  Future<int> countGroupsBySpace(String space) async {
    final db = await _db;
    final (where, args) = _spaceClause(space);
    final r = await db.rawQuery('''
      SELECT COUNT(*) as c FROM (
        SELECT CASE
          WHEN batch_id IS NOT NULL AND batch_id != '' THEN batch_id
          ELSE CAST(id AS TEXT)
        END as grp
        FROM media WHERE $where
        GROUP BY grp
      )
    ''', args);
    return (r.first['c'] as int?) ?? 0;
  }

  Future<Map<String, int>> countByTypeForSpace(String space) async {
    final db = await _db;
    final (where, args) = _spaceClause(space);
    final rows = await db.rawQuery(
      'SELECT media_type, COUNT(*) as c FROM media WHERE $where GROUP BY media_type',
      args,
    );
    return {
      for (final r in rows) r['media_type'] as String: (r['c'] as int?) ?? 0,
    };
  }

  Future<int> countDistinctCountries() async {
    final db = await _db;
    final r = await db.rawQuery(
      "SELECT COUNT(DISTINCT country_code) as c FROM media WHERE space='work' AND country_code != ''",
    );
    return (r.first['c'] as int?) ?? 0;
  }

  Future<int> sumFileSizeKb() async {
    final db = await _db;
    final r = await db.rawQuery("SELECT SUM(file_size_kb) as s FROM media");
    return (r.first['s'] as int?) ?? 0;
  }

  Future<List<MediaItem>> findRecent({int limit = 10}) async {
    final db = await _db;
    final rows = await db.query(
      'media',
      orderBy: 'created_at DESC',
      limit: limit,
    );
    return rows.map(MediaItem.fromMap).toList();
  }

  /// 최근 N일간 날짜별 등록 건수 (yyyy-MM-dd → count)
  Future<Map<String, int>> activityByDay({int days = 30}) async {
    final db = await _db;
    final since = DateTime.now()
        .subtract(Duration(days: days))
        .millisecondsSinceEpoch;
    final rows = await db.rawQuery(
      '''
      SELECT strftime('%Y-%m-%d', created_at / 1000, 'unixepoch', 'localtime') as day,
             COUNT(*) as c
      FROM media
      WHERE created_at >= ?
      GROUP BY day
      ORDER BY day
    ''',
      [since],
    );
    return {for (final r in rows) r['day'] as String: (r['c'] as int?) ?? 0};
  }

  /// 태그별 사용 횟수 TOP N
  Future<List<Map<String, dynamic>>> topTags({int limit = 5}) async {
    final db = await _db;
    final rows = await db.rawQuery(
      '''
      SELECT t.label, t.color, COUNT(mt.media_id) as cnt
      FROM tags t
      JOIN media_tags mt ON t.id = mt.tag_id
      GROUP BY t.id
      ORDER BY cnt DESC
      LIMIT ?
    ''',
      [limit],
    );
    return rows.toList();
  }

  /// 키워드로 Work/Secret 내 빠른 검색 — note·태그·지역·국가 포함
  Future<List<MediaItem>> quickSearch(String query, String space) async {
    final db = await _db;
    if (query.trim().isEmpty) {
      return space == 'work' ? findWork(limit: 200) : findSecret(limit: 200);
    }
    final q = '%$query%';
    // legacy 'personal' row도 secret 검색에 포함
    final spaceClause = space == 'work'
        ? 'm.space = ?'
        : "m.space IN ('secret','personal') AND ? = ?";
    final spaceArgs = space == 'work' ? [space] : [space, space];
    final rows = await db.rawQuery(
      '''
      SELECT DISTINCT m.* FROM media m
      LEFT JOIN media_tags mt ON m.id = mt.media_id
      LEFT JOIN tags t ON mt.tag_id = t.id
      WHERE $spaceClause
        AND (
          m.title LIKE ? OR
          m.note LIKE ? OR
          m.region LIKE ? OR
          m.country_code LIKE ? OR
          t.label LIKE ? OR
          m.ocr_text LIKE ?
        )
      ORDER BY m.taken_at DESC
      LIMIT 100
    ''',
      [...spaceArgs, q, q, q, q, q, q],
    );
    return rows.map(MediaItem.fromMap).toList();
  }
}
