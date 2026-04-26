import 'media_item.dart';

class Tag {
  final int? id;
  final MediaSpace space;
  final String key;
  final String label;
  final String color;
  final String icon;
  final bool isCustom;

  const Tag({
    this.id,
    required this.space,
    required this.key,
    required this.label,
    required this.color,
    required this.icon,
    this.isCustom = false,
  });

  factory Tag.fromMap(Map<String, dynamic> map) => Tag(
    id: map['id'] as int?,
    space: MediaSpaceX.parse(map['space'] as String?),
    key: map['key'] as String,
    label: map['label'] as String,
    color: map['color'] as String,
    icon: map['icon'] as String,
    isCustom: (map['is_custom'] as int? ?? 0) == 1,
  );

  Map<String, dynamic> toMap() => {
    if (id != null) 'id': id,
    'space': space.dbValue,
    'key': key,
    'label': label,
    'color': color,
    'icon': icon,
    'is_custom': isCustom ? 1 : 0,
  };
}
