import 'package:sqflite/sqflite.dart';
import '../../shared/models/person.dart';
import 'database.dart';

class PeopleDao {
  Future<Database> get _db => AppDatabase.instance;

  Future<List<Person>> findAll() async {
    final db = await _db;
    final rows = await db.query('people', orderBy: 'name ASC');
    return rows.map(Person.fromMap).toList();
  }

  Future<Person?> findByName(String name) async {
    final db = await _db;
    final rows = await db.query('people', where: 'name = ?', whereArgs: [name]);
    if (rows.isEmpty) return null;
    return Person.fromMap(rows.first);
  }

  Future<int> insert(Person person) async {
    final db = await _db;
    return db.insert(
      'people',
      person.toMap(),
      conflictAlgorithm: ConflictAlgorithm.ignore,
    );
  }

  Future<int> delete(int id) async {
    final db = await _db;
    return db.delete('people', where: 'id = ?', whereArgs: [id]);
  }

  Future<List<Person>> findByMediaId(int mediaId) async {
    final db = await _db;
    final rows = await db.rawQuery(
      '''
      SELECT p.* FROM people p
      JOIN media_people mp ON p.id = mp.person_id
      WHERE mp.media_id = ?
      ORDER BY p.name ASC
    ''',
      [mediaId],
    );
    return rows.map(Person.fromMap).toList();
  }

  Future<void> setMediaPeople(int mediaId, List<int> personIds) async {
    final db = await _db;
    await db.transaction((txn) async {
      await txn.delete(
        'media_people',
        where: 'media_id = ?',
        whereArgs: [mediaId],
      );
      for (final pid in personIds) {
        await txn.insert('media_people', {
          'media_id': mediaId,
          'person_id': pid,
        }, conflictAlgorithm: ConflictAlgorithm.ignore);
      }
    });
  }

  /// 인물 이름으로 조회, 없으면 생성 후 id 반환
  Future<int> upsert(String name) async {
    final existing = await findByName(name);
    if (existing?.id != null) return existing!.id!;
    return insert(Person(name: name));
  }
}
