import 'dart:io';
import 'package:country_picker/country_picker.dart';
import 'package:exif/exif.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geocoding/geocoding.dart';
import 'package:intl/intl.dart';
import 'package:uuid/uuid.dart';
import 'package:flutter/services.dart';
import '../../core/db/media_dao.dart';
import '../../core/db/tag_dao.dart';
import '../../core/db/people_dao.dart';
import '../../core/services/country_default_service.dart';
import '../../core/services/ocr_service.dart';
import '../../core/services/storage_service.dart';
import '../models/media_item.dart';
import '../models/tag.dart';
import '../models/person.dart';
import '../widgets/capture_bottom_sheet.dart';
import '../widgets/people_chips.dart';
import '../../core/services/media_save_service.dart';
import '../../features/auth/providers/lock_session_provider.dart';
import '../../features/auth/services/lock_auth_service.dart';
import '../../features/auth/services/lock_toggle_helper.dart';
import '../../features/personal/providers/personal_provider.dart';
import '../../features/work/providers/work_provider.dart';
import 'media_viewer_screen.dart';

class MediaDetailScreen extends ConsumerStatefulWidget {
  final List<MediaItem> items;
  final int initialIndex;

  /// 신규 미디어 추가 직후 진입 여부.
  /// - true  : "추가" 흐름 — 하단 액션 바에서 [미디어 삭제] 버튼을 숨기고 [저장]만 표시.
  /// - false : 그리드 탭으로 진입한 "수정" 흐름 — [미디어 삭제]와 [저장] 모두 표시.
  final bool isNewlyAdded;

  const MediaDetailScreen({
    super.key,
    required this.items,
    this.initialIndex = 0,
    this.isNewlyAdded = false,
  });

  @override
  ConsumerState<MediaDetailScreen> createState() => _MediaDetailScreenState();
}

class _MediaDetailScreenState extends ConsumerState<MediaDetailScreen> {
  final _noteCtrl = TextEditingController();
  final _countryCtrl = TextEditingController();
  final _regionCtrl = TextEditingController();
  final _tagInputCtrl = TextEditingController();

  List<Tag> _allTags = [];
  Set<int> _selectedTagIds = {};
  Set<int> _initialTagIds = {}; // 로드 시점 태그 (변경 감지용)
  List<Person> _allPeople = [];
  Set<int> _selectedPersonIds = {};
  Set<int> _initialPersonIds = {}; // 로드 시점 인물 (변경 감지용)
  bool _saving = false;
  bool _saved = false;
  bool _locating = false;
  bool _ocrRunning = false;
  // per-item lock 게이트: item.isLocked == 1 이고 세션 만료면 인증 필요.
  // 인증 통과 전에는 본문(이미지/메타/메모) 일체를 가린다.
  // 인증 실패 시 화면 종료. (spec 섹션 6 (c))
  bool _gateChecked = false;
  bool _gateAuthorized = false;
  late List<MediaItem> _previewItems; // 삭제 반영용 로컬 복사본
  late DateTime _eventDate; // 이벤트 날짜 (takenAt 기반, 편집 가능)
  late DateTime _initialEventDate;

  final _mediaDao = MediaDao();
  final _tagDao = TagDao();
  final _peopleDao = PeopleDao();
  final _countryDefaultService = CountryDefaultService();

  // 현재 편집 대상 아이템 (초기 선택 항목)
  MediaItem get item => widget.items[widget.initialIndex];

  // 미디어 추가가 일어났는지 추적 — form 필드를 안 건드려도 저장 버튼이
  // 활성화되어야 한다 (Bug 2 회귀 가드).
  bool _mediaAdded = false;

  /// 현재 폼이 초기 상태에서 변경되었는지 여부
  bool get _isDirty {
    return _mediaAdded ||
        _noteCtrl.text.trim() != item.note.trim() ||
        _countryCtrl.text.trim() != item.countryCode.trim() ||
        _regionCtrl.text.trim() != item.region.trim() ||
        _eventDate.millisecondsSinceEpoch !=
            _initialEventDate.millisecondsSinceEpoch ||
        !_setEquals(_selectedTagIds, _initialTagIds) ||
        !_setEquals(_selectedPersonIds, _initialPersonIds);
  }

  bool _setEquals(Set<int> a, Set<int> b) =>
      a.length == b.length && a.containsAll(b);

  @override
  void initState() {
    super.initState();
    _previewItems = List.from(widget.items);
    _noteCtrl.text = item.note;
    _countryCtrl.text = item.countryCode;
    _regionCtrl.text = item.region;
    _eventDate = DateTime.fromMillisecondsSinceEpoch(item.takenAt);
    _initialEventDate = _eventDate;
    _loadTagsAndPeople();
    // per-item lock 게이트 — 잠긴 항목은 인증 후에만 본문 노출
    WidgetsBinding.instance.addPostFrameCallback((_) => _runLockGate());
    // Work 아이템이고 위치 정보가 없으면 EXIF에서 자동 입력
    // 그래도 country가 비어있으면 default(폰 locale → 최근 등록 country) 적용.
    if (item.space == MediaSpace.work &&
        item.countryCode.isEmpty &&
        item.region.isEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) async {
        await _autoFillLocation(silent: true);
        if (!mounted) return;
        if (_countryCtrl.text.trim().isEmpty) {
          await _applyCountryDefault();
        }
      });
    } else if (item.space == MediaSpace.work && item.countryCode.isEmpty) {
      // EXIF auto-fill 조건은 미충족(region은 채워져 있음) but country만 비어있는 경우
      WidgetsBinding.instance.addPostFrameCallback((_) async {
        await _applyCountryDefault();
      });
    }
  }

  /// Country picker default 적용 — 폰 locale → 최근 등록 country fallback.
  /// 빈 문자열이 반환되면 컨트롤러는 그대로 빈 채로 둔다 (사용자가 picker로 선택).
  Future<void> _applyCountryDefault() async {
    try {
      final code = await _countryDefaultService.resolveDefault();
      if (!mounted || code.isEmpty) return;
      // EXIF가 동시에 채웠을 가능성 — race 가드
      if (_countryCtrl.text.trim().isNotEmpty) return;
      setState(() {
        _countryCtrl.text = code;
      });
    } catch (_) {
      // 조용히 무시 — 사용자는 picker로 직접 선택 가능.
    }
  }

  /// ISO country code(예: "KR") → 사용자 친화 localized name(예: "대한민국") 변환.
  /// `country_picker`의 Country 객체에서 `nameLocalized`(디바이스 locale 기반)를
  /// 우선 사용하고, 없으면 영어 `name`, 매핑 실패 시 ISO 코드 그대로 fallback.
  /// 빈 문자열 입력 → 빈 문자열 (placeholder가 hintText로 보임).
  String _displayCountryName(String iso) {
    final code = iso.trim().toUpperCase();
    if (code.isEmpty) return '';
    try {
      final country = Country.tryParse(code);
      if (country == null) return iso;
      final localized = country.nameLocalized;
      if (localized != null && localized.isNotEmpty) return localized;
      return country.name;
    } catch (_) {
      return iso;
    }
  }

  /// Country picker 진입 — 250개 국가 리스트 + 검색바 + flag emoji.
  /// 한국어 라벨은 country_picker가 디바이스 locale에 맞춰 자동 처리.
  void _showCountryPicker() {
    showCountryPicker(
      context: context,
      showPhoneCode: false,
      countryListTheme: const CountryListThemeData(
        bottomSheetHeight: 500,
        inputDecoration: InputDecoration(
          labelText: '검색',
          hintText: '국가 이름 또는 코드',
          prefixIcon: Icon(Icons.search),
        ),
      ),
      onSelect: (Country country) {
        if (!mounted) return;
        setState(() {
          _countryCtrl.text = country.countryCode;
        });
      },
    );
  }

  /// per-item lock 게이트 — 항목이 잠겨있고 세션 만료면 인증 다이얼로그 호출.
  /// 인증 실패 시 화면 종료(pop). 통과하면 본문 노출 플래그를 켠다.
  Future<void> _runLockGate() async {
    if (!mounted) return;
    if (item.isLocked != 1) {
      setState(() {
        _gateChecked = true;
        _gateAuthorized = true;
      });
      return;
    }
    final session = ref.read(lockSessionProvider);
    if (session.isUnlocked) {
      setState(() {
        _gateChecked = true;
        _gateAuthorized = true;
      });
      return;
    }
    final LockAuthService auth = ref.read(lockAuthServiceProvider);
    final ok = await auth.authenticate(context);
    if (!mounted) return;
    if (!ok) {
      _saved = true; // PopScope dirty 가드 우회 (인증 실패는 강제 종료)
      Navigator.of(context).pop();
      return;
    }
    setState(() {
      _gateChecked = true;
      _gateAuthorized = true;
    });
  }

  Future<void> _loadTagsAndPeople() async {
    final allTags = await _tagDao.findBySpace(item.space);
    final selTags = item.id != null
        ? await _tagDao.findByMediaId(item.id!)
        : <Tag>[];

    List<Person> allPeople = [];
    Set<int> selPersonIds = {};
    if (item.space == MediaSpace.personal) {
      allPeople = await _peopleDao.findAll();
      if (item.id != null) {
        final selPeople = await _peopleDao.findByMediaId(item.id!);
        selPersonIds = selPeople.map((p) => p.id!).toSet();
      }
    }

    final selTagIds = selTags.map((t) => t.id!).toSet();
    setState(() {
      _allTags = allTags;
      _selectedTagIds = selTagIds;
      _initialTagIds = Set.from(selTagIds);
      _allPeople = allPeople;
      _selectedPersonIds = selPersonIds;
      _initialPersonIds = Set.from(selPersonIds);
    });
  }

  /// EXIF에서 GPS를 읽어 국가코드·지역 자동 입력
  /// [silent] = true이면 GPS 없을 때 스낵바를 표시하지 않음 (자동 호출용)
  Future<void> _autoFillLocation({bool silent = false}) async {
    setState(() => _locating = true);
    try {
      final bytes = await File(item.filePath).readAsBytes();
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
      } else if (!silent && mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('사진에 GPS 정보가 없습니다')));
      }
    } catch (_) {
      if (!silent && mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('위치 정보를 읽을 수 없습니다')));
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
        double d =
            r[0].numerator / r[0].denominator +
            r[1].numerator / r[1].denominator / 60 +
            r[2].numerator / r[2].denominator / 3600;
        if (ref == 'S' || ref == 'W') d = -d;
        return d;
      }
    } catch (_) {}
    return null;
  }

  /// OCR 실행 (사진 전용) — 결과를 DB에 저장하고 item 갱신
  Future<void> _runOcr() async {
    if (item.mediaType == MediaType.video) return;
    setState(() => _ocrRunning = true);
    try {
      final text = await OcrService.extractText(item.filePath);
      if (item.id != null) {
        await _mediaDao.updateOcrText(item.id!, text);
      }
      if (mounted) {
        setState(() {});
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              text.isEmpty ? '텍스트를 인식하지 못했습니다' : '텍스트 인식 완료 (${text.length}자)',
            ),
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _ocrRunning = false);
    }
  }

  /// 달력 + 시간 선택으로 이벤트 날짜 변경
  Future<void> _pickEventDate() async {
    final pickedDate = await showDatePicker(
      context: context,
      initialDate: _eventDate,
      firstDate: DateTime(2000),
      lastDate: DateTime.now().add(const Duration(days: 365)),
      helpText: '이벤트 날짜 선택',
      confirmText: '다음',
      cancelText: '취소',
    );
    if (pickedDate == null || !mounted) return;

    final pickedTime = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(_eventDate),
      helpText: '이벤트 시간',
      confirmText: '확인',
      cancelText: '취소',
    );
    if (!mounted) return;

    final newDate = DateTime(
      pickedDate.year,
      pickedDate.month,
      pickedDate.day,
      pickedTime?.hour ?? _eventDate.hour,
      pickedTime?.minute ?? _eventDate.minute,
      pickedTime == null ? _eventDate.second : 0,
    );
    setState(() => _eventDate = newDate);
  }

  @override
  void dispose() {
    _noteCtrl.dispose();
    _countryCtrl.dispose();
    _regionCtrl.dispose();
    _tagInputCtrl.dispose();
    super.dispose();
  }

  /// 잠금 토글 (Detail 진입점) — 인증 후 lock/unlock + 그리드 갱신.
  /// 토글 성공 시 detail을 pop 하여 상위에서 새 상태로 다시 진입하도록 한다.
  /// (현재 화면의 _previewItems/MediaItem 인스턴스는 immutable이라 상태가
  /// 어긋날 수 있으므로 pop 후 호출처에서 invalidate.)
  Future<void> _toggleLock() async {
    final ok = await handleLockToggle(context, ref, item);
    if (!ok || !mounted) return;
    // 양쪽 공간 모두 갱신 (work ↔ personal 모두 안전하게).
    ref.invalidate(workMediaProvider);
    ref.invalidate(secretMediaProvider);
    _saved = true; // dirty 가드 우회
    setState(() {});
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) Navigator.pop(context, 'locked');
    });
  }

  Future<void> _save() async {
    setState(() => _saving = true);

    final updated = item.copyWith(
      note: _noteCtrl.text.trim(),
      countryCode: _countryCtrl.text.trim(),
      region: _regionCtrl.text.trim(),
      takenAt: _eventDate.millisecondsSinceEpoch,
    );
    await _mediaDao.update(updated);

    if (updated.id != null) {
      await _tagDao.setMediaTags(updated.id!, _selectedTagIds.toList());
      if (updated.space == MediaSpace.personal) {
        await _peopleDao.setMediaPeople(
          updated.id!,
          _selectedPersonIds.toList(),
        );
      }
    }

    // 저장 후 초기값 갱신 (재진입 시 dirty 상태 초기화)
    _initialTagIds = Set.from(_selectedTagIds);
    _initialPersonIds = Set.from(_selectedPersonIds);
    _initialEventDate = _eventDate;

    _saved = true; // canPop 갱신이 rebuild 전에 반영되도록 먼저 설정
    setState(() => _saving = false);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) Navigator.pop(context, true);
    });
  }

  Future<void> _delete() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('삭제'),
        content: const Text('이 미디어를 삭제할까요? 파일도 함께 삭제됩니다.'),
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
    if (ok != true) return;
    await StorageService.deleteFile(item.filePath);
    if (item.thumbPath != null) {
      await StorageService.deleteFile(item.thumbPath!);
    }
    if (item.id != null) await _mediaDao.delete(item.id!);
    _saved = true;
    setState(() {});
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) Navigator.pop(context, 'deleted');
    });
  }

  /// 태그 즉시 저장 — 미디어 저장(저장 버튼)과 무관하게 태그 테이블에 바로 저장
  Future<void> _addCustomTag() async {
    final label = _tagInputCtrl.text.trim();
    if (label.isEmpty) return;
    _tagInputCtrl.clear();

    // 이미 존재하는 태그면 선택 상태로 전환
    final existing = _allTags.firstWhere(
      (t) => t.label == label,
      orElse: () =>
          Tag(space: item.space, key: '', label: '', color: '', icon: ''),
    );
    if (existing.id != null) {
      setState(() => _selectedTagIds.add(existing.id!));
      return;
    }

    // 새 태그 → 즉시 DB 저장
    final newTag = Tag(
      space: item.space,
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

  @override
  Widget build(BuildContext context) {
    final isWork = item.space == MediaSpace.work;
    final cs = Theme.of(context).colorScheme;

    // per-item lock 게이트 미통과 — 본문(이미지/메모/메타) 일체 가림
    if (item.isLocked == 1 && (!_gateChecked || !_gateAuthorized)) {
      return Scaffold(
        appBar: AppBar(
          foregroundColor: Colors.black87,
          title: Text(isWork ? 'Work 미디어' : 'Personal 미디어'),
        ),
        body: const Center(child: CircularProgressIndicator()),
      );
    }

    return PopScope(
      canPop: _saved || !_isDirty,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        // canPop == false 인 경우만 여기 도달 (dirty 상태)
        final result = await showDialog<String>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: const Text('변경사항이 있습니다'),
            content: const Text('저장하지 않고 나가시겠어요?'),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx, 'cancel'),
                child: const Text('계속 편집'),
              ),
              TextButton(
                onPressed: () => Navigator.pop(ctx, 'discard'),
                child: Text(
                  '저장 안 함',
                  style: TextStyle(
                    color: Theme.of(
                      context,
                    ).colorScheme.onSurface.withValues(alpha: 0.6),
                  ),
                ),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(ctx, 'save'),
                child: const Text('저장'),
              ),
            ],
          ),
        );
        if (!mounted) return;
        if (result == 'save') {
          await _save();
        } else if (result == 'discard') {
          _saved = true;
          setState(() {});
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) Navigator.pop(context);
          });
        }
        // 'cancel' → 아무것도 안 함
      },
      child: Scaffold(
        appBar: AppBar(
          foregroundColor: Colors.black87,
          actionsIconTheme: const IconThemeData(
            color: Color(0xFF1A73E8),
            size: 28,
          ),
          title: Text(isWork ? 'Work 미디어' : 'Personal 미디어'),
          actions: [
            // 잠금 토글 (Detail 진입점) — Phase 3C
            // (저장은 하단 sticky 액션 바로 이동 — UX 보강 A1/A2)
            IconButton(
              icon: Icon(
                item.isLocked == 1 ? Icons.lock_open : Icons.lock_outline,
              ),
              tooltip: item.isLocked == 1 ? '잠금 해제' : '잠금',
              onPressed: _saving ? null : _toggleLock,
            ),
          ],
        ),
        body: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              // 미디어 미리보기 (여러 장인 경우 PageView)
              _buildPreview(),
              const SizedBox(height: 8),
              _MediaMeta(item: item),
              const SizedBox(height: 16),

              // ── 이벤트 날짜 ──
              _EventDateRow(date: _eventDate, onTap: _pickEventDate),
              const SizedBox(height: 16),

              // 태그 섹션 — 이벤트 날짜 바로 아래로 이동 (A5: 태그로 검색 용이)
              _TagSection(
                allTags: _allTags,
                selectedTagIds: _selectedTagIds,
                tagInputCtrl: _tagInputCtrl,
                onToggle: (id) => setState(() {
                  if (_selectedTagIds.contains(id)) {
                    _selectedTagIds.remove(id);
                  } else {
                    _selectedTagIds.add(id);
                  }
                }),
                onAddCustom: _addCustomTag,
                colorScheme: cs,
              ),
              const SizedBox(height: 16),

              // Work 위치 필드 (EXIF 자동 입력됨, 수동 수정 가능)
              if (isWork) ...[
                // 위치 자동입력 버튼
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      '위치',
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    TextButton.icon(
                      onPressed: _locating ? null : _autoFillLocation,
                      icon: _locating
                          ? const SizedBox(
                              width: 14,
                              height: 14,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.my_location, size: 16),
                      label: Text(
                        _locating ? '읽는 중...' : 'GPS 자동 입력',
                        style: const TextStyle(fontSize: 12),
                      ),
                      style: TextButton.styleFrom(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 4,
                        ),
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // 국가 — picker로만 입력. 표시는 사용자 친화 localized name
                    // ("대한민국"), 저장은 ISO 코드("KR"). _countryCtrl.text는
                    // ISO 그대로 보관 (DB 호환), UI 표시만 변환 (Bug 3 회귀 가드).
                    Expanded(
                      child: InkWell(
                        onTap: _showCountryPicker,
                        child: InputDecorator(
                          decoration: const InputDecoration(
                            labelText: '국가',
                            hintText: '선택',
                            prefixIcon: Icon(Icons.flag_outlined, size: 18),
                            suffixIcon: Icon(Icons.arrow_drop_down),
                          ),
                          child: Text(
                            _displayCountryName(_countryCtrl.text),
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    // 지역 — 사용자 직접 텍스트 입력 (picker 적용 X).
                    Expanded(
                      child: TextField(
                        controller: _regionCtrl,
                        decoration: const InputDecoration(
                          labelText: '지역',
                          hintText: '서울',
                          prefixIcon: Icon(
                            Icons.location_on_outlined,
                            size: 18,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
              ],

              // 메모 — 1줄로 시작, 입력 내용에 맞춰 height 자동 확장 (A6)
              TextField(
                controller: _noteCtrl,
                maxLines: null,
                minLines: 1,
                keyboardType: TextInputType.multiline,
                textInputAction: TextInputAction.newline,
                decoration: const InputDecoration(
                  labelText: '메모',
                  hintText: '메모를 입력하세요',
                  alignLabelWithHint: true,
                ),
              ),
              const SizedBox(height: 20),

              // 인물 (Personal 전용)
              if (!isWork) ...[
                Text(
                  '인물',
                  style: Theme.of(
                    context,
                  ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 8),
                PeopleChips(
                  allPeople: _allPeople,
                  selectedIds: _selectedPersonIds,
                  onChanged: (ids) {
                    _peopleDao.findAll().then((people) {
                      setState(() {
                        _allPeople = people;
                        _selectedPersonIds = ids;
                      });
                    });
                  },
                ),
                const SizedBox(height: 20),
              ],

              // ── OCR 인식 텍스트 (사진·문서만) ──
              if (item.mediaType != MediaType.video) ...[
                _OcrSection(item: item, isRunning: _ocrRunning, onRun: _runOcr),
              ],
              const SizedBox(height: 24),
            ],
          ),
        ),
        // 하단 sticky 액션 바 — 저장 + (수정 모드일 때만) 미디어 삭제 (A2)
        bottomNavigationBar: _BottomActionBar(
          isNewlyAdded: widget.isNewlyAdded,
          isSaving: _saving,
          isDirty: _isDirty,
          onSave: _save,
          onDelete: _delete,
        ),
      ), // PopScope
    );
  }

  Widget _buildPreview() {
    final items = _previewItems;

    return Column(
      children: [
        ...List.generate(items.length, (index) {
          final cur = items[index];
          return Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: _buildSingleImage(cur, index),
          );
        }),
        const SizedBox(height: 4),
        OutlinedButton.icon(
          onPressed: _addMedia,
          icon: const Icon(Icons.add_photo_alternate_outlined, size: 20),
          label: const Text('미디어 추가'),
          style: OutlinedButton.styleFrom(
            minimumSize: const Size(double.infinity, 52),
            foregroundColor: Theme.of(
              context,
            ).colorScheme.onSurface.withValues(alpha: 0.6),
            side: BorderSide(
              color: Theme.of(context).dividerColor,
              width: 1,
            ),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
          ),
        ),
      ],
    );
  }

  /// 미디어 추가 — CaptureBottomSheet로 선택 후 저장, _previewItems에 추가.
  ///
  /// 사용자 의도: 기존 work/personal 카드에 사진을 더 추가하면 같은 카드에
  /// 묶여야 함. MediaTimeline은 batchId 기준 그룹화이므로, 신규 항목을
  /// 기존 그룹의 batchId로 저장한다 (overrideBatchId).
  ///
  /// 기존 그룹이 단일 사진이라 batchId == '' 이면, 새 batchId를 부여하면서
  /// 기존 row도 함께 갱신해 둘이 같은 카드로 묶이도록 한다.
  Future<void> _addMedia() async {
    final captureResult = await CaptureBottomSheet.show(
      context,
      allowDocument: item.space == MediaSpace.work,
      space: item.space,
    );
    if (captureResult == null ||
        captureResult.items.isEmpty ||
        !mounted) {
      return;
    }
    try {
      // 기존 그룹의 batchId 결정.
      String batchId = _previewItems.first.batchId;
      if (batchId.isEmpty) {
        // 단일 사진 그룹 — 신규 batchId 만들고 기존 row(s)도 같은 batchId로 갱신.
        // 메모리 _previewItems도 함께 갱신해 다음 _addMedia 호출 시 같은 batchId
        // 인계가 정확히 작동하도록 한다.
        batchId = const Uuid().v4();
        final updatedExisting = <MediaItem>[];
        for (final existing in _previewItems) {
          if (existing.id != null) {
            final updated = existing.copyWith(batchId: batchId);
            await _mediaDao.update(updated);
            updatedExisting.add(updated);
          } else {
            updatedExisting.add(existing);
          }
        }
        _previewItems = updatedExisting;
      }

      final results = await MediaSaveService.saveAll(
        captured: captureResult.items,
        space: item.space,
        albumId: item.albumId,
        overrideBatchId: batchId,
      );
      if (!mounted) return;
      setState(() {
        // 사용자 의도: "최하단에 하나씩 붙이는 방식". append로 추가.
        // group 안 정렬은 MediaTimeline._groupByBatch가 createdAt ASC로 처리해
        // 재진입 시에도 같은 순서 유지 (오래된 사진 먼저, 새 추가가 마지막).
        _previewItems = [..._previewItems, ...results.map((r) => r.item)];
        // 저장 버튼 활성화 트리거 — form 필드 미변경이어도 dirty로 간주 (Bug 2).
        _mediaAdded = true;
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('미디어 저장 실패: $e'), backgroundColor: Colors.red),
      );
    }
  }

  /// viewer에서 삭제 후 돌아오면 파일이 없는 항목 제거
  void _refreshPreviewItems() {
    final updated = _previewItems
        .where((it) => File(it.filePath).existsSync())
        .toList();
    if (!mounted) return;
    setState(() => _previewItems = updated);
    if (updated.isEmpty && mounted) Navigator.pop(context, 'deleted');
  }

  Widget _buildSingleImage(MediaItem cur, int index) {
    final allItems = _previewItems;

    // 문서
    if (cur.mediaType == MediaType.document) {
      return Container(
        height: 120,
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surfaceContainerLow,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: Theme.of(context).dividerColor),
        ),
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.description,
                size: 48,
                color: Theme.of(
                  context,
                ).colorScheme.onSurface.withValues(alpha: 0.6),
              ),
              const SizedBox(height: 8),
              Text(
                cur.filePath.split('/').last,
                style: TextStyle(
                  fontSize: 12,
                  color: Theme.of(
                    context,
                  ).colorScheme.onSurface.withValues(alpha: 0.6),
                ),
              ),
            ],
          ),
        ),
      );
    }

    // 사진 / 영상
    // 사진은 filePath(원본)를 우선해야 화질 보존. 비디오는 mp4라 Image.file에
    // 못 띄우므로 thumbPath 사용 (Bug #1 회귀 가드).
    final path = cur.mediaType == MediaType.video
        ? (cur.thumbPath ?? cur.filePath)
        : cur.filePath;
    if (File(path).existsSync()) {
      return GestureDetector(
        onTap: () async {
          final result = await Navigator.push<dynamic>(
            context,
            MaterialPageRoute(
              builder: (_) => MediaViewerScreen(
                items: List.from(allItems),
                initialIndex: index,
              ),
            ),
          );
          if (result == 'deleted') _refreshPreviewItems();
        },
        child: ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: Stack(
            children: [
              Image.file(
                File(path),
                height: 240,
                width: double.infinity,
                fit: BoxFit.cover,
              ),
              // 여러 장일 때 순서 표시
              if (allItems.length > 1)
                Positioned(
                  left: 10,
                  top: 10,
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.black54,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      '${index + 1} / ${allItems.length}',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ),
              // 전체화면 힌트
              Positioned(
                right: 10,
                bottom: 10,
                child: Container(
                  padding: const EdgeInsets.all(6),
                  decoration: BoxDecoration(
                    color: Colors.black54,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(
                    Icons.fullscreen,
                    color: Colors.white,
                    size: 18,
                  ),
                ),
              ),
              // 영상 play 오버레이
              if (cur.mediaType == MediaType.video)
                const Center(
                  child: Icon(
                    Icons.play_circle_fill,
                    color: Colors.white70,
                    size: 52,
                  ),
                ),
            ],
          ),
        ),
      );
    }

    return Container(
      height: 240,
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(12),
      ),
    );
  }
}

// ── 태그 섹션 위젯 ────────────────────────────────────────────

class _TagSection extends StatelessWidget {
  final List<Tag> allTags;
  final Set<int> selectedTagIds;
  final TextEditingController tagInputCtrl;
  final ValueChanged<int> onToggle;
  final VoidCallback onAddCustom;
  final ColorScheme colorScheme;

  const _TagSection({
    required this.allTags,
    required this.selectedTagIds,
    required this.tagInputCtrl,
    required this.onToggle,
    required this.onAddCustom,
    required this.colorScheme,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '태그',
          style: Theme.of(
            context,
          ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 10),

        // 기존 태그 선택 (통일 색상: 에메랄드)
        Wrap(
          spacing: 6,
          runSpacing: 6,
          children: allTags.map((tag) {
            final selected = selectedTagIds.contains(tag.id);
            const primary = Color(0xFF00C896);
            const unselectedText = Color(0xFF005C42); // 진한 다크그린
            return GestureDetector(
              onTap: () => onToggle(tag.id!),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: selected ? primary : primary.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                    color: selected ? primary : primary,
                    width: 1.5,
                  ),
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

        // 태그 직접 입력 필드
        const SizedBox(height: 10),
        Row(
          children: [
            Expanded(
              child: TextField(
                controller: tagInputCtrl,
                decoration: InputDecoration(
                  hintText: '태그 직접 입력',
                  hintStyle: const TextStyle(fontSize: 13),
                  prefixIcon: const Icon(Icons.label_outline, size: 18),
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 10,
                  ),
                  isDense: true,
                ),
                onSubmitted: (_) => onAddCustom(),
              ),
            ),
            const SizedBox(width: 8),
            FilledButton(
              onPressed: onAddCustom,
              style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 12,
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              child: const Text('추가', style: TextStyle(fontSize: 13)),
            ),
          ],
        ),
      ],
    );
  }
}

// ── 이벤트 날짜 행 ───────────────────────────────────────────

class _EventDateRow extends StatelessWidget {
  final DateTime date;
  final VoidCallback onTap;

  const _EventDateRow({required this.date, required this.onTap});

  static final _dateFmt = DateFormat('yyyy년 M월 d일 (E)  HH:mm', 'ko');

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cs = Theme.of(context).colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '이벤트 날짜',
          style: Theme.of(
            context,
          ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 8),
        InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(12),
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
            decoration: BoxDecoration(
              color: isDark
                  ? const Color(0xFF1E2530)
                  : cs.primary.withValues(alpha: 0.05),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: cs.primary.withValues(alpha: 0.3),
                width: 1.2,
              ),
            ),
            child: Row(
              children: [
                Icon(Icons.event_outlined, size: 20, color: cs.primary),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    _dateFmt.format(date),
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: isDark ? Colors.white : const Color(0xFF1A1F2E),
                    ),
                  ),
                ),
                Icon(Icons.edit_calendar_outlined, size: 18, color: cs.primary),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

// ── 메타 정보 ────────────────────────────────────────────────

class _MediaMeta extends StatelessWidget {
  final MediaItem item;
  const _MediaMeta({required this.item});

  @override
  Widget build(BuildContext context) {
    final sizeKb = StorageService.fileSizeKb(item.filePath);
    final sizeLabel = sizeKb >= 1024
        ? '${(sizeKb / 1024).toStringAsFixed(1)} MB'
        : '$sizeKb KB';

    return Wrap(
      spacing: 12,
      runSpacing: 4,
      children: [
        _metaItem(context, Icons.folder_outlined, sizeLabel),
        if (item.countryCode.isNotEmpty || item.region.isNotEmpty)
          _metaItem(
            context,
            Icons.location_on_outlined,
            [
              item.countryCode,
              item.region,
            ].where((s) => s.isNotEmpty).join(' · '),
          ),
      ],
    );
  }

  Widget _metaItem(BuildContext context, IconData icon, String text) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          icon,
          size: 13,
          color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.6),
        ),
        const SizedBox(width: 4),
        Text(
          text,
          style: TextStyle(
            fontSize: 12,
            color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.6),
          ),
        ),
      ],
    );
  }
}

// ── OCR 섹션 ─────────────────────────────────────────────────
//
// A4: OCR 결과는 기본 2줄까지만 표시하고, 멀티라인 또는 60자 초과인 경우
// "더보기"/"접기" 토글로 전체 본문을 펼칠 수 있다.

class _OcrSection extends StatefulWidget {
  final MediaItem item;
  final bool isRunning;
  final VoidCallback onRun;

  const _OcrSection({
    required this.item,
    required this.isRunning,
    required this.onRun,
  });

  @override
  State<_OcrSection> createState() => _OcrSectionState();
}

class _OcrSectionState extends State<_OcrSection> {
  bool _expanded = false;

  static const int _ocrCollapsedMaxLines = 2;
  static const int _ocrExpandThresholdChars = 60;

  bool get _shouldShowToggle {
    final text = widget.item.ocrText;
    return text.contains('\n') || text.length > _ocrExpandThresholdChars;
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cs = Theme.of(context).colorScheme;
    final hasText = widget.item.ocrText.isNotEmpty;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 헤더 행
        Row(
          children: [
            const Icon(Icons.text_fields_outlined, size: 18),
            const SizedBox(width: 6),
            Expanded(
              child: Text(
                'OCR 텍스트 인식',
                style: Theme.of(
                  context,
                ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
              ),
            ),
            // 실행 버튼
            TextButton.icon(
              onPressed: widget.isRunning ? null : widget.onRun,
              icon: widget.isRunning
                  ? const SizedBox(
                      width: 14,
                      height: 14,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.document_scanner_outlined, size: 16),
              label: Text(
                widget.isRunning
                    ? '인식 중...'
                    : (hasText ? '재인식' : '텍스트 인식'),
                style: const TextStyle(fontSize: 13),
              ),
              style: TextButton.styleFrom(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 4,
                ),
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),

        // OCR 결과 표시
        if (hasText)
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF1A2030) : const Color(0xFFF0F8FF),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: isDark
                    ? const Color(0xFF2A3550)
                    : const Color(0xFFBBDDFF),
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.format_quote,
                      size: 14,
                      color: cs.primary.withValues(alpha: 0.6),
                    ),
                    const SizedBox(width: 4),
                    Text(
                      '인식된 텍스트 (${widget.item.ocrText.length}자)',
                      style: TextStyle(
                        fontSize: 11,
                        color: cs.primary,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const Spacer(),
                    // 클립보드 복사
                    GestureDetector(
                      onTap: () {
                        Clipboard.setData(
                          ClipboardData(text: widget.item.ocrText),
                        );
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('클립보드에 복사했습니다'),
                            duration: Duration(seconds: 1),
                          ),
                        );
                      },
                      child: Icon(
                        Icons.copy_outlined,
                        size: 16,
                        color: cs.primary.withValues(alpha: 0.7),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                // A4: 기본 2줄(maxLines: 2 + ellipsis), 펼침 시 전체 표시
                AnimatedSize(
                  duration: const Duration(milliseconds: 180),
                  alignment: Alignment.topLeft,
                  child: Text(
                    widget.item.ocrText,
                    maxLines: _expanded ? null : _ocrCollapsedMaxLines,
                    overflow: _expanded
                        ? TextOverflow.visible
                        : TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 13,
                      height: 1.6,
                      color: isDark ? Colors.white70 : const Color(0xFF2D3748),
                    ),
                  ),
                ),
                if (_shouldShowToggle) ...[
                  const SizedBox(height: 6),
                  GestureDetector(
                    onTap: () => setState(() => _expanded = !_expanded),
                    behavior: HitTestBehavior.opaque,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 2),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            _expanded ? '접기' : '더보기',
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w700,
                              color: cs.primary,
                            ),
                          ),
                          const SizedBox(width: 2),
                          Icon(
                            _expanded
                                ? Icons.keyboard_arrow_up
                                : Icons.keyboard_arrow_down,
                            size: 16,
                            color: cs.primary,
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ],
            ),
          )
        else
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(vertical: 16),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF1A2030) : const Color(0xFFF8F9FA),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: isDark
                    ? const Color(0xFF2A3550)
                    : const Color(0xFFE0E0E0),
              ),
            ),
            child: Column(
              children: [
                Icon(
                  Icons.text_snippet_outlined,
                  size: 32,
                  color: Theme.of(
                    context,
                  ).colorScheme.onSurface.withValues(alpha: 0.3),
                ),
                const SizedBox(height: 8),
                Text(
                  '인식된 텍스트가 없습니다\n"텍스트 인식" 버튼을 눌러 실행하세요',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 13,
                    color: Theme.of(
                      context,
                    ).colorScheme.onSurface.withValues(alpha: 0.6),
                    height: 1.5,
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

// ── 하단 sticky 액션 바 ──────────────────────────────────────
//
// A2: AppBar 우상단의 저장 아이콘을 제거하고, 화면 하단에 항상 보이는
// 액션 바로 [미디어 삭제] [저장] 버튼을 노출.
// - isNewlyAdded == true  → [저장]만 풀너비.
// - isNewlyAdded == false → [미디어 삭제] (좌, 빨강) + [저장] (우, primary).

class _BottomActionBar extends StatelessWidget {
  final bool isNewlyAdded;
  final bool isSaving;
  final bool isDirty;
  final VoidCallback onSave;
  final VoidCallback onDelete;

  const _BottomActionBar({
    required this.isNewlyAdded,
    required this.isSaving,
    required this.isDirty,
    required this.onSave,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final canSave = !isSaving && (isDirty || isNewlyAdded);

    final saveButton = FilledButton.icon(
      onPressed: canSave ? onSave : null,
      icon: isSaving
          ? const SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: Colors.white,
              ),
            )
          : const Icon(Icons.check, size: 18),
      label: Text(isSaving ? '저장 중...' : '저장'),
      style: FilledButton.styleFrom(
        backgroundColor: cs.primary,
        foregroundColor: Colors.white,
        padding: const EdgeInsets.symmetric(vertical: 14),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
        textStyle: const TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w700,
        ),
      ),
    );

    final deleteButton = OutlinedButton.icon(
      onPressed: isSaving ? null : onDelete,
      icon: const Icon(Icons.delete_outline, color: Colors.red, size: 18),
      label: const Text(
        '미디어 삭제',
        style: TextStyle(color: Colors.red, fontWeight: FontWeight.w600),
      ),
      style: OutlinedButton.styleFrom(
        side: const BorderSide(color: Colors.red, width: 1),
        padding: const EdgeInsets.symmetric(vertical: 14),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
      ),
    );

    return SafeArea(
      top: false,
      child: Container(
        padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
        decoration: BoxDecoration(
          color: Theme.of(context).scaffoldBackgroundColor,
          border: Border(
            top: BorderSide(
              color: Theme.of(context).dividerColor.withValues(alpha: 0.6),
              width: 0.6,
            ),
          ),
        ),
        child: isNewlyAdded
            ? SizedBox(width: double.infinity, child: saveButton)
            : Row(
                children: [
                  Expanded(child: deleteButton),
                  const SizedBox(width: 10),
                  Expanded(child: saveButton),
                ],
              ),
      ),
    );
  }
}
