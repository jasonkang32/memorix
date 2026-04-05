import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/work_provider.dart';
import '../../../core/db/media_dao.dart';

// 국가 코드 → 국가명 매핑 (주요 출장국)
const _countryNames = {
  'KR': '🇰🇷 한국',
  'JP': '🇯🇵 일본',
  'CN': '🇨🇳 중국',
  'US': '🇺🇸 미국',
  'DE': '🇩🇪 독일',
  'GB': '🇬🇧 영국',
  'FR': '🇫🇷 프랑스',
  'SG': '🇸🇬 싱가포르',
  'AU': '🇦🇺 호주',
  'IN': '🇮🇳 인도',
  'TH': '🇹🇭 태국',
  'VN': '🇻🇳 베트남',
};

final _countryListProvider = FutureProvider<List<String>>((ref) async {
  // DB에서 실제로 사용된 국가 코드만 가져오기
  final dao = MediaDao();
  final items = await dao.findWork();
  final codes = items
      .map((i) => i.countryCode)
      .where((c) => c.isNotEmpty)
      .toSet()
      .toList()
    ..sort();
  return codes;
});

final _regionListProvider =
    FutureProvider.family<List<String>, String>((ref, countryCode) async {
  final dao = MediaDao();
  final items = await dao.findWork(countryCode: countryCode);
  final regions = items
      .map((i) => i.region)
      .where((r) => r.isNotEmpty)
      .toSet()
      .toList()
    ..sort();
  return regions;
});

class WorkFilterSheet extends ConsumerStatefulWidget {
  const WorkFilterSheet({super.key});

  @override
  ConsumerState<WorkFilterSheet> createState() => _WorkFilterSheetState();
}

class _WorkFilterSheetState extends ConsumerState<WorkFilterSheet> {
  String? _selectedCountry;
  String? _selectedRegion;

  @override
  void initState() {
    super.initState();
    final current = ref.read(workFilterProvider);
    _selectedCountry = current.countryCode;
    _selectedRegion = current.region;
  }

  void _apply() {
    ref.read(workFilterProvider.notifier).state = WorkFilter(
      countryCode: _selectedCountry,
      region: _selectedRegion,
    );
    Navigator.pop(context);
  }

  void _reset() {
    ref.read(workFilterProvider.notifier).state = const WorkFilter();
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final countriesAsync = ref.watch(_countryListProvider);
    final regionsAsync = _selectedCountry != null
        ? ref.watch(_regionListProvider(_selectedCountry!))
        : null;

    return DraggableScrollableSheet(
      initialChildSize: 0.55,
      minChildSize: 0.4,
      maxChildSize: 0.85,
      expand: false,
      builder: (context, scrollCtrl) => Column(
        children: [
          // 핸들
          const SizedBox(height: 8),
          Container(
            width: 40,
            height: 4,
            decoration: BoxDecoration(
              color: Colors.grey[300],
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('필터',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                Row(
                  children: [
                    TextButton(onPressed: _reset, child: const Text('초기화')),
                    const SizedBox(width: 8),
                    FilledButton(onPressed: _apply, child: const Text('적용')),
                  ],
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: ListView(
              controller: scrollCtrl,
              padding: const EdgeInsets.all(16),
              children: [
                // 국가 선택
                Text('국가', style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                countriesAsync.when(
                  loading: () => const Center(child: CircularProgressIndicator()),
                  error: (e, _) => Text('오류: $e'),
                  data: (countries) => countries.isEmpty
                      ? const Text('등록된 국가 없음',
                          style: TextStyle(color: Colors.grey))
                      : Wrap(
                          spacing: 8,
                          runSpacing: 4,
                          children: [
                            ChoiceChip(
                              label: const Text('전체'),
                              selected: _selectedCountry == null,
                              onSelected: (_) => setState(() {
                                _selectedCountry = null;
                                _selectedRegion = null;
                              }),
                            ),
                            ...countries.map((code) => ChoiceChip(
                                  label: Text(
                                      _countryNames[code] ?? code),
                                  selected: _selectedCountry == code,
                                  onSelected: (_) => setState(() {
                                    _selectedCountry = code;
                                    _selectedRegion = null;
                                  }),
                                )),
                          ],
                        ),
                ),
                // 지역 선택 (국가 선택 후)
                if (_selectedCountry != null) ...[
                  const SizedBox(height: 20),
                  Text('지역', style: Theme.of(context).textTheme.titleSmall),
                  const SizedBox(height: 8),
                  regionsAsync!.when(
                    loading: () =>
                        const Center(child: CircularProgressIndicator()),
                    error: (e, _) => Text('오류: $e'),
                    data: (regions) => regions.isEmpty
                        ? const Text('등록된 지역 없음',
                            style: TextStyle(color: Colors.grey))
                        : Wrap(
                            spacing: 8,
                            runSpacing: 4,
                            children: [
                              ChoiceChip(
                                label: const Text('전체'),
                                selected: _selectedRegion == null,
                                onSelected: (_) =>
                                    setState(() => _selectedRegion = null),
                              ),
                              ...regions.map((r) => ChoiceChip(
                                    label: Text(r),
                                    selected: _selectedRegion == r,
                                    onSelected: (_) =>
                                        setState(() => _selectedRegion = r),
                                  )),
                            ],
                          ),
                  ),
                ],
                // 미디어 타입 필터
                const SizedBox(height: 20),
                Text('미디어 유형', style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                _MediaTypeFilter(),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _MediaTypeFilter extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final filter = ref.watch(workFilterProvider);
    return Wrap(
      spacing: 8,
      children: [
        ChoiceChip(
          label: const Text('전체'),
          selected: filter.mediaType == null,
          onSelected: (_) => ref.read(workFilterProvider.notifier).state =
              WorkFilter(
                  countryCode: filter.countryCode, region: filter.region),
        ),
        ChoiceChip(
          label: const Text('📷 사진'),
          selected: filter.mediaType == 'photo',
          onSelected: (_) => ref.read(workFilterProvider.notifier).state =
              WorkFilter(
                  countryCode: filter.countryCode,
                  region: filter.region,
                  mediaType: 'photo'),
        ),
        ChoiceChip(
          label: const Text('🎬 영상'),
          selected: filter.mediaType == 'video',
          onSelected: (_) => ref.read(workFilterProvider.notifier).state =
              WorkFilter(
                  countryCode: filter.countryCode,
                  region: filter.region,
                  mediaType: 'video'),
        ),
        ChoiceChip(
          label: const Text('📄 문서'),
          selected: filter.mediaType == 'document',
          onSelected: (_) => ref.read(workFilterProvider.notifier).state =
              WorkFilter(
                  countryCode: filter.countryCode,
                  region: filter.region,
                  mediaType: 'document'),
        ),
      ],
    );
  }
}
