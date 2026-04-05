import 'dart:io';

class MessengerApp {
  final String id;
  final String name;
  final String emoji;
  final List<String> scanPaths; // 우선순위 순

  const MessengerApp({
    required this.id,
    required this.name,
    required this.emoji,
    required this.scanPaths,
  });
}

class MessengerFile {
  final String path;
  final String name;
  final int sizeKb;
  final DateTime modifiedAt;
  final String type; // 'photo' | 'video' | 'document'
  final String messenger; // messenger app id

  const MessengerFile({
    required this.path,
    required this.name,
    required this.sizeKb,
    required this.modifiedAt,
    required this.type,
    required this.messenger,
  });
}

class MessengerScanService {
  static const _photoExts = {'.jpg', '.jpeg', '.png', '.gif', '.webp', '.heic'};
  static const _videoExts = {'.mp4', '.mov', '.avi', '.mkv', '.3gp', '.webm'};
  static const _docExts = {'.pdf', '.doc', '.docx', '.xls', '.xlsx', '.pptx'};

  static const List<MessengerApp> apps = [
    MessengerApp(
      id: 'kakaotalk',
      name: '카카오톡',
      emoji: '💬',
      scanPaths: [
        '/storage/emulated/0/KakaoTalk/Received/',
        '/storage/emulated/0/KakaoTalk/',
        '/storage/emulated/0/Android/media/com.kakao.talk/KakaoTalk/Received Files/',
        '/storage/emulated/0/Android/media/com.kakao.talk/KakaoTalk/',
      ],
    ),
    MessengerApp(
      id: 'line',
      name: '라인',
      emoji: '💚',
      scanPaths: [
        '/storage/emulated/0/LINE/',
        '/storage/emulated/0/Android/media/jp.naver.line.android/LINE/',
        '/storage/emulated/0/Android/media/com.linecorp.linelite/LINE/',
      ],
    ),
    MessengerApp(
      id: 'telegram',
      name: '텔레그램',
      emoji: '✈️',
      scanPaths: [
        '/storage/emulated/0/Telegram/',
        '/storage/emulated/0/Android/media/org.telegram.messenger/Telegram/',
        '/storage/emulated/0/Android/media/org.telegram.messenger.web/Telegram/',
      ],
    ),
    MessengerApp(
      id: 'wechat',
      name: '위챗',
      emoji: '🟢',
      scanPaths: [
        '/storage/emulated/0/tencent/MicroMsg/',
        '/storage/emulated/0/Android/media/com.tencent.mm/MicroMsg/',
      ],
    ),
    MessengerApp(
      id: 'whatsapp',
      name: 'WhatsApp',
      emoji: '📱',
      scanPaths: [
        '/storage/emulated/0/WhatsApp/Media/',
        '/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/',
      ],
    ),
  ];

  /// 특정 메신저의 파일 목록 스캔
  static Future<List<MessengerFile>> scan(MessengerApp app) async {
    final files = <MessengerFile>[];

    for (final basePath in app.scanPaths) {
      final dir = Directory(basePath);
      if (!dir.existsSync()) continue;

      await _scanDir(dir, app.id, files, depth: 0);
      if (files.isNotEmpty) break; // 파일 찾으면 다음 경로 탐색 중단
    }

    // 최신순 정렬
    files.sort((a, b) => b.modifiedAt.compareTo(a.modifiedAt));
    return files;
  }

  static Future<void> _scanDir(
    Directory dir,
    String messengerId,
    List<MessengerFile> out, {
    required int depth,
  }) async {
    if (depth > 3) return; // 최대 3단계 깊이
    try {
      await for (final entity in dir.list(followLinks: false)) {
        if (entity is Directory) {
          await _scanDir(entity, messengerId, out, depth: depth + 1);
        } else if (entity is File) {
          final file = _toMessengerFile(entity, messengerId);
          if (file != null) out.add(file);
        }
      }
    } catch (_) {}
  }

  static MessengerFile? _toMessengerFile(File file, String messengerId) {
    try {
      final name = file.path.split('/').last;
      final ext = '.${name.split('.').last.toLowerCase()}';

      String type;
      if (_photoExts.contains(ext)) {
        type = 'photo';
      } else if (_videoExts.contains(ext)) {
        type = 'video';
      } else if (_docExts.contains(ext)) {
        type = 'document';
      } else {
        return null; // 지원하지 않는 형식
      }

      final stat = file.statSync();
      final sizeKb = (stat.size / 1024).ceil();

      return MessengerFile(
        path: file.path,
        name: name,
        sizeKb: sizeKb,
        modifiedAt: stat.modified,
        type: type,
        messenger: messengerId,
      );
    } catch (_) {
      return null;
    }
  }

  /// 설치된 메신저 중 파일이 있는 것만 반환
  static Future<List<MessengerApp>> detectInstalled() async {
    final installed = <MessengerApp>[];
    for (final app in apps) {
      for (final path in app.scanPaths) {
        if (Directory(path).existsSync()) {
          installed.add(app);
          break;
        }
      }
    }
    return installed;
  }
}
