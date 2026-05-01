import 'package:sqflite/sqflite.dart';
import '../../shared/models/album.dart';
import 'database.dart';

class AlbumDao {
  Future<Database> get _db => AppDatabase.instance;

  Future<int> insert(Album album) async {
    final db = await _db;
    return db.insert('albums', album.toMap());
  }

  Future<int> update(Album album) async {
    final db = await _db;
    return db.update(
      'albums',
      album.toMap(),
      where: 'id = ?',
      whereArgs: [album.id],
    );
  }

  Future<int> delete(int id) async {
    final db = await _db;
    return db.delete('albums', where: 'id = ?', whereArgs: [id]);
  }

  Future<List<Album>> findAll() async {
    final db = await _db;
    final rows = await db.query('albums', orderBy: 'date_start DESC');
    return rows.map(Album.fromMap).toList();
  }

  Future<List<Album>> findByEventType(EventType type) async {
    final db = await _db;
    final rows = await db.query(
      'albums',
      where: 'event_type = ?',
      whereArgs: [type.name],
      orderBy: 'date_start DESC',
    );
    return rows.map(Album.fromMap).toList();
  }

  Future<Album?> findById(int id) async {
    final db = await _db;
    final rows = await db.query('albums', where: 'id = ?', whereArgs: [id]);
    if (rows.isEmpty) return null;
    return Album.fromMap(rows.first);
  }
}
