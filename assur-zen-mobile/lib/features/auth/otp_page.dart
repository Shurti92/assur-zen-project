import 'dart:async';

import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/network/api_exception.dart';
import '../../core/ui.dart';
import '../../data/repositories/repositories.dart';
import '../shell/main_shell.dart';

/// Contexte de l’écran : double authentification à la connexion, inscription ou PIN oublié.
enum OtpFlow { login, register, resetPin }

class OtpArgs {
  const OtpArgs({
    required this.phone,
    required this.purpose,
    this.challengeToken,
    this.maskedPhone,
    this.fullName,
    this.pin,
    this.marketingConsent = false,
    this.returnToCaller = false,
  });

  final String phone;
  final OtpFlow purpose;

  /// Connexion avec double authentification : jeton du défi et numéro masqué.
  final String? challengeToken;
  final String? maskedPhone;
  final String? fullName;
  final String? pin;
  final bool marketingConsent;
  final bool returnToCaller;
}

/// Vérification du numéro par code SMS (décision 4.3) et réinitialisation du PIN.
class OtpPage extends StatefulWidget {
  const OtpPage({super.key, required this.args});

  final OtpArgs args;

  @override
  State<OtpPage> createState() => _OtpPageState();
}

class _OtpPageState extends State<OtpPage> {
  final _code = TextEditingController();
  final _newPin = TextEditingController();
  final _confirmPin = TextEditingController();
  String? _error;
  String? _pinError;
  bool _loading = false;
  bool _verified = false;
  int _countdown = 45;
  Timer? _timer;

  bool get _isReset => widget.args.purpose == OtpFlow.resetPin;
  bool get _isLogin => widget.args.purpose == OtpFlow.login;

  String get _destination =>
      _isLogin && widget.args.maskedPhone != null ? widget.args.maskedPhone! : '+225 ${formatPhone(widget.args.phone)}';

  @override
  void initState() {
    super.initState();
    _startTimer();
  }

  void _startTimer() {
    _timer?.cancel();
    setState(() => _countdown = 45);
    _timer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (!mounted) return t.cancel();
      setState(() => _countdown--);
      if (_countdown <= 0) t.cancel();
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _code.dispose();
    _newPin.dispose();
    _confirmPin.dispose();
    super.dispose();
  }

  Future<void> _resend() async {
    try {
      final auth = context.repos.auth;
      if (_isLogin) {
        await auth.resendLoginOtp(widget.args.challengeToken ?? '');
      } else {
        await auth.requestOtp(
          widget.args.phone,
          purpose: _isReset ? OtpPurpose.resetPin : OtpPurpose.register,
        );
      }
      if (!mounted) return;
      showAzSnack(context, 'Nouveau code envoyé au $_destination.');
      _startTimer();
    } catch (e) {
      if (mounted) showAzSnack(context, errorMessage(e));
    }
  }

  Future<void> _verify() async {
    final err = Validators.otp(_code.text);
    if (err != null) return setState(() => _error = err);
    setState(() {
      _loading = true;
      _error = null;
    });
    final repos = context.repos;
    final session = context.session;
    try {
      if (_isLogin) {
        final s = await repos.auth.verifyLoginOtp(
          challengeToken: widget.args.challengeToken ?? '',
          code: _code.text,
          phone: widget.args.phone,
        );
        await session.signIn(s);
        if (!mounted) return;
        if (widget.args.returnToCaller) {
          context.pop(true);
        } else {
          context.resetTo(AppRoutes.shell, ShellTab.home);
        }
        return;
      }
      if (_isReset) {
        // Le code est vérifié par le serveur avec le nouveau PIN (une seule fois).
        if (mounted) setState(() => _verified = true);
        return;
      }
      final verificationToken = await repos.auth.verifyOtp(phone: widget.args.phone, code: _code.text);
      final s = await repos.auth.register(
        fullName: widget.args.fullName ?? '',
        phone: widget.args.phone,
        pin: widget.args.pin ?? '',
        verificationToken: verificationToken,
      );
      await session.signIn(s);
      if (!mounted) return;
      final found = (s.linkedCount ?? 0) > 0;
      if (widget.args.returnToCaller) {
        showAzSnack(context, 'Compte créé. Poursuivons votre souscription.');
        context.pop(true);
      } else {
        context.resetTo(AppRoutes.shell, ShellTab.home);
        showAzSnack(
          context,
          found ? 'Compte créé. Nous avons retrouvé vos contrats AssurZen.' : 'Compte créé. Bienvenue chez AssurZen !',
        );
      }
    } catch (e) {
      if (mounted) setState(() => _error = errorMessage(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _savePin() async {
    final err = Validators.newPin(_newPin.text) ?? (_newPin.text != _confirmPin.text ? 'Les deux codes ne correspondent pas.' : null);
    if (err != null) return setState(() => _pinError = err);
    setState(() => _loading = true);
    try {
      await context.repos.auth.resetPin(phone: widget.args.phone, code: _code.text, newPin: _newPin.text);
      if (!mounted) return;
      showAzSnack(context, 'Votre nouveau code PIN est enregistré.');
      Navigator.of(context).popUntil((r) => r.settings.name == AppRoutes.login || r.isFirst);
    } catch (e) {
      if (!mounted) return;
      // Code SMS refusé (ou consommé par un PIN refusé) : retour à la saisie du code.
      if (e is ApiException && ((e.code ?? '').contains('OTP') || e.code == 'PIN_REUSED')) {
        setState(() {
          _verified = false;
          _code.clear();
          _error = e.message;
        });
      } else {
        setState(() => _pinError = errorMessage(e));
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: const AzTopBar(),
      body: SafeArea(
        top: false,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(24, 4, 24, 24),
          children: _verified ? _pinStep() : _codeStep(),
        ),
      ),
    );
  }

  List<Widget> _codeStep() => [
        Text('Vérifiez votre numéro', style: AzText.display.copyWith(fontSize: 28)),
        const SizedBox(height: 8),
        Text('Saisissez le code à 6 chiffres envoyé par SMS au $_destination.',
            style: AzText.body.copyWith(color: AzColors.muted)),
        const SizedBox(height: 24),
        AzCodeField(
          controller: _code,
          label: 'Code de vérification',
          obscure: false,
          autofocus: true,
          error: _error,
          onCompleted: (_) => _verify(),
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Text('Code non reçu ?', style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
            if (_countdown > 0)
              Padding(
                padding: const EdgeInsets.only(left: 6),
                child: Text('Renvoyer dans 0:${_countdown.toString().padLeft(2, '0')}', style: AzText.bodyMedium),
              )
            else
              AzTextLink('Renvoyer le code', onPressed: _resend),
          ],
        ),
        const SizedBox(height: 24),
        AzButton('Valider', loading: _loading, onPressed: _verify),
      ];

  List<Widget> _pinStep() => [
        Text('Nouveau code PIN', style: AzText.display.copyWith(fontSize: 28)),
        const SizedBox(height: 8),
        Text('Choisissez un code à 6 chiffres que vous seul connaissez.', style: AzText.body.copyWith(color: AzColors.muted)),
        const SizedBox(height: 24),
        AzCodeField(controller: _newPin, label: 'Nouveau code PIN', autofocus: true),
        const SizedBox(height: 16),
        AzCodeField(controller: _confirmPin, label: 'Confirmez le code PIN', error: _pinError),
        const SizedBox(height: 24),
        AzButton('Enregistrer', loading: _loading, onPressed: _savePin),
      ];
}
