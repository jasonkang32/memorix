import 'package:sqflite/sqflite.dart';
import '../../shared/models/tag.dart';
import '../../shared/models/media_item.dart';
import 'database.dart';

class TagDao {
  Future<Database> get _db => AppDatabase.instance;

  Future<List<Tag>> findBySpace(MediaSpace space) async {
    final db = await _db;
    final rows = await db.query(
      'tags',
      where: 'space = ?',
      whereArgs: [space.name],
      orderBy: 'is_custom ASC, label ASC',
    );
    return rows.map(Tag.fromMap).toList();
  }

  Future<int> insert(Tag tag) async {
    final db = await _db;
    return db.insert(
      'tags',
      tag.toMap(),
      conflictAlgorithm: ConflictAlgorithm.ignore,
    );
  }

  Future<List<Tag>> findByMediaId(int mediaId) async {
    final db = await _db;
    final rows = await db.rawQuery(
      '''
      SELECT t.* FROM tags t
      JOIN media_tags mt ON t.id = mt.tag_id
      WHERE mt.media_id = ?
      ORDER BY t.label ASC
    ''',
      [mediaId],
    );
    return rows.map(Tag.fromMap).toList();
  }

  Future<List<Tag>> findAll() async {
    final db = await _db;
    final rows = await db.query(
      'tags',
      orderBy: 'space ASC, is_custom ASC, label ASC',
    );
    return rows.map(Tag.fromMap).toList();
  }

  Future<int> delete(int id) async {
    final db = await _db;
    // media_tags는 ON DELETE CASCADE로 자동 삭제
    return db.delete('tags', where: 'id = ?', whereArgs: [id]);
  }

  Future<int> update(Tag tag) async {
    final db = await _db;
    return db.update('tags', tag.toMap(), where: 'id = ?', whereArgs: [tag.id]);
  }

  Future<void> setMediaTags(int mediaId, List<int> tagIds) async {
    final db = await _db;
    await db.transaction((txn) async {
      await txn.delete(
        'media_tags',
        where: 'media_id = ?',
        whereArgs: [mediaId],
      );
      for (final tagId in tagIds) {
        await txn.insert('media_tags', {
          'media_id': mediaId,
          'tag_id': tagId,
        }, conflictAlgorithm: ConflictAlgorithm.ignore);
      }
    });
  }
}
