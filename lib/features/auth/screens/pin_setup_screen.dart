import 'package:flutter/material.dart';
import '../../../core/services/auth_service.dart';

/// PIN 최초 설정 화면 (2단계: 입력 → 확인)
class PinSetupScreen extends StatefulWidget {
  const PinSetupScreen({super.key});

  @override
  State<PinSetupScreen> createState() => _PinSetupScreenState();
}

class _PinSetupScreenState extends State<PinSetupScreen> {
  final List<String> _digits = [];
  List<String>? _firstPin;
  String? _errorMsg;

  static const _pinLength = 6;

  bool get _isConfirmStep => _firstPin != null;

  void _onDigit(String d) {
    if (_digits.length >= _pinLength) return;
    setState(() {
      _digits.add(d);
      _errorMsg = null;
    });
    if (_digits.length == _pinLength) _onComplete();
  }

  void _onDelete() {
    if (_digits.isEmpty) return;
    setState(() => _digits.removeLast());
  }

  Future<void> _onComplete() async {
    if (!_isConfirmStep) {
      // 첫 번째 입력 완료
      final first = List<String>.from(_digits);
      setState(() {
        _firstPin = first;
        _digits.clear();
      });
    } else {
      // 확인 입력
      if (_digits.join() == _firstPin!.join()) {
        await AuthService.setPin(_digits.join());
        if (mounted) {
          Navigator.pop(context, true);
        }
      } else {
        setState(() {
          _firstPin = null;
          _digits.clear();
          _errorMsg = 'PIN이 일치하지 않습니다. 다시 시작하세요.';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('PIN 설정')),
      body: SafeArea(
        child: Column(
          children: [
            const Spacer(),
            Icon(Icons.lock_outline,
                size: 48, color: Theme.of(context).colorScheme.primary),
            const SizedBox(height: 16),
            Text(
              _isConfirmStep ? 'PIN을 다시 입력하세요' : '새 PIN을 입력하세요 (6자리)',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 32),
            // 도트
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(_pinLength, (i) {
                final filled = i < _digits.length;
                return AnimatedContainer(
                  duration: const Duration(milliseconds: 150),
                  margin: const EdgeInsets.symmetric(horizontal: 8),
                  width: 16,
                  height: 16,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: filled
                        ? Theme.of(context).colorScheme.primary
                        : Colors.grey[300],
                    border: Border.all(
                      color: filled
                          ? Theme.of(context).colorScheme.primary
                          : Colors.grey[400]!,
                    ),
                  ),
                );
              }),
            ),
            const SizedBox(height: 12),
            SizedBox(
              height: 20,
              child: _errorMsg != null
                  ? Text(_errorMsg!,
                      style: const TextStyle(color: Colors.red, fontSize: 13))
                  : null,
            ),
            const Spacer(),
            _Keypad(onDigit: _onDigit, onDelete: _onDelete),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }
}

class _Keypad extends StatelessWidget {
  final ValueChanged<String> onDigit;
  final VoidCallback onDelete;
  const _Keypad({required this.onDigit, required this.onDelete});

  static const _layout = [
    ['1', '2', '3'],
    ['4', '5', '6'],
    ['7', '8', '9'],
    ['', '0', '⌫'],
  ];

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 48),
      child: Column(
        children: _layout.map((row) {
          return Row(
            children: row.map((key) {
              if (key.isEmpty) return const Expanded(child: SizedBox());
              return Expanded(
                child: Padding(
                  padding: const EdgeInsets.all(6),
                  child: AspectRatio(
                    aspectRatio: 1.6,
                    child: TextButton(
                      style: TextButton.styleFrom(
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12)),
                        backgroundColor:
                            Theme.of(context).colorScheme.surfaceContainerHighest,
                      ),
                      onPressed: () =>
                          key == '⌫' ? onDelete() : onDigit(key),
                      child: Text(key,
                          style: Theme.of(context).textTheme.titleLarge),
                    ),
                  ),
                ),
              );
            }).toList(),
          );
        }).toList(),
      ),
    );
  }
}
