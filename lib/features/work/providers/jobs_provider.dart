import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/db/jobs_dao.dart';

final jobsProvider = StateNotifierProvider<JobsNotifier, AsyncValue<List<Job>>>(
  (ref) => JobsNotifier(),
);

class JobsNotifier extends StateNotifier<AsyncValue<List<Job>>> {
  JobsNotifier() : super(const AsyncValue.loading()) {
    load();
  }

  final _dao = JobsDao();

  Future<void> load() async {
    state = const AsyncValue.loading();
    try {
      final jobs = await _dao.findAll();
      state = AsyncValue.data(jobs);
    } on Exception catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  Future<Job?> add({required String jobName, String site = ''}) async {
    try {
      final now = DateTime.now();
      final insertedId = await _dao.insert(
        Job(jobName: jobName, site: site, createdAt: now),
      );
      final newJob = Job(
        id: insertedId,
        jobName: jobName,
        site: site,
        createdAt: now,
      );
      final current = state.valueOrNull ?? [];
      state = AsyncValue.data([newJob, ...current]);
      return newJob;
    } on Exception {
      return null;
    }
  }

  Future<void> remove(int id) async {
    try {
      await _dao.delete(id);
      final current = state.valueOrNull ?? [];
      state = AsyncValue.data(current.where((j) => j.id != id).toList());
    } on Exception catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }
}
