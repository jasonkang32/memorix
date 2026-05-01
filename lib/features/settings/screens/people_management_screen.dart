import 'package:flutter/material.dart';
import '../../../core/db/people_dao.dart';
import '../../../shared/models/person.dart';

class PeopleManagementScreen extends StatefulWidget {
  const PeopleManagementScreen({super.key});

  @override
  State<PeopleManagementScreen> createState() => _PeopleManagementScreenState();
}

class _PeopleManagementScreenState extends State<PeopleManagementScreen> {
  final _dao = PeopleDao();
  List<Person> _people = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final list = await _dao.findAll();
    if (!mounted) return;
    setState(() => _people = list);
  }

  Future<void> _addPerson() async {
    final name = await _showNameDialog(title: '인물 추가');
    if (name == null || name.isEmpty) return;
    await _dao.upsert(name);
    _load();
  }

  Future<void> _renamePerson(Person person) async {
    final name = await _showNameDialog(title: '이름 수정', initial: person.name);
    if (name == null || name.isEmpty || name == person.name) return;
    await _dao.insert(Person(id: person.id, name: name));
    _load();
  }

  Future<void> _deletePerson(Person person) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('인물 삭제'),
        content: Text('"${person.name}"을(를) 삭제할까요?\n해당 인물이 태그된 미디어에서도 제거됩니다.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('취소'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('삭제'),
          ),
        ],
      ),
    );
    if (ok == true) {
      await _dao.delete(person.id!);
      _load();
    }
  }

  Future<String?> _showNameDialog({
    required String title,
    String initial = '',
  }) {
    final ctrl = TextEditingController(text: initial);
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          decoration: const InputDecoration(
            labelText: '이름',
            border: OutlineInputBorder(),
          ),
          onSubmitted: (v) => Navigator.pop(ctx, v.trim()),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('취소'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, ctrl.text.trim()),
            child: const Text('확인'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('인물 관리')),
      floatingActionButton: FloatingActionButton.small(
        onPressed: _addPerson,
        tooltip: '인물 추가',
        child: const Icon(Icons.person_add_outlined),
      ),
      body: _people.isEmpty
          ? Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    Icons.people_outline,
                    size: 56,
                    color: Theme.of(context).dividerColor,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    '등록된 인물이 없습니다',
                    style: TextStyle(
                      color: Theme.of(
                        context,
                      ).colorScheme.onSurface.withValues(alpha: 0.6),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    '+ 버튼으로 추가하세요',
                    style: TextStyle(
                      color: Theme.of(
                        context,
                      ).colorScheme.onSurface.withValues(alpha: 0.4),
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            )
          : ListView.separated(
              itemCount: _people.length,
              separatorBuilder: (context, i) =>
                  const Divider(height: 1, indent: 72),
              itemBuilder: (context, i) {
                final person = _people[i];
                final initials = person.name.isNotEmpty
                    ? person.name[0].toUpperCase()
                    : '?';
                return ListTile(
                  leading: CircleAvatar(
                    backgroundColor: Theme.of(
                      context,
                    ).colorScheme.primaryContainer,
                    child: Text(
                      initials,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.onPrimaryContainer,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  title: Text(person.name),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        icon: const Icon(Icons.edit_outlined),
                        onPressed: () => _renamePerson(person),
                        tooltip: '이름 수정',
                      ),
                      IconButton(
                        icon: const Icon(
                          Icons.delete_outline,
                          color: Colors.red,
                        ),
                        onPressed: () => _deletePerson(person),
                        tooltip: '삭제',
                      ),
                    ],
                  ),
                );
              },
            ),
    );
  }
}
