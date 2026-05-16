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

  /// Work Space 목록 (국가·지역 필터).
  ///
  /// [includeLocked] = true (default): 그리드/필터 등 일반 화면에서 사용.
  /// 잠긴 항목도 포함되어 위젯 단계에서 블러 처리된다.
  /// PDF 보고서 등 외부 노출 흐름에서는 false로 명시 — 잠긴 항목은
  /// 자동 제외된다 (spec 8b).
  Future<List<MediaItem>> findWork({
    String? countryCode,
    String? region,
    String? mediaType,
    int limit = 50,
    int offset = 0,
    bool includeLocked = true,
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
    if (!includeLocked) {
      where.add('is_locked = 0');
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

  /// Personal Space 목록 (앨범 필터).
  /// v7 마이그레이션 후 모든 legacy 'secret' row는 'personal'로 통합됨.
  Future<List<MediaItem>> findSecret({
    int? albumId,
    String? mediaType,
    int limit = 50,
    int offset = 0,
  }) async {
    final db = await _db;
    final where = <String>["space = 'personal'"];
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
      conditions.add("m.space = '${space.dbValue}'");
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

  /// 드라이브 미동기화 목록.
  ///
  /// 잠긴 항목(`is_locked = 1`)은 외부 클라우드 노출을 막기 위해 자동 제외
  /// (spec 8a). 잠금 해제 후 다시 큐에 들어온다.
  Future<List<MediaItem>> findPendingSync() async {
    final db = await _db;
    final rows = await db.query(
      'media',
      where: 'drive_synced = 0 AND is_locked = 0',
    );
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

  /// space는 'work' 또는 'personal'. v7 이후 legacy 'secret' row는 모두
  /// 'personal'로 통합되었으므로 단순 동등 비교로 충분하다.
  static (String, List<dynamic>) _spaceClause(String space) {
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

  /// 최근 등록 미디어. space 필수 — 호출처가 명시적으로 어느 공간을 보일지
  /// 결정해야 한다. 공간 분리 원칙(secret 보관함은 공용 화면에 노출 금지)을
  /// 강제하기 위해 default 값 없음.
  ///
  /// Bug #2 회귀 가드: 이전 버전은 where 절이 없어 secret도 반환했다.
  Future<List<MediaItem>> findRecent({
    required String space,
    int limit = 10,
  }) async {
    final db = await _db;
    final (where, args) = _spaceClause(space);
    final rows = await db.query(
      'media',
      where: where,
      whereArgs: args.isEmpty ? null : args,
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

  /// 가장 최근에 등록된 미디어의 country_code를 반환.
  ///
  /// 빈 country_code(`''`)는 무시한다. space 필수 — Work/Personal 분리 원칙.
  /// 새 미디어 등록 시 country picker의 default 값 fallback으로 사용
  /// (폰 locale에 country code가 없을 때).
  Future<String?> findMostRecentCountryCode({required String space}) async {
    final db = await _db;
    final rows = await db.query(
      'media',
      columns: ['country_code'],
      where: "space = ? AND country_code != ''",
      whereArgs: [space],
      orderBy: 'created_at DESC',
      limit: 1,
    );
    if (rows.isEmpty) return null;
    return rows.first['country_code'] as String?;
  }

  /// 키워드로 Work/Personal 내 빠른 검색 — note·태그·지역·국가 포함
  Future<List<MediaItem>> quickSearch(String query, String space) async {
    final db = await _db;
    if (query.trim().isEmpty) {
      return space == 'work' ? findWork(limit: 200) : findSecret(limit: 200);
    }
    final q = '%$query%';
    final rows = await db.rawQuery(
      '''
      SELECT DISTINCT m.* FROM media m
      LEFT JOIN media_tags mt ON m.id = mt.media_id
      LEFT JOIN tags t ON mt.tag_id = t.id
      WHERE m.space = ?
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
      [space, q, q, q, q, q, q],
    );
    return rows.map(MediaItem.fromMap).toList();
  }
}
