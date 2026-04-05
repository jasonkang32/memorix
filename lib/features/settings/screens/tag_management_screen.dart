import 'package:flutter/material.dart';
import '../../../core/db/tag_dao.dart';
import '../../../shared/models/media_item.dart';
import '../../../shared/models/tag.dart';

class TagManagementScreen extends StatefulWidget {
  const TagManagementScreen({super.key});

  @override
  State<TagManagementScreen> createState() => _TagManagementScreenState();
}

class _TagManagementScreenState extends State<TagManagementScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabCtrl;
  final _tagDao = TagDao();
  List<Tag> _workTags = [];
  List<Tag> _personalTags = [];

  @override
  void initState() {
    super.initState();
    _tabCtrl = TabController(length: 2, vsync: this);
    _load();
  }

  @override
  void dispose() {
    _tabCtrl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final all = await _tagDao.findAll();
    if (!mounted) return;
    setState(() {
      _workTags = all.where((t) => t.space == MediaSpace.work).toList();
      _personalTags = all.where((t) => t.space == MediaSpace.personal).toList();
    });
  }

  Future<void> _addTag(MediaSpace space) async {
    final result = await showDialog<Tag>(
      context: context,
      builder: (ctx) => _AddTagDialog(space: space),
    );
    if (result != null) {
      await _tagDao.insert(result);
      _load();
    }
  }

  Future<void> _deleteTag(Tag tag) async {
    if (!tag.isCustom) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('기본 태그는 삭제할 수 없습니다')),
      );
      return;
    }
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('태그 삭제'),
        content: Text('"${tag.label}" 태그를 삭제할까요?\n해당 태그가 붙은 미디어에서도 제거됩니다.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('취소')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('삭제'),
          ),
        ],
      ),
    );
    if (ok == true) {
      await _tagDao.delete(tag.id!);
      _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('태그 관리'),
        bottom: TabBar(
          controller: _tabCtrl,
          tabs: const [
            Tab(text: '💼 Work'),
            Tab(text: '🏠 Personal'),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton.small(
        onPressed: () => _addTag(
          _tabCtrl.index == 0 ? MediaSpace.work : MediaSpace.personal,
        ),
        tooltip: '태그 추가',
        child: const Icon(Icons.add),
      ),
      body: TabBarView(
        controller: _tabCtrl,
        children: [
          _TagList(tags: _workTags, onDelete: _deleteTag),
          _TagList(tags: _personalTags, onDelete: _deleteTag),
        ],
      ),
    );
  }
}

class _TagList extends StatelessWidget {
  final List<Tag> tags;
  final Future<void> Function(Tag) onDelete;
  const _TagList({required this.tags, required this.onDelete});

  @override
  Widget build(BuildContext context) {
    if (tags.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.label_outline, size: 48, color: Colors.grey[300]),
            const SizedBox(height: 12),
            Text('태그가 없습니다', style: TextStyle(color: Colors.grey[500])),
          ],
        ),
      );
    }
    return ListView.separated(
      itemCount: tags.length,
      separatorBuilder: (context, i) => const Divider(height: 1, indent: 72),
      itemBuilder: (context, i) {
        final tag = tags[i];
        final color = _parseColor(tag.color);
        return ListTile(
          leading: CircleAvatar(
            backgroundColor: color.withValues(alpha: 0.15),
            child: Text(tag.icon, style: const TextStyle(fontSize: 18)),
          ),
          title: Text(tag.label),
          subtitle: Text(
            tag.isCustom ? '사용자 정의 태그' : '기본 태그',
            style: TextStyle(
              fontSize: 11,
              color: tag.isCustom
                  ? Theme.of(context).colorScheme.primary
                  : Colors.grey[500],
            ),
          ),
          trailing: tag.isCustom
              ? IconButton(
                  icon: const Icon(Icons.delete_outline, color: Colors.red),
                  onPressed: () => onDelete(tag),
                )
              : const Icon(Icons.lock_outline, size: 16, color: Colors.grey),
        );
      },
    );
  }

  Color _parseColor(String hex) {
    try {
      return Color(int.parse(hex.replaceFirst('#', '0xFF')));
    } catch (_) {
      return Colors.grey;
    }
  }
}

class _AddTagDialog extends StatefulWidget {
  final MediaSpace space;
  const _AddTagDialog({required this.space});

  @override
  State<_AddTagDialog> createState() => _AddTagDialogState();
}

class _AddTagDialogState extends State<_AddTagDialog> {
  final _labelCtrl = TextEditingController();
  String _selectedIcon = '🏷️';
  String _selectedColor = '#607D8B';

  static const _icons = ['🏷️', '⭐', '📌', '🔥', '💡', '🎯', '📎', '🔖', '✅', '⚡'];
  static const _colors = [
    '#607D8B', '#F44336', '#E91E63', '#9C27B0',
    '#3F51B5', '#2196F3', '#009688', '#4CAF50',
    '#FF9800', '#795548',
  ];

  @override
  void dispose() {
    _labelCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('새 태그'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TextField(
            controller: _labelCtrl,
            decoration: const InputDecoration(
              labelText: '태그 이름',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 16),
          const Text('아이콘', style: TextStyle(fontSize: 12, color: Colors.grey)),
          const SizedBox(height: 6),
          Wrap(
            spacing: 8,
            children: _icons.map((icon) => GestureDetector(
              onTap: () => setState(() => _selectedIcon = icon),
              child: Container(
                padding: const EdgeInsets.all(6),
                decoration: BoxDecoration(
                  color: _selectedIcon == icon
                      ? Theme.of(context).colorScheme.primaryContainer
                      : null,
                  borderRadius: BorderRadius.circular(8),
                  border: _selectedIcon == icon
                      ? Border.all(color: Theme.of(context).colorScheme.primary)
                      : null,
                ),
                child: Text(icon, style: const TextStyle(fontSize: 20)),
              ),
            )).toList(),
          ),
          const SizedBox(height: 16),
          const Text('색상', style: TextStyle(fontSize: 12, color: Colors.grey)),
          const SizedBox(height: 6),
          Wrap(
            spacing: 8,
            children: _colors.map((hex) {
              final color = Color(int.parse(hex.replaceFirst('#', '0xFF')));
              return GestureDetector(
                onTap: () => setState(() => _selectedColor = hex),
                child: Container(
                  width: 28,
                  height: 28,
                  decoration: BoxDecoration(
                    color: color,
                    shape: BoxShape.circle,
                    border: _selectedColor == hex
                        ? Border.all(color: Colors.black, width: 2)
                        : null,
                  ),
                ),
              );
            }).toList(),
          ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('취소')),
        FilledButton(
          onPressed: () {
            final label = _labelCtrl.text.trim();
            if (label.isEmpty) return;
            Navigator.pop(
              context,
              Tag(
                space: widget.space,
                key: 'custom_${label.toLowerCase().replaceAll(' ', '_')}',
                label: label,
                color: _selectedColor,
                icon: _selectedIcon,
                isCustom: true,
              ),
            );
          },
          child: const Text('추가'),
        ),
      ],
    );
  }
}
