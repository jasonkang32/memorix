/// 미디어 영역.
/// `personal`은 v1.x 호환을 위해 남겨두며, DB에는 더 이상 새로 기록되지 않는다.
/// 마이그레이션 v6에서 모든 row가 `secret`으로 일괄 변환된다.
enum MediaSpace { work, secret, personal }

enum MediaType { photo, video, document }

extension MediaSpaceX on MediaSpace {
  /// DB 저장값 — 'personal'은 더 이상 쓰지 않지만 fromMap 호환을 위해 유지
  String get dbValue => switch (this) {
    MediaSpace.work => 'work',
    MediaSpace.secret => 'secret',
    MediaSpace.personal => 'secret', // 새 row는 항상 secret으로 저장
  };

  static MediaSpace parse(String? raw) => switch (raw) {
    'work' => MediaSpace.work,
    // legacy 'personal' 값도 secret으로 해석한다
    'personal' || 'secret' => MediaSpace.secret,
    _ => MediaSpace.work,
  };
}

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
  // OCR 추출 텍스트
  final String ocrText;
  // 작업(Job) 연결 — Work Space 전용
  final int? jobId;
  // Secret vault 암호화 여부 (1 = filePath/thumbPath가 .enc 파일)
  final int encrypted;

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
    this.ocrText = '',
    this.jobId,
    this.encrypted = 0,
  });

  bool get isEncrypted => encrypted == 1;

  factory MediaItem.fromMap(Map<String, dynamic> map) => MediaItem(
    id: map['id'] as int?,
    space: MediaSpaceX.parse(map['space'] as String?),
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
    ocrText: map['ocr_text'] as String? ?? '',
    jobId: map['job_id'] as int?,
    encrypted: map['encrypted'] as int? ?? 0,
  );

  Map<String, dynamic> toMap() => {
    if (id != null) 'id': id,
    'space': space.dbValue,
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
    'ocr_text': ocrText,
    'job_id': jobId,
    'encrypted': encrypted,
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
    String? ocrText,
    int? jobId,
    bool clearJobId = false,
    int? encrypted,
  }) => MediaItem(
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
    ocrText: ocrText ?? this.ocrText,
    jobId: clearJobId ? null : (jobId ?? this.jobId),
    encrypted: encrypted ?? this.encrypted,
  );
}
