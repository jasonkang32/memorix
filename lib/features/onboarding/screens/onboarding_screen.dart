import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

const _storage = FlutterSecureStorage();
const _onboardingKey = 'onboarding_done';

final onboardingDoneProvider = FutureProvider<bool>((ref) async {
  final v = await _storage.read(key: _onboardingKey);
  return v == '1';
});

Future<void> markOnboardingDone() =>
    _storage.write(key: _onboardingKey, value: '1');

class OnboardingScreen extends StatefulWidget {
  final VoidCallback onDone;
  const OnboardingScreen({super.key, required this.onDone});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final _ctrl = PageController();
  int _page = 0;

  static const _pages = [
    _OnboardPage(
      emoji: '🔒',
      title: '내 사진, 밖에 안 나갑니다',
      desc:
          '갤러리 대신 메모릭스(Memorix)에 보관하세요.\n기기 내부에만 저장되어 외부에 노출되지 않고,\n내가 원할 때 바로 찾을 수 있습니다.',
      gradientColors: [Color(0xFF00C896), Color(0xFF00897B)],
    ),
    _OnboardPage(
      emoji: '💼',
      title: 'Work · Personal 완전 분리',
      desc: '업무 현장 사진과 개인 사진을\n하나의 앱에서 완전히 분리해 관리합니다.',
      gradientColors: [Color(0xFF1A73E8), Color(0xFF7B61FF)],
    ),
    _OnboardPage(
      emoji: '🤖',
      title: 'AI 자동 태그',
      desc: '촬영 즉시 AI가 내용을 분석해\n태그를 자동으로 붙여드립니다.\n나중에 한 번에 검색하세요.',
      gradientColors: [Color(0xFF7B61FF), Color(0xFFFF6B9D)],
    ),
    _OnboardPage(
      emoji: '📄',
      title: '현장에서 바로 보고서',
      desc: '현장 사진을 선택하면\n출장보고서 PDF를 즉시 생성합니다.\n앱 밖으로 나갈 필요 없습니다.',
      gradientColors: [Color(0xFFFF6B35), Color(0xFFFFB347)],
    ),
  ];

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _done() async {
    await markOnboardingDone();
    widget.onDone();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          PageView.builder(
            controller: _ctrl,
            itemCount: _pages.length,
            onPageChanged: (i) => setState(() => _page = i),
            itemBuilder: (context, i) => _pages[i],
          ),
          // 하단 컨트롤
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: SafeArea(
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 16,
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    // 도트 인디케이터
                    Row(
                      children: List.generate(
                        _pages.length,
                        (i) => AnimatedContainer(
                          duration: const Duration(milliseconds: 250),
                          margin: const EdgeInsets.symmetric(horizontal: 3),
                          width: i == _page ? 20 : 8,
                          height: 8,
                          decoration: BoxDecoration(
                            color: i == _page
                                ? Colors.white
                                : Colors.white.withValues(alpha: 0.4),
                            borderRadius: BorderRadius.circular(4),
                          ),
                        ),
                      ),
                    ),
                    // 버튼
                    _page < _pages.length - 1
                        ? FilledButton(
                            style: FilledButton.styleFrom(
                              backgroundColor: Colors.white.withValues(
                                alpha: 0.25,
                              ),
                              padding: const EdgeInsets.symmetric(
                                horizontal: 28,
                                vertical: 12,
                              ),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(14),
                              ),
                            ),
                            onPressed: () => _ctrl.nextPage(
                              duration: const Duration(milliseconds: 300),
                              curve: Curves.easeInOut,
                            ),
                            child: const Text(
                              '다음',
                              style: TextStyle(
                                color: Colors.white,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          )
                        : FilledButton(
                            style: FilledButton.styleFrom(
                              backgroundColor: Colors.white,
                              foregroundColor: const Color(0xFF00C896),
                              padding: const EdgeInsets.symmetric(
                                horizontal: 32,
                                vertical: 12,
                              ),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(14),
                              ),
                            ),
                            onPressed: _done,
                            child: const Text(
                              '시작하기',
                              style: TextStyle(fontWeight: FontWeight.w800),
                            ),
                          ),
                  ],
                ),
              ),
            ),
          ),
          // Skip 버튼
          Positioned(
            top: 0,
            right: 0,
            child: SafeArea(
              child: _page < _pages.length - 1
                  ? TextButton(
                      onPressed: _done,
                      child: const Text(
                        '건너뛰기',
                        style: TextStyle(color: Colors.white70),
                      ),
                    )
                  : const SizedBox.shrink(),
            ),
          ),
        ],
      ),
    );
  }
}

class _OnboardPage extends StatelessWidget {
  final String emoji;
  final String title;
  final String desc;
  final List<Color> gradientColors;

  const _OnboardPage({
    required this.emoji,
    required this.title,
    required this.desc,
    required this.gradientColors,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: gradientColors,
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(32, 60, 32, 100),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 이모지 + 글로우 배경
              Container(
                width: 100,
                height: 100,
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(28),
                ),
                child: Center(
                  child: Text(emoji, style: const TextStyle(fontSize: 52)),
                ),
              ),
              const Spacer(),
              Text(
                title,
                style: const TextStyle(
                  fontSize: 30,
                  fontWeight: FontWeight.w800,
                  color: Colors.white,
                  height: 1.2,
                  letterSpacing: -0.5,
                ),
              ),
              const SizedBox(height: 16),
              Text(
                desc,
                style: const TextStyle(
                  fontSize: 15,
                  color: Colors.white,
                  height: 1.7,
                ),
              ),
              const Spacer(flex: 2),
            ],
          ),
        ),
      ),
    );
  }
}
