import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path_provider/path_provider.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../core/services/auth_service.dart';
import '../../../core/services/drive_service.dart';
import '../../../core/services/storage_service.dart';
import '../../../shared/theme/app_theme.dart';

import '../../../features/auth/providers/lock_provider.dart';
import '../../../features/auth/screens/pin_setup_screen.dart';
import 'tag_management_screen.dart';
import 'people_management_screen.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  bool _hasPin = false;
  bool _personalLockEnabled = false;
  bool _biometricEnabled = false;
  bool _biometricAvailable = false; // 기기가 생체인증 지원 + 등록됨
  bool _driveConnected = false;
  bool _driveSyncing = false;
  StorageBreakdown? _storage;
  String _version = '';
  String _buildNumber = '';
  StorageLocation _storageLocation = StorageLocation.internal;
  PhotoQuality _photoQuality = PhotoQuality.original;

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    final results = await Future.wait([
      AuthService.hasPin(),
      AuthService.isPersonalLockEnabled(),
      StorageService.calcBreakdown(),
      DriveService.isSignedIn,
      PackageInfo.fromPlatform(),
      StorageService.getStorageLocation(),
      StorageService.getPhotoQuality(),
      AuthService.canUseBiometric(),
      AuthService.isBiometricEnabled(),
    ]);
    if (!mounted) return;
    setState(() {
      _hasPin = results[0] as bool;
      _personalLockEnabled = results[1] as bool;
      _storage = results[2] as StorageBreakdown;
      _driveConnected = results[3] as bool;
      final info = results[4] as PackageInfo;
      _version = info.version;
      _buildNumber = info.buildNumber;
      _storageLocation = results[5] as StorageLocation;
      _photoQuality = results[6] as PhotoQuality;
      _biometricAvailable = results[7] as bool;
      _biometricEnabled = results[8] as bool;
    });
  }

  Future<void> _onAppLockTap() async {
    if (_hasPin) {
      // PIN 해제 확인
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('앱 잠금 해제'),
          content: const Text('PIN을 삭제하고 앱 잠금을 해제할까요?'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('취소'),
            ),
            FilledButton(
              style: FilledButton.styleFrom(backgroundColor: Colors.red),
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('해제'),
            ),
          ],
        ),
      );
      if (ok == true) {
        await AuthService.setPin('');
        await AuthService.setBiometricEnabled(false); // PIN 제거 시 생체인증도 해제
        setState(() {
          _hasPin = false;
          _biometricEnabled = false;
        });
        ref.read(lockProvider.notifier).unlock();
      }
    } else {
      final result = await Navigator.push<bool>(
        context,
        MaterialPageRoute(builder: (_) => const PinSetupScreen()),
      );
      if (result == true) setState(() => _hasPin = true);
    }
  }

  Future<void> _onBiometricToggle(bool value) async {
    await AuthService.setBiometricEnabled(value);
    setState(() => _biometricEnabled = value);
  }

  Future<void> _onPersonalLockToggle(bool value) async {
    await AuthService.setPersonalLockEnabled(value);
    setState(() => _personalLockEnabled = value);
  }

  Future<void> _onDriveConnect() async {
    final error = await DriveService.signIn();
    if (!mounted) return;
    if (error == null) {
      ref.invalidate(driveAccountInfoProvider);
      setState(() => _driveConnected = true);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Google Drive 연결 완료')));
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error),
          duration: const Duration(seconds: 6),
          backgroundColor: Colors.red[700],
        ),
      );
    }
  }

  Future<void> _onDriveDisconnect() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Drive 연결 해제'),
        content: const Text('Google Drive 연결을 해제합니다.\n이미 업로드된 파일은 유지됩니다.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('취소'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('해제'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    await DriveService.signOut();
    if (!mounted) return;
    ref.invalidate(driveAccountInfoProvider);
    setState(() => _driveConnected = false);
  }

  Future<void> _onDriveSync() async {
    setState(() => _driveSyncing = true);
    final result = await DriveService.syncPending();
    if (!mounted) return;
    setState(() => _driveSyncing = false);
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(result.summary)));
  }

  Future<void> _onClearStorage() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('저장소 초기화'),
        content: const Text('모든 미디어와 DB를 삭제합니다.\n이 작업은 되돌릴 수 없습니다.'),
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
    if (ok != true || !mounted) return;
    try {
      final dir = await getApplicationDocumentsDirectory();
      final memorixDir = Directory('${dir.path}/memorix');
      if (memorixDir.existsSync()) memorixDir.deleteSync(recursive: true);
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('저장소가 초기화되었습니다')));
        _loadSettings();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('오류: $e')));
      }
    }
  }

  Future<void> _launchPrivacyPolicy() async {
    final uri = Uri.parse('https://memorix.app/privacy');
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    }
  }

  Future<void> _launchFeedback() async {
    final uri = Uri.parse('mailto:support@memorix.app?subject=Memorix%20피드백');
    if (await canLaunchUrl(uri)) await launchUrl(uri);
  }

  Future<void> _onRefreshStorage() async {
    setState(() => _storage = null);
    final s = await StorageService.calcBreakdown();
    if (mounted) setState(() => _storage = s);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('⚙️ 설정')),
      body: ListView(
        children: [
          // ── 보안 ──
          _SectionHeader('보안'),
          ListTile(
            leading: Icon(
              _hasPin ? Icons.lock : Icons.lock_open_outlined,
              color: _hasPin ? Theme.of(context).colorScheme.primary : null,
            ),
            title: const Text('앱 잠금 (PIN)'),
            subtitle: Text(_hasPin ? '잠금 설정됨 — 탭하여 해제' : '탭하여 PIN 설정'),
            trailing: _hasPin
                ? Icon(
                    Icons.check_circle,
                    color: Theme.of(context).colorScheme.primary,
                  )
                : const Icon(Icons.chevron_right),
            onTap: _onAppLockTap,
          ),
          // 생체인증 (기기 지원 + PIN 설정 시에만 노출)
          if (_biometricAvailable && _hasPin)
            SwitchListTile(
              secondary: const Icon(Icons.fingerprint),
              title: const Text('생체인증'),
              subtitle: Text(
                _biometricEnabled
                    ? '잠금 화면에서 생체인증으로 해제'
                    : '지문 / Face ID로 빠른 잠금 해제',
              ),
              value: _biometricEnabled,
              onChanged: _onBiometricToggle,
            ),
          SwitchListTile(
            secondary: const Icon(Icons.lock_outline_rounded),
            title: const Text('Secret 보관함 항상 잠금'),
            subtitle: const Text('Secret 탭 진입 시 매번 생체인증 요구'),
            value: _personalLockEnabled,
            onChanged: _hasPin ? _onPersonalLockToggle : null,
          ),
          const Divider(),

          // ── 콘텐츠 관리 ──
          _SectionHeader('콘텐츠 관리'),
          ListTile(
            leading: const Icon(Icons.label_outlined),
            title: const Text('태그 관리'),
            subtitle: const Text('Work·Secret 태그 추가/삭제'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const TagManagementScreen()),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.people_outline),
            title: const Text('인물 관리'),
            subtitle: const Text('Secret 보관함 인물 목록 관리'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const PeopleManagementScreen()),
            ),
          ),
          const Divider(),

          // ── 저장소 ──
          _SectionHeader('저장소'),
          _StorageCard(breakdown: _storage, onRefresh: _onRefreshStorage),
          // 저장소 위치 선택 (Android만)
          if (Platform.isAndroid)
            ListTile(
              leading: const Icon(Icons.folder_outlined),
              title: const Text('저장 위치'),
              subtitle: Text(
                _storageLocation == StorageLocation.internal
                    ? '내부 저장소 (앱 전용, 보안 우수)'
                    : '외부 저장소 (SD카드/공유폴더)',
              ),
              trailing: DropdownButton<StorageLocation>(
                value: _storageLocation,
                underline: const SizedBox.shrink(),
                items: const [
                  DropdownMenuItem(
                    value: StorageLocation.internal,
                    child: Text('내부'),
                  ),
                  DropdownMenuItem(
                    value: StorageLocation.external,
                    child: Text('외부'),
                  ),
                ],
                onChanged: (v) async {
                  if (v == null) return;
                  await StorageService.setStorageLocation(v);
                  setState(() => _storageLocation = v);
                },
              ),
            ),
          // 사진 저장 품질
          ListTile(
            leading: const Icon(Icons.high_quality_outlined),
            title: const Text('사진 저장 품질'),
            subtitle: Text(_photoQuality.desc),
            trailing: DropdownButton<PhotoQuality>(
              value: _photoQuality,
              underline: const SizedBox.shrink(),
              items: PhotoQuality.values
                  .map((q) => DropdownMenuItem(value: q, child: Text(q.label)))
                  .toList(),
              onChanged: (v) async {
                if (v == null) return;
                await StorageService.setPhotoQuality(v);
                setState(() => _photoQuality = v);
              },
            ),
          ),
          ListTile(
            leading: const Icon(
              Icons.delete_forever_outlined,
              color: Colors.red,
            ),
            title: const Text('저장소 초기화', style: TextStyle(color: Colors.red)),
            subtitle: const Text('모든 미디어 및 DB 삭제'),
            onTap: _onClearStorage,
          ),
          const Divider(),

          // ── Google Drive ──
          _SectionHeader('Google Drive'),
          if (!_driveConnected)
            ListTile(
              leading: const Icon(Icons.cloud_outlined),
              title: const Text('Google Drive 연동'),
              subtitle: const Text('탭하여 Google 계정 연결'),
              trailing: const Icon(Icons.chevron_right),
              onTap: _onDriveConnect,
            )
          else ...[
            ref
                .watch(driveAccountInfoProvider)
                .when(
                  loading: () => ListTile(
                    leading: Icon(
                      Icons.cloud_done,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                    title: const Text('Google Drive 연결됨'),
                    subtitle: const Text('탭하여 연결 해제'),
                    onTap: _onDriveDisconnect,
                  ),
                  error: (e, st) => const SizedBox.shrink(),
                  data: (info) => ListTile(
                    leading: Icon(
                      Icons.cloud_done,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                    title: Text(
                      info?.displayName.isNotEmpty == true
                          ? info!.displayName
                          : 'Google Drive 연결됨',
                    ),
                    subtitle: Text(info?.email ?? '탭하여 연결 해제'),
                    onTap: _onDriveDisconnect,
                  ),
                ),
            ListTile(
              leading: _driveSyncing
                  ? const SizedBox(
                      width: 24,
                      height: 24,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.sync),
              title: const Text('지금 동기화'),
              subtitle: const Text('미업로드 파일 Drive에 업로드'),
              onTap: _driveSyncing ? null : _onDriveSync,
            ),
          ],
          const Divider(),

          // ── 플랜 ──
          _SectionHeader('플랜'),
          ListTile(
            leading: const Icon(Icons.workspace_premium),
            title: const Text('현재 플랜: Free'),
            subtitle: const Text('Drive 연동, AI 태깅, PDF 보고서 무제한'),
            trailing: FilledButton(
              onPressed: () => _showProUpgradeDialog(context),
              child: const Text('Pro'),
            ),
          ),
          const Divider(),

          // ── 앱 정보 ──
          _SectionHeader('앱 정보'),
          ListTile(
            leading: const Icon(Icons.info_outline),
            title: const Text('버전'),
            trailing: Text(
              _version.isEmpty ? '...' : _version,
              style: TextStyle(
                color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.6),
              ),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.numbers_outlined),
            title: const Text('빌드 번호'),
            trailing: Text(
              _buildNumber.isEmpty ? '...' : _buildNumber,
              style: TextStyle(
                color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.6),
              ),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.privacy_tip_outlined),
            title: const Text('개인정보 처리방침'),
            trailing: const Icon(Icons.open_in_new, size: 18),
            onTap: () => _launchPrivacyPolicy(),
          ),
          ListTile(
            leading: const Icon(Icons.help_outline),
            title: const Text('문의 및 피드백'),
            trailing: const Icon(Icons.open_in_new, size: 18),
            onTap: () => _launchFeedback(),
          ),
        ],
      ),
    );
  }

  void _showProUpgradeDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Memorix Pro'),
        content: const Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _ProFeatureItem(Icons.cloud, 'Google Drive 자동 동기화'),
            _ProFeatureItem(Icons.auto_awesome, 'AI 태그 자동 입력 무제한'),
            _ProFeatureItem(Icons.picture_as_pdf, 'PDF 보고서 무제한'),
            _ProFeatureItem(Icons.lock, 'Secret 보관함 항상 잠금'),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('닫기'),
          ),
          FilledButton(onPressed: () {}, child: const Text('구독하기')),
        ],
      ),
    );
  }
}

class _ProFeatureItem extends StatelessWidget {
  final IconData icon;
  final String label;
  const _ProFeatureItem(this.icon, this.label);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Icon(icon, size: 20, color: Theme.of(context).colorScheme.primary),
          const SizedBox(width: 12),
          Text(label),
        ],
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;
  const _SectionHeader(this.title);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
      child: Text(
        title,
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: Theme.of(context).colorScheme.primary,
        ),
      ),
    );
  }
}

class _StorageCard extends StatelessWidget {
  final StorageBreakdown? breakdown;
  final VoidCallback onRefresh;
  const _StorageCard({required this.breakdown, required this.onRefresh});

  @override
  Widget build(BuildContext context) {
    if (breakdown == null) {
      return const ListTile(
        leading: Icon(Icons.storage),
        title: Text('저장 공간 계산 중...'),
        trailing: SizedBox(
          width: 18,
          height: 18,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
      );
    }
    final b = breakdown!;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                '총 ${b.totalLabel}',
                style: const TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 15,
                ),
              ),
              TextButton.icon(
                onPressed: onRefresh,
                icon: const Icon(Icons.refresh, size: 16),
                label: const Text('새로고침', style: TextStyle(fontSize: 12)),
              ),
            ],
          ),
          const SizedBox(height: 8),
          // 시각적 바
          _StorageBar(breakdown: b),
          const SizedBox(height: 10),
          // 범례
          Wrap(
            spacing: 16,
            runSpacing: 4,
            children: [
              _legend(context, Colors.blue[400]!, '사진', b.photosLabel),
              _legend(context, Colors.orange[400]!, '영상', b.videosLabel),
              _legend(context, Colors.green[400]!, '문서', b.documentsLabel),
              _legend(context, Colors.purple[400]!, '보고서', b.reportsLabel),
              _legend(context, AppColors.mutedTextLight, 'DB', b.dbLabel),
            ],
          ),
        ],
      ),
    );
  }

  Widget _legend(BuildContext context, Color color, String name, String size) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 10,
          height: 10,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 4),
        Text('$name $size', style: const TextStyle(fontSize: 11)),
      ],
    );
  }
}

class _StorageBar extends StatelessWidget {
  final StorageBreakdown breakdown;
  const _StorageBar({required this.breakdown});

  @override
  Widget build(BuildContext context) {
    final total = breakdown.total;
    if (total == 0) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(4),
        child: LinearProgressIndicator(
          value: 0,
          minHeight: 10,
          backgroundColor: Theme.of(
            context,
          ).colorScheme.surfaceContainerHighest,
        ),
      );
    }
    final segments = [
      (breakdown.photos, Colors.blue[400]!),
      (breakdown.videos, Colors.orange[400]!),
      (breakdown.documents, Colors.green[400]!),
      (breakdown.reports, Colors.purple[400]!),
      (breakdown.db, AppColors.mutedTextLight),
    ];
    return ClipRRect(
      borderRadius: BorderRadius.circular(4),
      child: SizedBox(
        height: 10,
        child: Row(
          children: segments
              .where((s) => s.$1 > 0)
              .map(
                (s) => Flexible(
                  flex: s.$1,
                  child: Container(color: s.$2),
                ),
              )
              .toList(),
        ),
      ),
    );
  }
}
