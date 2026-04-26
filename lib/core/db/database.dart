import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';

class AppDatabase {
  static Database? _db;

  static Future<Database> get instance async {
    _db ??= await _open();
    return _db!;
  }

  static Future<Database> _open() async {
    final dbPath = await getDatabasesPath();
    final path = join(dbPath, 'memorix.db');
    return openDatabase(
      path,
      version: 6,
      onCreate: _onCreate,
      onUpgrade: _onUpgrade,
    );
  }

  static Future<void> _onCreate(Database db, int version) async {
    await db.execute('''
      CREATE TABLE media (
        id            INTEGER PRIMARY KEY AUTOINCREMENT,
        space         TEXT NOT NULL DEFAULT 'work',
        media_type    TEXT NOT NULL,
        file_path     TEXT NOT NULL,
        thumb_path    TEXT,
        title         TEXT NOT NULL DEFAULT '',
        note          TEXT DEFAULT '',
        country_code  TEXT DEFAULT '',
        region        TEXT DEFAULT '',
        album_id      INTEGER REFERENCES albums(id) ON DELETE SET NULL,
        latitude      REAL,
        longitude     REAL,
        taken_at      INTEGER NOT NULL,
        created_at    INTEGER NOT NULL,
        file_size_kb  INTEGER DEFAULT 0,
        duration_sec  INTEGER DEFAULT 0,
        drive_synced  INTEGER DEFAULT 0,
        drive_file_id TEXT DEFAULT '',
        batch_id      TEXT DEFAULT '',
        ocr_text      TEXT DEFAULT '',
        job_id        INTEGER DEFAULT NULL REFERENCES jobs(id),
        encrypted     INTEGER NOT NULL DEFAULT 0
      )
    ''');

    await db.execute('''
      CREATE TABLE albums (
        id             INTEGER PRIMARY KEY AUTOINCREMENT,
        event_type     TEXT NOT NULL,
        title          TEXT NOT NULL,
        date_start     INTEGER,
        date_end       INTEGER,
        cover_media_id INTEGER REFERENCES media(id),
        memo           TEXT DEFAULT '',
        created_at     INTEGER NOT NULL
      )
    ''');

    await db.execute('''
      CREATE TABLE tags (
        id        INTEGER PRIMARY KEY AUTOINCREMENT,
        space     TEXT NOT NULL DEFAULT 'work',
        key       TEXT NOT NULL UNIQUE,
        label     TEXT NOT NULL,
        color     TEXT NOT NULL,
        icon      TEXT NOT NULL,
        is_custom INTEGER DEFAULT 0
      )
    ''');

    await db.execute('''
      CREATE TABLE media_tags (
        media_id INTEGER REFERENCES media(id) ON DELETE CASCADE,
        tag_id   INTEGER REFERENCES tags(id)  ON DELETE CASCADE,
        PRIMARY KEY (media_id, tag_id)
      )
    ''');

    await db.execute('''
      CREATE TABLE people (
        id    INTEGER PRIMARY KEY AUTOINCREMENT,
        name  TEXT NOT NULL UNIQUE
      )
    ''');

    await db.execute('''
      CREATE TABLE media_people (
        media_id  INTEGER REFERENCES media(id)   ON DELETE CASCADE,
        person_id INTEGER REFERENCES people(id)  ON DELETE CASCADE,
        PRIMARY KEY (media_id, person_id)
      )
    ''');

    await db.execute('''
      CREATE TABLE jobs (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        job_name   TEXT NOT NULL,
        site       TEXT DEFAULT '',
        created_at INTEGER NOT NULL
      )
    ''');

    await _createFts(db);

    await _insertDefaultTags(db);
  }

  // FTS4: fts5보다 호환성 높음 (Android 기본 SQLite 지원)
  static Future<void> _createFts(Database db) async {
    await db.execute('''
      CREATE VIRTUAL TABLE media_fts USING fts4(
        content="media",
        title, note, ocr_text
      )
    ''');

    await db.execute('''
      CREATE TRIGGER media_fts_insert AFTER INSERT ON media BEGIN
        INSERT INTO media_fts(docid, title, note, ocr_text)
        VALUES (new.id, new.title, new.note, new.ocr_text);
      END
    ''');
    await db.execute('''
      CREATE TRIGGER media_fts_update AFTER UPDATE ON media BEGIN
        DELETE FROM media_fts WHERE docid = old.id;
        INSERT INTO media_fts(docid, title, note, ocr_text)
        VALUES (new.id, new.title, new.note, new.ocr_text);
      END
    ''');
    await db.execute('''
      CREATE TRIGGER media_fts_delete AFTER DELETE ON media BEGIN
        DELETE FROM media_fts WHERE docid = old.id;
      END
    ''');
  }

  static Future<void> _onUpgrade(
    Database db,
    int oldVersion,
    int newVersion,
  ) async {
    if (oldVersion < 2) {
      // fts5 → fts4 교체
      await db.execute('DROP TRIGGER IF EXISTS media_fts_insert');
      await db.execute('DROP TRIGGER IF EXISTS media_fts_update');
      await db.execute('DROP TRIGGER IF EXISTS media_fts_delete');
      await db.execute('DROP TABLE IF EXISTS media_fts');
      await _createFts(db);
    }
    if (oldVersion < 3) {
      await db.execute("ALTER TABLE media ADD COLUMN batch_id TEXT DEFAULT ''");
    }
    if (oldVersion < 4) {
      // ocr_text 컬럼 추가
      await db.execute("ALTER TABLE media ADD COLUMN ocr_text TEXT DEFAULT ''");
      // FTS 트리거 재생성 (ocr_text 포함)
      await db.execute('DROP TRIGGER IF EXISTS media_fts_insert');
      await db.execute('DROP TRIGGER IF EXISTS media_fts_update');
      await db.execute('DROP TRIGGER IF EXISTS media_fts_delete');
      await db.execute('DROP TABLE IF EXISTS media_fts');
      await _createFts(db);
    }
    if (oldVersion < 5) {
      await db.execute('''
        CREATE TABLE IF NOT EXISTS jobs (
          id         INTEGER PRIMARY KEY AUTOINCREMENT,
          job_name   TEXT NOT NULL,
          site       TEXT DEFAULT '',
          created_at INTEGER NOT NULL
        )
      ''');
      // SQLite ALTER TABLE은 FK를 파싱만 하고 강제하지 않음 — 삭제 시 jobs_dao.delete()에서 명시 처리
      await db.execute(
        'ALTER TABLE media ADD COLUMN job_id INTEGER DEFAULT NULL REFERENCES jobs(id)',
      );
    }
    if (oldVersion < 6) {
      // Personal → Secret 통합. media + tags 두 테이블의 space 컬럼을 모두 변환
      await db.update(
        'media',
        {'space': 'secret'},
        where: 'space = ?',
        whereArgs: ['personal'],
      );
      await db.update(
        'tags',
        {'space': 'secret'},
        where: 'space = ?',
        whereArgs: ['personal'],
      );
      // encrypted=0 컬럼 추가 (Secret vault 도입 전 기존 파일은 비암호화)
      await db.execute(
        'ALTER TABLE media ADD COLUMN encrypted INTEGER NOT NULL DEFAULT 0',
      );
    }
  }

  static Future<void> _insertDefaultTags(Database db) async {
    final workTags = [
      ('install', '설치현장', '#FF6B35', 'build'),
      ('meeting', '미팅', '#4ECDC4', 'handshake'),
      ('equipment', '장비', '#45B7D1', 'settings'),
      ('document', '문서', '#96CEB4', 'description'),
      ('fault', '불량/결함', '#FF6B6B', 'warning'),
      ('site', '현장', '#FFEAA7', 'location_on'),
    ];
    final secretTags = [
      ('travel', '여행', '#74B9FF', 'flight'),
      ('food', '음식', '#FD79A8', 'restaurant'),
      ('family', '가족', '#FDCB6E', 'family_restroom'),
      ('event', '이벤트', '#A29BFE', 'celebration'),
    ];

    for (final t in workTags) {
      await db.insert('tags', {
        'space': 'work',
        'key': t.$1,
        'label': t.$2,
        'color': t.$3,
        'icon': t.$4,
      });
    }
    for (final t in secretTags) {
      await db.insert('tags', {
        'space': 'secret',
        'key': t.$1,
        'label': t.$2,
        'color': t.$3,
        'icon': t.$4,
      });
    }
  }
}
