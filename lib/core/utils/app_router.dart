import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../features/auth/providers/secret_lock_provider.dart';
import '../../features/home/screens/home_screen.dart';
import '../../features/work/screens/work_screen.dart';
import '../../features/personal/screens/personal_screen.dart';
import '../../features/settings/screens/settings_screen.dart';
import '../../shared/screens/media_detail_screen.dart';
import '../../shared/models/media_item.dart';

final appRouter = GoRouter(
  // Work 중심: 첫 진입은 Work
  initialLocation: '/work',
  routes: [
    StatefulShellRoute.indexedStack(
      builder: (context, state, shell) => _ScaffoldWithNavBar(shell: shell),
      branches: [
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: '/home',
              builder: (context, state) => const HomeScreen(),
            ),
          ],
        ),
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: '/work',
              builder: (context, state) => const WorkScreen(),
              routes: [
                GoRoute(
                  path: 'detail',
                  builder: (context, state) {
                    final item = state.extra as MediaItem;
                    return MediaDetailScreen(items: [item], initialIndex: 0);
                  },
                ),
              ],
            ),
          ],
        ),
        StatefulShellBranch(
          routes: [
            // 'personal' 경로는 호환성 유지를 위해 둔다 (기존 카드/링크 대응)
            GoRoute(
              path: '/secret',
              builder: (context, state) => const PersonalScreen(),
            ),
            GoRoute(path: '/personal', redirect: (context, state) => '/secret'),
          ],
        ),
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: '/settings',
              builder: (context, state) => const SettingsScreen(),
            ),
          ],
        ),
      ],
    ),
  ],
);

class _ScaffoldWithNavBar extends ConsumerWidget {
  final StatefulNavigationShell shell;
  const _ScaffoldWithNavBar({required this.shell});

  static const _secretBranchIndex = 2;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      body: shell,
      bottomNavigationBar: SafeArea(
        bottom: true,
        child: NavigationBar(
          selectedIndex: shell.currentIndex,
          onDestinationSelected: (index) {
            // Secret 탭에서 다른 탭으로 빠져나갈 때 즉시 잠금
            if (shell.currentIndex == _secretBranchIndex &&
                index != _secretBranchIndex) {
              ref.read(secretLockProvider.notifier).lock();
            }
            shell.goBranch(index, initialLocation: index == shell.currentIndex);
          },
          destinations: const [
            NavigationDestination(
              icon: Icon(Icons.home_outlined),
              selectedIcon: Icon(Icons.home_rounded),
              label: 'Home',
            ),
            NavigationDestination(
              icon: Icon(Icons.work_outline_rounded),
              selectedIcon: Icon(Icons.work_rounded),
              label: 'Work',
            ),
            NavigationDestination(
              icon: Icon(Icons.lock_outline_rounded),
              selectedIcon: Icon(Icons.lock_rounded),
              label: 'Secret',
            ),
            NavigationDestination(
              icon: Icon(Icons.settings_outlined),
              selectedIcon: Icon(Icons.settings_rounded),
              label: '설정',
            ),
          ],
        ),
      ),
    );
  }
}
