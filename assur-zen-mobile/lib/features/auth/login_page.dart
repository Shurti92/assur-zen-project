import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/security/biometric.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';
import 'otp_page.dart';
import '../shell/main_shell.dart';

/// Connexion : numéro + code PIN à 6 chiffres, empreinte si l’appareil est connu.
class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _phone = TextEditingController();
  final _pin = TextEditingController();
  String? _phoneError;
  String? _pinError;
  String? _formError;
  bool _loading = false;
  bool _bioAvailable = false;

  @override
  void initState() {
    super.initState();
    final known = context.store.knownPhone;
    if (known != null) _phone.text = formatPhone(known);
    if (known != null && context.store.biometricEnabled) {
      Biometric.isAvailable().then((ok) {
        if (mounted) setState(() => _bioAvailable = ok);
      });
    }
  }

  @override
  void dispose() {
    _phone.dispose();
    _pin.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _phoneError = Validators.phone(_phone.text);
      _pinError = Validators.pin(_pin.text);
      _formError = null;
    });
    if (_phoneError != null || _pinError != null) return;
    setState(() => _loading = true);
    try {
      final phone = phoneDigits(_phone.text);
      final result = await context.repos.auth.login(phone: phone, pin: _pin.text);
      if (!mounted) return;
      switch (result) {
        case LoginSuccess(:final session):
          await _onSignedIn(session);
        case LoginOtpChallenge():
          // Double authentification : code SMS à saisir avant d’ouvrir la session.
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
          if (ok == true && mounted) await _afterSignIn(askBiometric: true, wasEnabled: context.session.biometricEnabled);
      }
    } catch (e) {
      if (mounted) setState(() => _formError = errorMessage(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _biometric() async {
    final ok = await Biometric.authenticate('Confirmez votre identité pour accéder à AssurZen');
    if (!ok || !mounted) return;
    final token = await context.session.refreshToken();
    if (!mounted) return;
    if (token == null) {
      setState(() => _formError = 'Saisissez votre code PIN pour cette connexion.');
      return;
    }
    setState(() => _loading = true);
    try {
      final session = await context.repos.auth.resumeSession(token, phone: context.store.knownPhone);
      if (mounted) await _onSignedIn(session, askBiometric: false);
    } catch (e) {
      if (mounted) setState(() => _formError = errorMessage(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _onSignedIn(Session session, {bool askBiometric = true}) async {
    final s = context.session;
    final wasEnabled = s.biometricEnabled;
    await s.signIn(session);
    if (!mounted) return;
    await _afterSignIn(askBiometric: askBiometric, wasEnabled: wasEnabled);
  }

  Future<void> _afterSignIn({required bool askBiometric, required bool wasEnabled}) async {
    // Le choix du mode de déverrouillage est proposé une seule fois, à l'arrivée sur l'accueil.
    if (mounted) context.resetTo(AppRoutes.shell, ShellTab.home);
  }

  @override
  Widget build(BuildContext context) {
    final canGoBack = Navigator.of(context).canPop();
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AzTopBar(showBack: canGoBack, onBack: canGoBack ? null : () => context.resetTo(AppRoutes.welcome)),
      body: SafeArea(
        top: false,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(24, 4, 24, 24),
          children: [
            Text('Connexion', style: AzText.display.copyWith(fontSize: 28)),
            const SizedBox(height: 8),
            Text('Saisissez le numéro associé à votre compte AssurZen.', style: AzText.body.copyWith(color: AzColors.muted)),
            const SizedBox(height: 24),
            AzPhoneField(controller: _phone, error: _phoneError, onChanged: (_) => setState(() => _phoneError = null)),
            const SizedBox(height: 18),
            AzCodeField(
              controller: _pin,
              label: 'Code PIN (6 chiffres)',
              error: _pinError,
              onChanged: (_) => setState(() => _pinError = null),
              onCompleted: (_) => _submit(),
            ),
            Align(
              alignment: Alignment.centerRight,
              child: AzTextLink('Code PIN oublié ?', onPressed: () => context.push(AppRoutes.forgotPin)),
            ),
            if (_formError != null) ...[
              const SizedBox(height: 4),
              AzBanner(kind: AzBannerKind.error, text: _formError!),
            ],
            const SizedBox(height: 20),
            AzButton('Se connecter', loading: _loading, onPressed: _submit),
            if (_bioAvailable) ...[
              const SizedBox(height: 12),
              AzButton.secondary('Se connecter avec l’empreinte', icon: Icons.fingerprint, onPressed: _loading ? null : _biometric),
            ],
            const SizedBox(height: 20),
            // Wrap : avec le texte agrandi, le lien passe à la ligne au lieu de déborder.
            Wrap(
              alignment: WrapAlignment.center,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Text('Pas encore de compte ?', style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
                AzTextLink('Créer un compte', onPressed: () => context.push(AppRoutes.register)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
