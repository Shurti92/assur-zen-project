import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/security/biometric.dart';
import '../../core/security/pattern_lock.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';
import 'otp_page.dart';

/// Session verrouillée (inactivité de 5 minutes ou jeton expiré).
/// Les formulaires en cours restent en mémoire sous cet écran.
class LockPage extends StatefulWidget {
  const LockPage({super.key});

  @override
  State<LockPage> createState() => _LockPageState();
}

class _LockPageState extends State<LockPage> {
  final _pin = TextEditingController();
  String? _error;
  bool _loading = false;
  bool _bio = false;
  bool _usePattern = false;
  bool _patternError = false;
  int _patternFails = 0;

  @override
  void initState() {
    super.initState();
    // Si un schéma est défini, il est proposé en premier (le PIN reste disponible).
    _usePattern = context.session.patternEnabled;
    if (context.session.biometricEnabled) {
      Biometric.isAvailable().then((ok) {
        if (!mounted) return;
        setState(() => _bio = ok);
        if (ok) _biometric();
      });
    }
  }

  @override
  void dispose() {
    _pin.dispose();
    super.dispose();
  }

  Future<void> _unlockWithPin() async {
    final err = Validators.pin(_pin.text);
    if (err != null) return setState(() => _error = err);
    final session = context.session;
    final phone = session.user?.phone ?? context.store.knownPhone ?? '';
    setState(() => _loading = true);
    try {
      final result = await context.repos.auth.login(phone: phone, pin: _pin.text);
      if (!mounted) return;
      switch (result) {
        case LoginSuccess(session: final s):
          await session.signIn(s);
          if (mounted) context.pop();
        case LoginOtpChallenge():
          final ok = await context.push<bool>(
            AppRoutes.otp,
            OtpArgs(
              phone: phone,
              purpose: OtpFlow.login,
              challengeToken: result.challengeToken,
              maskedPhone: result.maskedPhone,
              returnToCaller: true,
            ),
          );
          if (ok == true && mounted) context.pop();
      }
    } catch (e) {
      if (mounted) setState(() => _error = errorMessage(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _biometric() async {
    final ok = await Biometric.authenticate('Déverrouillez AssurZen');
    if (!ok || !mounted) return;
    context.session.unlock();
    context.pop();
  }

  Future<void> _onPattern(List<int> dots) async {
    if (PatternLock.validate(dots) == null && await PatternLock.verify(dots)) {
      if (!mounted) return;
      context.session.unlock();
      context.pop();
      return;
    }
    if (!mounted) return;
    _patternFails++;
    setState(() {
      _patternError = true;
      // Après 5 échecs, retour obligatoire au code PIN.
      if (_patternFails >= PatternLock.maxAttempts) _usePattern = false;
    });
    Future.delayed(const Duration(milliseconds: 700), () {
      if (mounted) setState(() => _patternError = false);
    });
  }

  Future<void> _logout() async {
    await context.session.signOut();
    if (mounted) context.resetTo(AppRoutes.login);
  }

  @override
  Widget build(BuildContext context) {
    final session0 = context.session;
    return PopScope(
      canPop: false,
      child: Scaffold(
        backgroundColor: Colors.white,
        body: SafeArea(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(24, 48, 24, 24),
            children: [
              const Center(child: AzLogo(height: 56)),
              const SizedBox(height: 32),
              Container(
                width: 56,
                height: 56,
                alignment: Alignment.center,
                decoration: BoxDecoration(color: AzColors.infoBg, borderRadius: BorderRadius.circular(16)),
                child: const Icon(Icons.lock_outline, color: AzColors.info),
              ),
              const SizedBox(height: 16),
              const Text('Votre session est verrouillée', style: AzText.headline),
              const SizedBox(height: 8),
              Text(
                'Pour protéger votre compte, saisissez à nouveau votre code PIN. Les informations en cours de saisie sont conservées.',
                style: AzText.body.copyWith(color: AzColors.muted),
              ),
              const SizedBox(height: 24),
              if (_usePattern) ...[
                Text(
                  _patternError ? 'Schéma incorrect, réessayez.' : 'Dessinez votre schéma.',
                  style: AzText.bodyMedium.copyWith(color: _patternError ? AzColors.error : AzColors.muted),
                ),
                const SizedBox(height: 12),
                AzPatternPad(error: _patternError, onCompleted: _onPattern),
                const SizedBox(height: 12),
                AzButton.secondary('Utiliser mon code PIN', icon: Icons.pin_outlined, onPressed: () => setState(() => _usePattern = false)),
              ] else ...[
                AzCodeField(controller: _pin, label: 'Code PIN', autofocus: !_bio, error: _error, onCompleted: (_) => _unlockWithPin()),
                const SizedBox(height: 20),
                AzButton('Déverrouiller', loading: _loading, onPressed: _unlockWithPin),
                if (session0.patternEnabled && _patternFails < PatternLock.maxAttempts) ...[
                  const SizedBox(height: 12),
                  AzButton.secondary('Utiliser mon schéma', icon: Icons.pattern, onPressed: () => setState(() => _usePattern = true)),
                ],
              ],
              if (_bio) ...[
                const SizedBox(height: 12),
                AzButton.secondary('Utiliser l’empreinte', icon: Icons.fingerprint, onPressed: _biometric),
              ],
              const SizedBox(height: 12),
              AzButton.ghost('Se déconnecter', onPressed: _logout),
            ],
          ),
        ),
      ),
    );
  }
}
