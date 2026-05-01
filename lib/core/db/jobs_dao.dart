import 'package:sqflite/sqflite.dart';
import 'database.dart';

class Job {
  const Job({
    this.id,
    required this.jobName,
    this.site = '',
    required this.createdAt,
  });

  final int? id;
  final String jobName;
  final String site;
  final DateTime createdAt;

  Map<String, dynamic> toMap() => {
    if (id != null) 'id': id,
    'job_name': jobName,
    'site': site,
    'created_at': createdAt.millisecondsSinceEpoch,
  };

  static Job fromMap(Map<String, dynamic> m) => Job(
    id: m['id'] as int?,
    jobName: m['job_name'] as String,
    site: (m['site'] as String?) ?? '',
    createdAt: DateTime.fromMillisecondsSinceEpoch(m['created_at'] as int),
  );

  Job copyWith({int? id, String? jobName, String? site}) => Job(
    id: id ?? this.id,
    jobName: jobName ?? this.jobName,
    site: site ?? this.site,
    createdAt: createdAt,
  );
}

class JobsDao {
  Future<Database> get _db => AppDatabase.instance;

  Future<int> insert(Job job) async {
    final db = await _db;
    return db.insert('jobs', job.toMap());
  }

  Future<List<Job>> findAll() async {
    final db = await _db;
    final rows = await db.query('jobs', orderBy: 'created_at DESC');
    return rows.map(Job.fromMap).toList();
  }

  Future<List<Job>> search(String query) async {
    final db = await _db;
    final q = '%$query%';
    final rows = await db.query(
      'jobs',
      where: 'job_name LIKE ? OR site LIKE ?',
      whereArgs: [q, q],
      orderBy: 'created_at DESC',
    );
    return rows.map(Job.fromMap).toList();
  }

  Future<int> delete(int id) async {
    final db = await _db;
    return db.transaction((txn) async {
      // job 삭제 전 연결된 media의 job_id를 NULL로 정리
      await txn.update(
        'media',
        {'job_id': null},
        where: 'job_id = ?',
        whereArgs: [id],
      );
      return txn.delete('jobs', where: 'id = ?', whereArgs: [id]);
    });
  }
}
