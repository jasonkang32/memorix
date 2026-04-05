import 'package:flutter/material.dart';
import '../models/tag.dart';

class TagChipRow extends StatelessWidget {
  final List<Tag> tags;
  final Set<int> selectedIds;
  final ValueChanged<int>? onToggle;
  final bool readOnly;

  const TagChipRow({
    super.key,
    required this.tags,
    this.selectedIds = const {},
    this.onToggle,
    this.readOnly = false,
  });

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 6,
      runSpacing: 4,
      children: tags.map((tag) {
        final selected = selectedIds.contains(tag.id);
        final color = _parseColor(tag.color);
        return FilterChip(
          label: Text(tag.label, style: const TextStyle(fontSize: 12)),
          selected: selected,
          selectedColor: color.withValues(alpha: 0.3),
          checkmarkColor: color,
          onSelected: readOnly ? null : (_) => onToggle?.call(tag.id!),
        );
      }).toList(),
    );
  }

  static Color _parseColor(String hex) {
    try {
      return Color(int.parse(hex.replaceFirst('#', 'FF'), radix: 16));
    } catch (_) {
      return Colors.grey;
    }
  }
}
