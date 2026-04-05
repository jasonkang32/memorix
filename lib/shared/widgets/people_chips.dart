import 'package:flutter/material.dart';
import '../../core/db/people_dao.dart';
import '../models/person.dart';

class PeopleChips extends StatefulWidget {
  final List<Person> allPeople;
  final Set<int> selectedIds;
  final ValueChanged<Set<int>> onChanged;

  const PeopleChips({
    super.key,
    required this.allPeople,
    required this.selectedIds,
    required this.onChanged,
  });

  @override
  State<PeopleChips> createState() => _PeopleChipsState();
}

class _PeopleChipsState extends State<PeopleChips> {
  final _dao = PeopleDao();
  final _inputCtrl = TextEditingController();

  static const _primary = Color(0xFF7B61FF); // 퍼플 (Personal 테마)
  static const _textColor = Color(0xFF3D2D99);

  @override
  void dispose() {
    _inputCtrl.dispose();
    super.dispose();
  }

  Future<void> _addPerson() async {
    final name = _inputCtrl.text.trim();
    if (name.isEmpty) return;

    // 이미 존재하는 인물이면 선택 상태로 전환
    final existing = widget.allPeople.where((p) => p.name == name).firstOrNull;
    if (existing != null && existing.id != null) {
      final newSet = Set<int>.from(widget.selectedIds)..add(existing.id!);
      widget.onChanged(newSet);
      _inputCtrl.clear();
      return;
    }

    // 새 인물 DB 저장 후 선택
    final id = await _dao.upsert(name);
    final newSet = Set<int>.from(widget.selectedIds)..add(id);
    widget.onChanged(newSet);
    _inputCtrl.clear();
  }

  void _togglePerson(Person p) {
    final newSet = Set<int>.from(widget.selectedIds);
    if (newSet.contains(p.id)) {
      newSet.remove(p.id);
    } else {
      newSet.add(p.id!);
    }
    widget.onChanged(newSet);
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 기존 인물 칩 목록
        if (widget.allPeople.isNotEmpty)
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: widget.allPeople.map((p) {
              final selected = widget.selectedIds.contains(p.id);
              return GestureDetector(
                onTap: () => _togglePerson(p),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 150),
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  decoration: BoxDecoration(
                    color: selected ? _primary : _primary.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: _primary, width: 1.5),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.person,
                        size: 13,
                        color: selected ? Colors.white : _textColor,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        p.name,
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          color: selected ? Colors.white : _textColor,
                        ),
                      ),
                    ],
                  ),
                ),
              );
            }).toList(),
          ),

        if (widget.allPeople.isNotEmpty) const SizedBox(height: 10),

        // 인라인 입력 필드 (태그와 동일한 방식)
        Row(
          children: [
            Expanded(
              child: TextField(
                controller: _inputCtrl,
                decoration: const InputDecoration(
                  hintText: '이름 입력 (예: 엄마, 홍길동)',
                  hintStyle: TextStyle(fontSize: 13),
                  prefixIcon: Icon(Icons.person_add_outlined, size: 18),
                  contentPadding:
                      EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                  isDense: true,
                ),
                onSubmitted: (_) => _addPerson(),
              ),
            ),
            const SizedBox(width: 8),
            FilledButton(
              onPressed: _addPerson,
              style: FilledButton.styleFrom(
                backgroundColor: _primary,
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
              ),
              child: const Text('추가', style: TextStyle(fontSize: 13)),
            ),
          ],
        ),
      ],
    );
  }
}
