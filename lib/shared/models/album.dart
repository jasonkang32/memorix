enum EventType { travel, ceremony, gathering, birthday, daily, other }

class Album {
  final int? id;
  final EventType eventType;
  final String title;
  final int? dateStart;
  final int? dateEnd;
  final int? coverMediaId;
  final String memo;
  final int createdAt;

  const Album({
    this.id,
    required this.eventType,
    required this.title,
    this.dateStart,
    this.dateEnd,
    this.coverMediaId,
    this.memo = '',
    required this.createdAt,
  });

  factory Album.fromMap(Map<String, dynamic> map) => Album(
        id: map['id'] as int?,
        eventType: EventType.values.firstWhere(
          (e) => e.name == map['event_type'],
          orElse: () => EventType.other,
        ),
        title: map['title'] as String,
        dateStart: map['date_start'] as int?,
        dateEnd: map['date_end'] as int?,
        coverMediaId: map['cover_media_id'] as int?,
        memo: map['memo'] as String? ?? '',
        createdAt: map['created_at'] as int,
      );

  Map<String, dynamic> toMap() => {
        if (id != null) 'id': id,
        'event_type': eventType.name,
        'title': title,
        'date_start': dateStart,
        'date_end': dateEnd,
        'cover_media_id': coverMediaId,
        'memo': memo,
        'created_at': createdAt,
      };
}
