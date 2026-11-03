import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';
import 'otp_page.dart';

/// Code PIN oublié : envoi d’un code SMS, puis création d’un nouveau PIN.
class ForgotPinPage extends StatefulWidget {
  const ForgotPinPage({super.key});

  @override
  State<ForgotPinPage> createState() => _ForgotPinPageState();
}

class _ForgotPinPageState extends State<ForgotPinPage> {
  final _phone = TextEditingController();
  String? _error;
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    final known = context.store.knownPhone ?? context.session.user?.phone;
    if (known != null) _phone.text = formatPhone(known);
  }

  @override
  void dispose() {
    _phone.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    final err = Validators.phone(_phone.text);
    if (err != null) return setState(() => _error = err);
    setState(() => _loading = true);
    final phone = phoneDigits(_phone.text);
    try {
      await context.repos.auth.requestOtp(phone, purpose: OtpPurpose.resetPin);
      if (!mounted) return;
      await context.push(AppRoutes.otp, OtpArgs(phone: phone, purpose: OtpFlow.resetPin));
    } catch (e) {
      if (mounted) setState(() => _error = errorMessage(e));
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
          children: [
            Text('Code PIN oublié', style: AzText.display.copyWith(fontSize: 28)),
            const SizedBox(height: 8),
            Text('Nous vous envoyons un code par SMS pour créer un nouveau code PIN.', style: AzText.body.copyWith(color: AzColors.muted)),
            const SizedBox(height: 24),
            AzPhoneField(controller: _phone, error: _error),
            const SizedBox(height: 24),
            AzButton('Recevoir le code', loading: _loading, onPressed: _send),
            const SizedBox(height: 16),
            // Numéro et horaires fournis par le serveur : absents = bandeau masqué.
            ValueListenableBuilder<ContactInfo>(
              valueListenable: ContactDirectory.info,
              builder: (context, info, _) {
                final phone = info.primaryPhone;
                if (phone == null) return const SizedBox.shrink();
                final hours = info.availabilityText;
                return AzBanner(
                  kind: AzBannerKind.info,
                  text: 'Besoin d’aide ? Contactez notre service client au ${formatPhone(phone)}${hours == null ? '.' : ' ($hours).'}',
                  onTap: () => Launch.phone(context),
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}
