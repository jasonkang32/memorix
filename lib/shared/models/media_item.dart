enum MediaSpace { work, personal }
enum MediaType { photo, video, document }

class MediaItem {
  final int? id;
  final MediaSpace space;
  final MediaType mediaType;
  final String filePath;
  final String? thumbPath;
  final String title;
  final String note;
  // Work
  final String countryCode;
  final String region;
  // Personal
  final int? albumId;
  // 공통
  final double? latitude;
  final double? longitude;
  final int takenAt;
  final int createdAt;
  final int fileSizeKb;
  final int durationSec;
  final int driveSynced;
  final String driveFileId;
  // 일괄 등록 그룹 식별자
  final String batchId;

  const MediaItem({
    this.id,
    required this.space,
    required this.mediaType,
    required this.filePath,
    this.thumbPath,
    this.title = '',
    this.note = '',
    this.countryCode = '',
    this.region = '',
    this.albumId,
    this.latitude,
    this.longitude,
    required this.takenAt,
    required this.createdAt,
    this.fileSizeKb = 0,
    this.durationSec = 0,
    this.driveSynced = 0,
    this.driveFileId = '',
    this.batchId = '',
  });

  factory MediaItem.fromMap(Map<String, dynamic> map) => MediaItem(
        id: map['id'] as int?,
        space: map['space'] == 'personal' ? MediaSpace.personal : MediaSpace.work,
        mediaType: _parseType(map['media_type'] as String),
        filePath: map['file_path'] as String,
        thumbPath: map['thumb_path'] as String?,
        title: map['title'] as String? ?? '',
        note: map['note'] as String? ?? '',
        countryCode: map['country_code'] as String? ?? '',
        region: map['region'] as String? ?? '',
        albumId: map['album_id'] as int?,
        latitude: map['latitude'] as double?,
        longitude: map['longitude'] as double?,
        takenAt: map['taken_at'] as int,
        createdAt: map['created_at'] as int,
        fileSizeKb: map['file_size_kb'] as int? ?? 0,
        durationSec: map['duration_sec'] as int? ?? 0,
        driveSynced: map['drive_synced'] as int? ?? 0,
        driveFileId: map['drive_file_id'] as String? ?? '',
        batchId: map['batch_id'] as String? ?? '',
      );

  Map<String, dynamic> toMap() => {
        if (id != null) 'id': id,
        'space': space.name,
        'media_type': mediaType.name,
        'file_path': filePath,
        'thumb_path': thumbPath,
        'title': title,
        'note': note,
        'country_code': countryCode,
        'region': region,
        'album_id': albumId,
        'latitude': latitude,
        'longitude': longitude,
        'taken_at': takenAt,
        'created_at': createdAt,
        'file_size_kb': fileSizeKb,
        'duration_sec': durationSec,
        'drive_synced': driveSynced,
        'drive_file_id': driveFileId,
        'batch_id': batchId,
      };

  static MediaType _parseType(String s) => switch (s) {
        'video' => MediaType.video,
        'document' => MediaType.document,
        _ => MediaType.photo,
      };

  MediaItem copyWith({
    int? id,
    MediaSpace? space,
    MediaType? mediaType,
    String? filePath,
    String? thumbPath,
    String? title,
    String? note,
    String? countryCode,
    String? region,
    int? albumId,
    double? latitude,
    double? longitude,
    int? takenAt,
    int? createdAt,
    int? fileSizeKb,
    int? durationSec,
    int? driveSynced,
    String? driveFileId,
    String? batchId,
  }) =>
      MediaItem(
        id: id ?? this.id,
        space: space ?? this.space,
        mediaType: mediaType ?? this.mediaType,
        filePath: filePath ?? this.filePath,
        thumbPath: thumbPath ?? this.thumbPath,
        title: title ?? this.title,
        note: note ?? this.note,
        countryCode: countryCode ?? this.countryCode,
        region: region ?? this.region,
        albumId: albumId ?? this.albumId,
        latitude: latitude ?? this.latitude,
        longitude: longitude ?? this.longitude,
        takenAt: takenAt ?? this.takenAt,
        createdAt: createdAt ?? this.createdAt,
        fileSizeKb: fileSizeKb ?? this.fileSizeKb,
        durationSec: durationSec ?? this.durationSec,
        driveSynced: driveSynced ?? this.driveSynced,
        driveFileId: driveFileId ?? this.driveFileId,
        batchId: batchId ?? this.batchId,
      );
}
