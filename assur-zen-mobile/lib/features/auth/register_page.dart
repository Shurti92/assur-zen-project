import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/repositories/repositories.dart';
import 'auth_layout.dart';
import 'otp_page.dart';

class RegisterArgs {
  const RegisterArgs({this.returnToCaller = false});

  /// `true` : après inscription, on revient à l’écran appelant (devis en cours).
  final bool returnToCaller;
}

/// Création de compte : uniquement les données nécessaires (décision 8.3).
class RegisterPage extends StatefulWidget {
  const RegisterPage({super.key, required this.args});

  final RegisterArgs args;

  @override
  State<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends State<RegisterPage> {
  final _name = TextEditingController();
  final _phone = TextEditingController();
  final _pin = TextEditingController();
  bool _cgu = false;
  bool _marketing = false;
  bool _loading = false;
  final Map<String, String?> _errors = {};

  @override
  void dispose() {
    _name.dispose();
    _phone.dispose();
    _pin.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _errors
        ..clear()
        ..['name'] = Validators.fullName(_name.text)
        ..['phone'] = Validators.phone(_phone.text)
        ..['pin'] = Validators.newPin(_pin.text)
        ..['cgu'] = _cgu ? null : 'Vous devez accepter les conditions pour créer un compte.';
    });
    if (_errors.values.any((e) => e != null)) return;

    setState(() => _loading = true);
    final phone = phoneDigits(_phone.text);
    try {
      await context.repos.auth.requestOtp(phone, purpose: OtpPurpose.register);
      if (!mounted) return;
      final ok = await context.push<bool>(
        AppRoutes.otp,
        OtpArgs(
          phone: phone,
          purpose: OtpFlow.register,
          fullName: _name.text.trim(),
          pin: _pin.text,
          marketingConsent: _marketing,
          returnToCaller: widget.args.returnToCaller,
        ),
      );
      if (ok == true && mounted && widget.args.returnToCaller) context.pop(true);
    } catch (e) {
      if (mounted) showAzSnack(context, errorMessage(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AuthLayout(
      title: 'Créer un compte',
      subtitle: widget.args.returnToCaller
          ? 'Votre devis est conservé. Créez votre compte pour souscrire.'
          : 'Seules les informations nécessaires à vos contrats.',
      showBack: true,
      step: 1,
      steps: 2,
      titleSize: 24,
      children: [
        AzTextField(
          label: 'Nom et prénoms',
          controller: _name,
          hint: 'Ex. Koné Awa',
          error: _errors['name'],
          capitalization: TextCapitalization.words,
          autofillHints: const [AutofillHints.name],
        ),
        AzPhoneField(controller: _phone, error: _errors['phone']),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            AzCodeField(
              controller: _pin,
              label: 'Créez votre code PIN (6 chiffres)',
              error: _errors['pin'],
            ),
            const SizedBox(height: 6),
            Text('Évitez les codes trop simples comme 000000 ou 123456.', style: AzText.caption.copyWith(fontWeight: FontWeight.w500)),
          ],
        ),
        AzCheckRow(
          value: _cgu,
          onChanged: (v) => setState(() {
            _cgu = v;
            _errors['cgu'] = null;
          }),
          child: const Text('J’accepte les Conditions générales d’utilisation et la Politique de confidentialité.'),
        ),
        AzCheckRow(
          value: _marketing,
          onChanged: (v) => setState(() => _marketing = v),
          child: const Text('J’accepte de recevoir les offres AssurZen (2 messages par mois au maximum). Facultatif.'),
        ),
        if (_errors['cgu'] != null) AzBanner(kind: AzBannerKind.error, text: _errors['cgu']!),
        AuthPrimaryButton('Continuer', loading: _loading, onPressed: _submit),
      ],
    );
  }
}
