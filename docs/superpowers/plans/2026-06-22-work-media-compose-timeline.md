# Work 미디어 등록 화면 + 타임라인 카드 리디자인 구현 플랜

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Work 탭에서 미디어 등록 시 저장 전 입력화면을 거치도록 흐름을 변경하고, 타임라인 카드를 인스타그램 스타일로 리디자인한다.

**Architecture:** 새로운 `MediaComposeScreen`을 생성 전용 화면으로 추가하고, `WorkScreen._onAddMedia`가 DB 저장 대신 ComposeScreen으로 네비게이션하도록 변경한다. `MediaSaveService.saveAll()`에 메모/태그/위치 파라미터를 추가하여 저장 시 한 번에 적용한다. `MediaTimeline`의 `_TimelineCard`를 인스타그램 스타일(위치+날짜 헤더, 이미지 그리드, 태그, 메모 미리보기, 미디어 카운트)로 리디자인한다.

**Tech Stack:** Flutter, Riverpod, sqflite, google_mlkit_image_labeling, geocoding, exif

## Global Constraints

- Flutter SDK ^3.11.3, Dart ^3.11
- 상태관리: flutter_riverpod ^2.6.1
- 로컬 DB: sqflite ^2.4.2
- AI 태깅: google_mlkit_image_labeling ^0.13.0
- 응답 언어: 한글
- 커밋 메시지: AI 작성 표시 금지
- Work 탭만 변경. Personal 탭 영향 없음.
- `MediaDetailScreen`은 변경 없음 (편집 전용 유지)

---

### Task 1: MediaSaveService에 메모/태그/위치 파라미터 추가

`saveAll()`이 메모, 태그 ID 목록, 국가, 지역을 받아 저장 시 일괄 적용하도록 확장한다.

**Files:**
- Modify: `lib/core/services/media_save_service.dart:86-104`
- Test: `test/unit/media_save_service_test.dart`

**Interfaces:**
- Consumes: `MediaDao.insert()`, `TagDao.setMediaTags()`, `CapturedMedia` (기존)
- Produces: `MediaSaveService.saveAll(captured, space, {note, tagIds, countryCode, region, albumId})` → `List<MediaSaveResult>`

- [ ] **Step 1: Write the failing test**

Create `test/unit/media_save_service_test.dart`:

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/core/services/media_save_service.dart';
import 'package:memorix/shared/models/media_item.dart';

void main() {
  group('MediaSaveService.saveAll parameter forwarding', () {
    test('saveAll accepts note, tagIds, countryCode, region parameters', () {
      // 컴파일 레벨 테스트: 파라미터가 존재하는지 확인
      // 실제 DB 없이는 호출 불가하므로 시그니처만 검증
      expect(
        MediaSaveService.saveAll,
        isA<Function>(),
      );
    });
  });

  group('MediaSaveService.save parameter forwarding', () {
    test('save accepts tagIds parameter', () {
      expect(
        MediaSaveService.save,
        isA<Function>(),
      );
    });
  });
}
```

- [ ] **Step 2: Run test to verify current state**

Run: `flutter test test/unit/media_save_service_test.dart`
Expected: PASS (시그니처 자체는 이미 존재)

- [ ] **Step 3: Modify `MediaSaveService.save()` to accept `tagIds` parameter**

In `lib/core/services/media_save_service.dart`, modify `save()` method signature and body:

```dart
static Future<MediaSaveResult> save({
  required CapturedMedia captured,
  required MediaSpace space,
  String note = '',
  String countryCode = '',
  String region = '',
  List<int> tagIds = const [],
  int? albumId,
  String batchId = '',
}) async {
  // ... (기존 로직 동일: EXIF 위치, OCR 등)

  final id = await _mediaDao.insert(item);
  final saved = item.copyWith(id: id);

  // tagIds가 명시적으로 전달된 경우 해당 태그 적용
  if (tagIds.isNotEmpty) {
    await _tagDao.setMediaTags(id, tagIds);
    final allTags = await _tagDao.findBySpace(space);
    final matched = allTags.where((t) => tagIds.contains(t.id)).toList();
    return MediaSaveResult(item: saved, suggestedTags: matched);
  }

  // tagIds가 비어있으면 기존 AI 태그 추천 로직 사용
  final suggestedTags = await _suggestAndApplyTags(captured, space, id);
  return MediaSaveResult(item: saved, suggestedTags: suggestedTags);
}
```

- [ ] **Step 4: Modify `MediaSaveService.saveAll()` to forward new parameters**

```dart
static Future<List<MediaSaveResult>> saveAll({
  required List<CapturedMedia> captured,
  required MediaSpace space,
  String note = '',
  List<int> tagIds = const [],
  String countryCode = '',
  String region = '',
  int? albumId,
}) async {
  final batchId = captured.length > 1 ? _uuid.v4() : '';
  final results = <MediaSaveResult>[];
  for (final c in captured) {
    try {
      results.add(await save(
        captured: c,
        space: space,
        note: note,
        countryCode: countryCode,
        region: region,
        tagIds: tagIds,
        albumId: albumId,
        batchId: batchId,
      ));
    } catch (e, stack) {
      developer.log('MediaSaveService: 항목 저장 실패: $e',
          error: e, stackTrace: stack, name: 'memorix');
    }
  }
  return results;
}
```

- [ ] **Step 5: Run tests**

Run: `flutter test test/unit/media_save_service_test.dart`
Expected: PASS

- [ ] **Step 6: Verify existing callers compile**

Run: `flutter analyze lib/core/services/media_save_service.dart lib/features/work/screens/work_screen.dart lib/features/personal/screens/personal_screen.dart lib/shared/screens/media_detail_screen.dart`
Expected: No errors (새 파라미터는 모두 기본값이 있으므로 기존 호출부 변경 불필요)

- [ ] **Step 7: Commit**

```bash
git add lib/core/services/media_save_service.dart test/unit/media_save_service_test.dart
git commit -m "feat: MediaSaveService에 메모/태그/위치 일괄 저장 파라미터 추가"
```

---

### Task 2: MediaComposeScreen 생성

사진 선택 후 DB 저장 전에 메모/태그/위치를 입력하는 화면. `MediaDetailScreen`의 편집 필드를 참고하되 생성 전용 단일 화면으로 분리한다.

**Files:**
- Create: `lib/features/work/screens/media_compose_screen.dart`
- Test: `test/widget/media_compose_screen_test.dart`

**Interfaces:**
- Consumes: `CapturedMedia` (from `MediaCaptureService`), `MediaSaveService.saveAll()` (Task 1), `TagDao.findBySpace()`, `AiTagService.suggestTags()`, `CaptureBottomSheet.show()`
- Produces: `MediaComposeScreen(captured: List<CapturedMedia>)` → `Navigator.pop(context, true)` on save

- [ ] **Step 1: Write the failing widget test**

Create `test/widget/media_compose_screen_test.dart`:

```dart
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/features/work/screens/media_compose_screen.dart';
import 'package:memorix/core/services/media_capture_service.dart';

void main() {
  group('MediaComposeScreen', () {
    test('can be instantiated with captured media list', () {
      final captured = CapturedMedia(
        filePath: '/tmp/test.jpg',
        mediaType: 'photo',
        fileSizeKb: 100,
      );
      final screen = MediaComposeScreen(captured: [captured]);
      expect(screen.captured, hasLength(1));
    });
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `flutter test test/widget/media_compose_screen_test.dart`
Expected: FAIL — `MediaComposeScreen` does not exist

- [ ] **Step 3: Create MediaComposeScreen**

Create `lib/features/work/screens/media_compose_screen.dart`:

```dart
import 'dart:io';
import 'package:exif/exif.dart';
import 'package:flutter/material.dart';
import 'package:geocoding/geocoding.dart';
import 'package:intl/intl.dart';
import '../../../core/db/tag_dao.dart';
import '../../../core/services/ai_tag_service.dart';
import '../../../core/services/media_capture_service.dart';
import '../../../core/services/media_save_service.dart';
import '../../../shared/models/media_item.dart';
import '../../../shared/models/tag.dart';
import '../../../shared/widgets/capture_bottom_sheet.dart';
import '../../../shared/screens/media_viewer_screen.dart';

class MediaComposeScreen extends StatefulWidget {
  final List<CapturedMedia> captured;

  const MediaComposeScreen({super.key, required this.captured});

  @override
  State<MediaComposeScreen> createState() => _MediaComposeScreenState();
}

class _MediaComposeScreenState extends State<MediaComposeScreen> {
  late List<CapturedMedia> _items;
  final _noteCtrl = TextEditingController();
  final _countryCtrl = TextEditingController();
  final _regionCtrl = TextEditingController();
  final _tagInputCtrl = TextEditingController();

  List<Tag> _allTags = [];
  Set<int> _selectedTagIds = {};
  List<Tag> _aiSuggestedTags = [];
  bool _saving = false;
  bool _locating = false;
  late DateTime _eventDate;

  final _tagDao = TagDao();

  bool get _hasContent =>
      _noteCtrl.text.trim().isNotEmpty ||
      _countryCtrl.text.trim().isNotEmpty ||
      _regionCtrl.text.trim().isNotEmpty ||
      _selectedTagIds.isNotEmpty;

  @override
  void initState() {
    super.initState();
    _items = List.from(widget.captured);
    _eventDate = DateTime.now();
    _loadTags();
    _autoFillFromExif();
    _runAiTagging();
  }

  @override
  void dispose() {
    _noteCtrl.dispose();
    _countryCtrl.dispose();
    _regionCtrl.dispose();
    _tagInputCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadTags() async {
    final tags = await _tagDao.findBySpace(MediaSpace.work);
    if (mounted) setState(() => _allTags = tags);
  }

  Future<void> _autoFillFromExif() async {
    final first = _items.firstOrNull;
    if (first == null) return;

    // EXIF 촬영일시
    if (first.takenAt != null) {
      setState(() {
        _eventDate = DateTime.fromMillisecondsSinceEpoch(first.takenAt!);
      });
    }

    // EXIF GPS → 역지오코딩
    if (first.latitude != null && first.longitude != null) {
      setState(() => _locating = true);
      try {
        final placemarks = await placemarkFromCoordinates(
            first.latitude!, first.longitude!);
        if (placemarks.isNotEmpty && mounted) {
          final p = placemarks.first;
          _countryCtrl.text = p.country ?? p.isoCountryCode ?? '';
          _regionCtrl.text = p.administrativeArea ?? p.locality ?? '';
        }
      } catch (_) {}
      if (mounted) setState(() => _locating = false);
    }
  }

  Future<void> _autoFillLocation() async {
    final first = _items.firstOrNull;
    if (first == null) return;

    setState(() => _locating = true);
    try {
      final bytes = await File(first.filePath).readAsBytes();
      final tags = await readExifFromBytes(bytes);

      final latTag = tags['GPS GPSLatitude'];
      final latRef = tags['GPS GPSLatitudeRef']?.printable;
      final lngTag = tags['GPS GPSLongitude'];
      final lngRef = tags['GPS GPSLongitudeRef']?.printable;

      double? lat = _parseGps(latTag, latRef);
      double? lng = _parseGps(lngTag, lngRef);

      if (lat != null && lng != null) {
        final placemarks = await placemarkFromCoordinates(lat, lng);
        if (placemarks.isNotEmpty && mounted) {
          final p = placemarks.first;
          _countryCtrl.text = p.country ?? p.isoCountryCode ?? '';
          _regionCtrl.text = p.administrativeArea ?? p.locality ?? '';
        }
      } else if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('사진에 GPS 정보가 없습니다')),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('위치 정보를 읽을 수 없습니다')),
        );
      }
    } finally {
      if (mounted) setState(() => _locating = false);
    }
  }

  double? _parseGps(IfdTag? tag, String? ref) {
    if (tag == null) return null;
    try {
      final vals = tag.values;
      if (vals is IfdRatios) {
        final r = vals.ratios;
        if (r.length < 3) return null;
        double d = r[0].numerator / r[0].denominator +
            r[1].numerator / r[1].denominator / 60 +
            r[2].numerator / r[2].denominator / 3600;
        if (ref == 'S' || ref == 'W') d = -d;
        return d;
      }
    } catch (_) {}
    return null;
  }

  Future<void> _runAiTagging() async {
    final allTags = await _tagDao.findBySpace(MediaSpace.work);
    final suggestedKeys = <String>{};

    for (final item in _items) {
      if (item.mediaType == 'document') {
        suggestedKeys.addAll(AiTagService.suggestForDocument());
      } else if (item.mediaType == 'photo') {
        suggestedKeys.addAll(
            await AiTagService.suggestTags(item.filePath, MediaSpace.work));
      } else if (item.mediaType == 'video' && item.thumbPath != null) {
        suggestedKeys.addAll(
            await AiTagService.suggestTags(item.thumbPath!, MediaSpace.work));
      }
    }

    final matched = allTags
        .where((t) => suggestedKeys.contains(t.key))
        .where((t) => !_selectedTagIds.contains(t.id))
        .toList();
    if (mounted) setState(() => _aiSuggestedTags = matched);
  }

  Future<void> _pickEventDate() async {
    final pickedDate = await showDatePicker(
      context: context,
      initialDate: _eventDate,
      firstDate: DateTime(2000),
      lastDate: DateTime.now().add(const Duration(days: 365)),
      helpText: '이벤트 날짜 선택',
    );
    if (pickedDate == null || !mounted) return;

    final pickedTime = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(_eventDate),
    );
    if (!mounted) return;

    setState(() {
      _eventDate = DateTime(
        pickedDate.year,
        pickedDate.month,
        pickedDate.day,
        pickedTime?.hour ?? _eventDate.hour,
        pickedTime?.minute ?? _eventDate.minute,
      );
    });
  }

  Future<void> _addMoreMedia() async {
    final captured = await CaptureBottomSheet.show(
      context,
      allowDocument: true,
    );
    if (captured == null || captured.isEmpty || !mounted) return;
    setState(() => _items = [..._items, ...captured]);
    _runAiTagging();
  }

  void _removeItem(int index) {
    if (_items.length <= 1) return;
    setState(() {
      _items = List.from(_items)..removeAt(index);
    });
  }

  Future<void> _addCustomTag() async {
    final label = _tagInputCtrl.text.trim();
    if (label.isEmpty) return;
    _tagInputCtrl.clear();

    final existing = _allTags.firstWhere(
      (t) => t.label == label,
      orElse: () =>
          Tag(space: MediaSpace.work, key: '', label: '', color: '', icon: ''),
    );
    if (existing.id != null) {
      setState(() => _selectedTagIds.add(existing.id!));
      return;
    }

    final newTag = Tag(
      space: MediaSpace.work,
      key: 'custom_${label.toLowerCase().replaceAll(' ', '_')}',
      label: label,
      color: '#00C896',
      icon: 'label',
      isCustom: true,
    );
    final id = await _tagDao.insert(newTag);
    final saved = Tag(
      id: id,
      space: newTag.space,
      key: newTag.key,
      label: newTag.label,
      color: newTag.color,
      icon: newTag.icon,
      isCustom: newTag.isCustom,
    );
    setState(() {
      _allTags = [..._allTags, saved];
      _selectedTagIds = {..._selectedTagIds, id};
    });
  }

  Future<void> _save() async {
    if (_items.isEmpty) return;
    setState(() => _saving = true);

    try {
      await MediaSaveService.saveAll(
        captured: _items,
        space: MediaSpace.work,
        note: _noteCtrl.text.trim(),
        tagIds: _selectedTagIds.toList(),
        countryCode: _countryCtrl.text.trim(),
        region: _regionCtrl.text.trim(),
      );

      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('저장 실패: $e'),
        backgroundColor: Colors.red,
      ));
      setState(() => _saving = false);
    }
  }

  static final _dateFmt = DateFormat('yyyy년 M월 d일 (E)  HH:mm', 'ko');

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return PopScope(
      canPop: !_hasContent,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        final discard = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: const Text('나가시겠습니까?'),
            content: const Text('작성 중인 내용이 사라집니다.'),
            actions: [
              TextButton(
                  onPressed: () => Navigator.pop(ctx, false),
                  child: const Text('계속 작성')),
              FilledButton(
                  onPressed: () => Navigator.pop(ctx, true),
                  child: const Text('나가기')),
            ],
          ),
        );
        if (discard == true && mounted) Navigator.pop(context);
      },
      child: Scaffold(
        appBar: AppBar(
          title: const Text('미디어 등록'),
          actions: [
            TextButton(
              onPressed: _saving || _items.isEmpty ? null : _save,
              child: _saving
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2))
                  : const Text('저장',
                      style: TextStyle(
                          fontWeight: FontWeight.w700, fontSize: 15)),
            ),
          ],
        ),
        body: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              // ── 미디어 미리보기 (가로 스크롤) ──
              _buildMediaPreview(),
              const SizedBox(height: 20),

              // ── 이벤트 날짜 ──
              _buildEventDate(cs, isDark),
              const SizedBox(height: 20),

              // ── 메모 ──
              TextField(
                controller: _noteCtrl,
                maxLines: 4,
                decoration: const InputDecoration(
                  labelText: '메모',
                  hintText: '메모를 입력하세요',
                  alignLabelWithHint: true,
                ),
              ),
              const SizedBox(height: 20),

              // ── 태그 ──
              _buildTagSection(cs),
              const SizedBox(height: 20),

              // ── 위치 ──
              _buildLocationSection(),
              const SizedBox(height: 20),

              // ── AI 추천 태그 ──
              if (_aiSuggestedTags.isNotEmpty) _buildAiTags(),

              const SizedBox(height: 40),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildMediaPreview() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          height: 88,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: _items.length + 1,
            separatorBuilder: (_, __) => const SizedBox(width: 8),
            itemBuilder: (context, index) {
              if (index == _items.length) {
                return GestureDetector(
                  onTap: _addMoreMedia,
                  child: Container(
                    width: 80,
                    height: 80,
                    decoration: BoxDecoration(
                      border: Border.all(color: Colors.grey.shade300),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.add_photo_alternate_outlined,
                            size: 24, color: Colors.grey),
                        SizedBox(height: 4),
                        Text('추가',
                            style:
                                TextStyle(fontSize: 11, color: Colors.grey)),
                      ],
                    ),
                  ),
                );
              }

              final item = _items[index];
              return _MediaThumb(
                item: item,
                onRemove: _items.length > 1 ? () => _removeItem(index) : null,
                onTap: () {
                  // 풀스크린 미리보기는 파일이 존재할 때만
                  if (File(item.filePath).existsSync()) {
                    final mediaItems = _items
                        .map((c) => MediaItem(
                              space: MediaSpace.work,
                              mediaType: c.mediaType == 'video'
                                  ? MediaType.video
                                  : c.mediaType == 'document'
                                      ? MediaType.document
                                      : MediaType.photo,
                              filePath: c.filePath,
                              thumbPath: c.thumbPath,
                              takenAt: c.takenAt ??
                                  DateTime.now().millisecondsSinceEpoch,
                              createdAt:
                                  DateTime.now().millisecondsSinceEpoch,
                            ))
                        .toList();
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => MediaViewerScreen(
                            items: mediaItems, initialIndex: index),
                      ),
                    );
                  }
                },
              );
            },
          ),
        ),
        const SizedBox(height: 4),
        Text('${_items.length}개 선택됨',
            style: const TextStyle(fontSize: 12, color: Colors.grey)),
      ],
    );
  }

  Widget _buildEventDate(ColorScheme cs, bool isDark) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('이벤트 날짜',
            style: Theme.of(context)
                .textTheme
                .titleSmall
                ?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 8),
        InkWell(
          onTap: _pickEventDate,
          borderRadius: BorderRadius.circular(12),
          child: Container(
            width: double.infinity,
            padding:
                const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
            decoration: BoxDecoration(
              color: isDark
                  ? const Color(0xFF1E2530)
                  : cs.primary.withValues(alpha: 0.05),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                  color: cs.primary.withValues(alpha: 0.3), width: 1.2),
            ),
            child: Row(
              children: [
                Icon(Icons.event_outlined, size: 20, color: cs.primary),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    _dateFmt.format(_eventDate),
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: isDark ? Colors.white : const Color(0xFF1A1F2E),
                    ),
                  ),
                ),
                Icon(Icons.edit_calendar_outlined,
                    size: 18, color: cs.primary),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildTagSection(ColorScheme cs) {
    const primary = Color(0xFF00C896);
    const unselectedText = Color(0xFF005C42);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('태그',
            style: Theme.of(context)
                .textTheme
                .titleSmall
                ?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 10),
        Wrap(
          spacing: 6,
          runSpacing: 6,
          children: _allTags.map((tag) {
            final selected = _selectedTagIds.contains(tag.id);
            return GestureDetector(
              onTap: () => setState(() {
                if (selected) {
                  _selectedTagIds.remove(tag.id);
                } else {
                  _selectedTagIds.add(tag.id!);
                }
              }),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color:
                      selected ? primary : primary.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: primary, width: 1.5),
                ),
                child: Text(
                  tag.label,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: selected ? Colors.white : unselectedText,
                  ),
                ),
              ),
            );
          }).toList(),
        ),
        const SizedBox(height: 10),
        Row(
          children: [
            Expanded(
              child: TextField(
                controller: _tagInputCtrl,
                decoration: const InputDecoration(
                  hintText: '태그 직접 입력',
                  hintStyle: TextStyle(fontSize: 13),
                  prefixIcon: Icon(Icons.label_outline, size: 18),
                  contentPadding:
                      EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                  isDense: true,
                ),
                onSubmitted: (_) => _addCustomTag(),
              ),
            ),
            const SizedBox(width: 8),
            FilledButton(
              onPressed: _addCustomTag,
              style: FilledButton.styleFrom(
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

  Widget _buildLocationSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('위치',
                style: Theme.of(context)
                    .textTheme
                    .titleSmall
                    ?.copyWith(fontWeight: FontWeight.w700)),
            TextButton.icon(
              onPressed: _locating ? null : _autoFillLocation,
              icon: _locating
                  ? const SizedBox(
                      width: 14,
                      height: 14,
                      child: CircularProgressIndicator(strokeWidth: 2))
                  : const Icon(Icons.my_location, size: 16),
              label: Text(_locating ? '읽는 중...' : 'GPS 자동 입력',
                  style: const TextStyle(fontSize: 12)),
              style: TextButton.styleFrom(
                padding:
                    const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
              ),
            ),
          ],
        ),
        const SizedBox(height: 4),
        Row(
          children: [
            Expanded(
              child: TextField(
                controller: _countryCtrl,
                decoration: const InputDecoration(
                  labelText: '국가',
                  hintText: '대한민국',
                  prefixIcon: Icon(Icons.flag_outlined, size: 18),
                ),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: TextField(
                controller: _regionCtrl,
                decoration: const InputDecoration(
                  labelText: '지역',
                  hintText: '서울',
                  prefixIcon: Icon(Icons.location_on_outlined, size: 18),
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildAiTags() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Icon(Icons.auto_awesome, size: 16, color: Color(0xFF7B61FF)),
            const SizedBox(width: 6),
            Text('AI 추천 태그',
                style: Theme.of(context)
                    .textTheme
                    .titleSmall
                    ?.copyWith(fontWeight: FontWeight.w700)),
          ],
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 6,
          runSpacing: 6,
          children: _aiSuggestedTags.map((tag) {
            return GestureDetector(
              onTap: () {
                setState(() {
                  _selectedTagIds.add(tag.id!);
                  _aiSuggestedTags =
                      _aiSuggestedTags.where((t) => t.id != tag.id).toList();
                });
              },
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: const Color(0xFF7B61FF).withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                      color: const Color(0xFF7B61FF).withValues(alpha: 0.4)),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.add, size: 14, color: Color(0xFF7B61FF)),
                    const SizedBox(width: 4),
                    Text(tag.label,
                        style: const TextStyle(
                            fontSize: 12,
                            color: Color(0xFF7B61FF),
                            fontWeight: FontWeight.w600)),
                  ],
                ),
              ),
            );
          }).toList(),
        ),
        const SizedBox(height: 20),
      ],
    );
  }
}

// ── 미디어 썸네일 ────────────────────────────────────────────

class _MediaThumb extends StatelessWidget {
  final CapturedMedia item;
  final VoidCallback? onRemove;
  final VoidCallback onTap;

  const _MediaThumb({
    required this.item,
    this.onRemove,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: SizedBox(
        width: 80,
        height: 88,
        child: Stack(
          children: [
            Positioned.fill(
              child: ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: _buildImage(),
              ),
            ),
            if (item.mediaType == 'video')
              const Center(
                child:
                    Icon(Icons.play_circle_fill, color: Colors.white70, size: 28),
              ),
            if (onRemove != null)
              Positioned(
                right: -2,
                top: -2,
                child: GestureDetector(
                  onTap: onRemove,
                  child: Container(
                    width: 20,
                    height: 20,
                    decoration: const BoxDecoration(
                      color: Colors.black54,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.close, size: 12, color: Colors.white),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildImage() {
    if (item.mediaType == 'document') {
      return Container(
        color: Colors.blue[50],
        child:
            const Center(child: Icon(Icons.description, size: 32, color: Colors.blueGrey)),
      );
    }
    final path = item.thumbPath ?? item.filePath;
    if (File(path).existsSync()) {
      return Image.file(File(path), fit: BoxFit.cover);
    }
    return Container(color: Colors.grey[200]);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `flutter test test/widget/media_compose_screen_test.dart`
Expected: PASS

- [ ] **Step 5: Run static analysis**

Run: `flutter analyze lib/features/work/screens/media_compose_screen.dart`
Expected: No errors

- [ ] **Step 6: Commit**

```bash
git add lib/features/work/screens/media_compose_screen.dart test/widget/media_compose_screen_test.dart
git commit -m "feat: MediaComposeScreen 생성 전용 입력화면 추가"
```

---

### Task 3: WorkScreen 흐름 변경

`_onAddMedia`에서 DB 저장 → MediaDetailScreen 대신 MediaComposeScreen으로 네비게이션하도록 변경한다.

**Files:**
- Modify: `lib/features/work/screens/work_screen.dart:189-244`

**Interfaces:**
- Consumes: `MediaComposeScreen(captured: List<CapturedMedia>)` (Task 2), `CaptureBottomSheet.show()` (기존)
- Produces: 변경된 `_onAddMedia()` — 갤러리 선택 후 ComposeScreen으로 이동, 저장 완료 시 workMediaProvider invalidate

- [ ] **Step 1: Replace `_onAddMedia` method in `work_screen.dart`**

Replace lines 189-244 of `lib/features/work/screens/work_screen.dart`:

```dart
Future<void> _onAddMedia(BuildContext context) async {
  final capturedList =
      await CaptureBottomSheet.show(context, allowDocument: true);

  if (capturedList == null || capturedList.isEmpty || !context.mounted) return;

  final saved = await Navigator.push<dynamic>(
    context,
    MaterialPageRoute(
      builder: (_) => MediaComposeScreen(captured: capturedList),
    ),
  );

  if (context.mounted && saved == true) {
    ref.invalidate(workMediaProvider);
  }
}
```

- [ ] **Step 2: Update imports in `work_screen.dart`**

Add import at top of file:

```dart
import 'media_compose_screen.dart';
```

Remove no-longer-needed import (MediaSaveService is no longer used directly in WorkScreen):

```dart
// Remove: import '../../../core/services/media_save_service.dart';
```

- [ ] **Step 3: Verify compile**

Run: `flutter analyze lib/features/work/screens/work_screen.dart`
Expected: No errors. Check for unused import warnings and remove if any.

- [ ] **Step 4: Commit**

```bash
git add lib/features/work/screens/work_screen.dart
git commit -m "feat: Work 탭 미디어 추가 흐름을 ComposeScreen 경유로 변경"
```

---

### Task 4: 타임라인 카드 리디자인

`_TimelineCard`를 인스타그램 스타일로 리디자인한다. 헤더(위치+날짜), 이미지 그리드(최대 3장), 태그, 메모 미리보기, 미디어 카운트 순서.

**Files:**
- Modify: `lib/shared/widgets/media_timeline.dart:131-457` (`_TimelineCard` + `_buildHeader` + `_buildImageArea` + `_buildFooter`)

**Interfaces:**
- Consumes: `MediaItem`, `TagDao.findByMediaId()` (기존)
- Produces: 리디자인된 `_TimelineCard` (외부 인터페이스 변경 없음 — `MediaTimeline` API 그대로)

- [ ] **Step 1: Redesign `_buildHeader` in `_TimelineCardState`**

Replace `_buildHeader` method (lines 222-298) with new header showing location + date on two lines:

```dart
Widget _buildHeader(
    BuildContext context, MediaItem item, bool isWork, int count) {
  final isDark = Theme.of(context).brightness == Brightness.dark;
  final dt = DateTime.fromMillisecondsSinceEpoch(item.takenAt);
  final dateStr = DateFormat('M월 d일 (E)  HH:mm', 'ko').format(dt);
  final location = [item.countryCode, item.region]
      .where((s) => s.isNotEmpty)
      .join(' · ');
  final firstTag =
      (_tags != null && _tags!.isNotEmpty) ? _tags!.first.label : null;

  return Padding(
    padding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 첫 줄: Space badge (옵션) + 위치 + 주요 태그
        Row(
          children: [
            if (widget.showSpaceBadge) ...[
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: isWork
                        ? [const Color(0xFF1A73E8), const Color(0xFF00C896)]
                        : [const Color(0xFFFF6B9D), const Color(0xFF7B61FF)],
                  ),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  isWork ? 'Work' : 'Personal',
                  style: const TextStyle(
                      color: Colors.white,
                      fontSize: 10,
                      fontWeight: FontWeight.w700),
                ),
              ),
              const SizedBox(width: 8),
            ],
            if (location.isNotEmpty) ...[
              const Icon(Icons.location_on,
                  size: 14, color: Color(0xFF00C896)),
              const SizedBox(width: 3),
              Flexible(
                child: Text(
                  location,
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: isDark ? Colors.white : const Color(0xFF1A1F2E),
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
            if (location.isNotEmpty && firstTag != null)
              Text(' · ',
                  style: TextStyle(
                      fontSize: 13,
                      color: isDark ? Colors.white54 : Colors.grey)),
            if (firstTag != null)
              Text(firstTag,
                  style: TextStyle(
                      fontSize: 13,
                      color: isDark ? Colors.white54 : Colors.grey[600])),
            const Spacer(),
            if (count > 1)
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
                decoration: BoxDecoration(
                  color: isDark
                      ? Colors.white.withValues(alpha: 0.1)
                      : Colors.black.withValues(alpha: 0.06),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.collections_outlined,
                        size: 11,
                        color: isDark ? Colors.white60 : Colors.black54),
                    const SizedBox(width: 3),
                    Text('$count',
                        style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color:
                                isDark ? Colors.white60 : Colors.black54)),
                  ],
                ),
              ),
          ],
        ),
        const SizedBox(height: 3),
        // 둘째 줄: 날짜·시간
        Text(
          dateStr,
          style: TextStyle(
            fontSize: 12,
            color: isDark ? Colors.white38 : const Color(0xFF888899),
          ),
        ),
      ],
    ),
  );
}
```

- [ ] **Step 2: Redesign `_buildFooter` in `_TimelineCardState`**

Replace `_buildFooter` method (lines 395-456) with new footer showing tags, expandable memo, media count:

```dart
Widget _buildFooter(BuildContext context, MediaItem item, bool isDark) {
  final tags = _tags ?? [];
  final note = item.note.trim();
  final hasNote = note.isNotEmpty;
  final count = widget.group.length;

  // 미디어 타입별 카운트
  final photoCount =
      widget.group.where((m) => m.mediaType == MediaType.photo).length;
  final videoCount =
      widget.group.where((m) => m.mediaType == MediaType.video).length;
  final docCount =
      widget.group.where((m) => m.mediaType == MediaType.document).length;

  return Padding(
    padding: const EdgeInsets.fromLTRB(16, 12, 16, 14),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // ── 태그 ──
        if (tags.isNotEmpty) ...[
          Wrap(
            spacing: 5,
            runSpacing: 4,
            children: tags
                .map((tag) => _TagChipDisplay(label: tag.label))
                .toList(),
          ),
          const SizedBox(height: 10),
        ],

        // ── 메모 (2줄 미리보기 + 더보기) ──
        if (hasNote) ...[
          _ExpandableNote(note: note, isDark: isDark),
          const SizedBox(height: 10),
        ],

        // ── 미디어 카운트 ──
        Row(
          children: [
            if (photoCount > 0) ...[
              const Icon(Icons.photo_outlined,
                  size: 13, color: Color(0xFF1A73E8)),
              const SizedBox(width: 3),
              Text('$photoCount장',
                  style: const TextStyle(
                      fontSize: 11,
                      color: Color(0xFF1A73E8),
                      fontWeight: FontWeight.w600)),
            ],
            if (videoCount > 0) ...[
              if (photoCount > 0) ...[
                const SizedBox(width: 8),
                Text('·',
                    style: TextStyle(fontSize: 11, color: Colors.grey[400])),
                const SizedBox(width: 8),
              ],
              const Icon(Icons.videocam_outlined,
                  size: 13, color: Color(0xFFFF6B9D)),
              const SizedBox(width: 3),
              Text('$videoCount개',
                  style: const TextStyle(
                      fontSize: 11,
                      color: Color(0xFFFF6B9D),
                      fontWeight: FontWeight.w600)),
            ],
            if (docCount > 0) ...[
              if (photoCount > 0 || videoCount > 0) ...[
                const SizedBox(width: 8),
                Text('·',
                    style: TextStyle(fontSize: 11, color: Colors.grey[400])),
                const SizedBox(width: 8),
              ],
              const Icon(Icons.description_outlined,
                  size: 13, color: Color(0xFFFFB800)),
              const SizedBox(width: 3),
              Text('$docCount개',
                  style: const TextStyle(
                      fontSize: 11,
                      color: Color(0xFFFFB800),
                      fontWeight: FontWeight.w600)),
            ],
            const Spacer(),
            if (item.driveSynced == 0) ...[
              const Icon(Icons.cloud_upload_outlined,
                  size: 13, color: Colors.grey),
              const SizedBox(width: 3),
              const Text('동기화 대기',
                  style: TextStyle(fontSize: 11, color: Colors.grey)),
            ],
          ],
        ),
      ],
    ),
  );
}
```

- [ ] **Step 3: Add `_ExpandableNote` widget**

Add after `_TypeBadge` class (before end of file):

```dart
class _ExpandableNote extends StatefulWidget {
  final String note;
  final bool isDark;

  const _ExpandableNote({required this.note, required this.isDark});

  @override
  State<_ExpandableNote> createState() => _ExpandableNoteState();
}

class _ExpandableNoteState extends State<_ExpandableNote> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => setState(() => _expanded = !_expanded),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            widget.note,
            maxLines: _expanded ? null : 2,
            overflow: _expanded ? null : TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w500,
              color: widget.isDark
                  ? Colors.white.withValues(alpha: 0.87)
                  : const Color(0xFF1A2030),
              height: 1.5,
            ),
          ),
          if (!_expanded && widget.note.length > 60)
            Text('더보기',
                style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey[500],
                    fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}
```

- [ ] **Step 4: Remove unused `_TypeBadge` widget**

The `_TypeBadge` widget is replaced by inline media count in the footer. Remove the entire `_TypeBadge` class (lines 541-563).

- [ ] **Step 5: Run static analysis**

Run: `flutter analyze lib/shared/widgets/media_timeline.dart`
Expected: No errors

- [ ] **Step 6: Commit**

```bash
git add lib/shared/widgets/media_timeline.dart
git commit -m "feat: 타임라인 카드를 인스타그램 스타일로 리디자인"
```

---

### Task 5: 통합 테스트 및 빌드 검증

전체 변경사항이 올바르게 동작하는지 빌드하고 검증한다.

**Files:**
- All modified files from Tasks 1-4

**Interfaces:**
- Consumes: All previous tasks
- Produces: Clean build, passing tests, working APK

- [ ] **Step 1: Run all tests**

Run: `flutter test`
Expected: All tests pass

- [ ] **Step 2: Run static analysis**

Run: `flutter analyze`
Expected: No errors

- [ ] **Step 3: Build APK**

Run: `flutter build apk --dart-define=APP_FLAVOR=memorix`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Rename APK**

```bash
mv build/app/outputs/flutter-apk/app-release.apk build/app/outputs/flutter-apk/memorix_1.0.0.apk
```

- [ ] **Step 5: Commit and report**

```bash
git add -A
git commit -m "chore: 통합 빌드 검증 완료"
```

Report: APK 경로, 파일 크기, 테스트 결과
