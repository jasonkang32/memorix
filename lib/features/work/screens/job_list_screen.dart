import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/db/jobs_dao.dart';
import '../providers/jobs_provider.dart';

class JobListScreen extends ConsumerStatefulWidget {
  const JobListScreen({super.key});

  @override
  ConsumerState<JobListScreen> createState() => _JobListScreenState();
}

class _JobListScreenState extends ConsumerState<JobListScreen> {
  final _searchCtrl = TextEditingController();
  String _query = '';

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final jobsAsync = ref.watch(jobsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('작업 선택'),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(56),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: TextField(
              controller: _searchCtrl,
              decoration: InputDecoration(
                hintText: '작업명 검색...',
                prefixIcon: const Icon(Icons.search, size: 20),
                filled: true,
                fillColor: Theme.of(
                  context,
                ).colorScheme.surfaceContainerHighest,
                contentPadding: EdgeInsets.zero,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: BorderSide.none,
                ),
                suffixIcon: _query.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear, size: 18),
                        onPressed: () {
                          _searchCtrl.clear();
                          setState(() => _query = '');
                        },
                      )
                    : null,
              ),
              onChanged: (v) => setState(() => _query = v),
            ),
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showNewJobDialog(context),
        icon: const Icon(Icons.add),
        label: const Text('새 작업'),
      ),
      body: jobsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('오류: $e')),
        data: (jobs) {
          final filtered = _query.trim().isEmpty
              ? jobs
              : jobs
                    .where(
                      (j) =>
                          j.jobName.contains(_query) || j.site.contains(_query),
                    )
                    .toList();

          if (filtered.isEmpty) {
            return _EmptyJobView(
              hasQuery: _query.isNotEmpty,
              onCreate: () => _showNewJobDialog(context),
            );
          }

          return ListView.separated(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 100),
            itemCount: filtered.length,
            separatorBuilder: (_, _) => const SizedBox(height: 4),
            itemBuilder: (_, i) => _JobTile(
              job: filtered[i],
              onTap: () => Navigator.pop(context, filtered[i]),
              onDelete: () => _confirmDelete(context, filtered[i]),
            ),
          );
        },
      ),
    );
  }

  Future<void> _showNewJobDialog(BuildContext context) async {
    final result = await showDialog<({String name, String site})>(
      context: context,
      builder: (_) => const _NewJobDialog(),
    );
    if (result == null || !context.mounted) return;

    final job = await ref
        .read(jobsProvider.notifier)
        .add(jobName: result.name, site: result.site);

    if (job != null && context.mounted) {
      Navigator.pop(context, job);
    }
  }

  Future<void> _confirmDelete(BuildContext context, Job job) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('작업 삭제'),
        content: Text('"${job.jobName}" 작업을 삭제하시겠습니까?\n연결된 사진은 삭제되지 않습니다.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('취소'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('삭제'),
          ),
        ],
      ),
    );
    if (ok == true && context.mounted) {
      await ref.read(jobsProvider.notifier).remove(job.id!);
    }
  }
}

// ── Job Tile ─────────────────────────────────────────────────────

class _JobTile extends StatelessWidget {
  const _JobTile({
    required this.job,
    required this.onTap,
    required this.onDelete,
  });

  final Job job;
  final VoidCallback onTap;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      child: ListTile(
        onTap: onTap,
        leading: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              colors: [Color(0xFF1A73E8), Color(0xFF00C896)],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: BorderRadius.circular(10),
          ),
          child: const Icon(Icons.work_outline, color: Colors.white, size: 20),
        ),
        title: Text(
          job.jobName,
          style: const TextStyle(fontWeight: FontWeight.w600),
        ),
        subtitle: job.site.isNotEmpty
            ? Text(
                job.site,
                style: TextStyle(
                  fontSize: 12,
                  color: Theme.of(
                    context,
                  ).colorScheme.onSurface.withValues(alpha: 0.6),
                ),
              )
            : null,
        trailing: IconButton(
          icon: const Icon(Icons.delete_outline, size: 18),
          color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.4),
          onPressed: onDelete,
        ),
      ),
    );
  }
}

// ── Empty View ────────────────────────────────────────────────────

class _EmptyJobView extends StatelessWidget {
  const _EmptyJobView({required this.hasQuery, required this.onCreate});

  final bool hasQuery;
  final VoidCallback onCreate;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            hasQuery ? Icons.search_off : Icons.work_outline,
            size: 56,
            color: Theme.of(context).dividerColor,
          ),
          const SizedBox(height: 16),
          Text(
            hasQuery ? '검색 결과가 없습니다' : '작업이 없습니다',
            style: TextStyle(
              color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.6),
            ),
          ),
          if (!hasQuery) ...[
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: onCreate,
              icon: const Icon(Icons.add),
              label: const Text('새 작업 만들기'),
            ),
          ],
        ],
      ),
    );
  }
}

// ── New Job Dialog ─────────────────────────────────────────────────

class _NewJobDialog extends StatefulWidget {
  const _NewJobDialog();

  @override
  State<_NewJobDialog> createState() => _NewJobDialogState();
}

class _NewJobDialogState extends State<_NewJobDialog> {
  final _nameCtrl = TextEditingController();
  final _siteCtrl = TextEditingController();
  final _formKey = GlobalKey<FormState>();

  @override
  void dispose() {
    _nameCtrl.dispose();
    _siteCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('새 작업 만들기'),
      content: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextFormField(
              controller: _nameCtrl,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: '작업명 *',
                hintText: '예: A현장 3호기 점검',
              ),
              validator: (v) =>
                  (v == null || v.trim().isEmpty) ? '작업명을 입력하세요' : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _siteCtrl,
              decoration: const InputDecoration(
                labelText: '현장명',
                hintText: '예: 부산 A공장',
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('취소'),
        ),
        FilledButton(
          onPressed: () {
            if (!_formKey.currentState!.validate()) return;
            Navigator.pop(context, (
              name: _nameCtrl.text.trim(),
              site: _siteCtrl.text.trim(),
            ));
          },
          child: const Text('만들기'),
        ),
      ],
    );
  }
}
