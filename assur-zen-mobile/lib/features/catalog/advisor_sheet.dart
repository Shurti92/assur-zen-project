import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';

/// Demande de rappel par un conseiller (délai annoncé seulement s'il vient du serveur).
Future<void> showAdvisorSheet(BuildContext context, {Product? product}) {
  return showAzSheet<void>(
    context,
    title: 'Être rappelé par un conseiller',
    builder: (_) => _AdvisorForm(product: product),
  );
}

class _AdvisorForm extends StatefulWidget {
  const _AdvisorForm({this.product});

  final Product? product;

  @override
  State<_AdvisorForm> createState() => _AdvisorFormState();
}

class _AdvisorFormState extends State<_AdvisorForm> {
  final _phone = TextEditingController();
  String _slot = 'Dès que possible';
  String? _error;
  bool _loading = false;

  // Libellé affiché → code attendu par le serveur.
  static const _slotCodes = {
    'Dès que possible': 'ASAP',
    'Matin': 'MORNING',
    'Après-midi': 'AFTERNOON',
    'Soir': 'EVENING',
  };
  static final _slots = _slotCodes.keys.toList();

  @override
  void initState() {
    super.initState();
    final phone = context.session.user?.phone ?? context.store.knownPhone;
    if (phone != null) _phone.text = formatPhone(phone);
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
    try {
      final receipt = await context.repos.quotes.requestCallback(
        phone: phoneDigits(_phone.text),
        slot: _slotCodes[_slot]!,
        productId: widget.product?.id,
      );
      if (!mounted) return;
      final messenger = ScaffoldMessenger.of(context);
      Navigator.of(context).pop();
      // Un délai n'est annoncé que s'il vient du serveur (sinon, aucun engagement).
      final minutes = receipt.expectedWithinMinutes;
      final when = _slot == 'Dès que possible'
          ? (minutes != null ? 'sous $minutes minutes' : 'dès que possible')
          : _slot.toLowerCase();
      final ref = receipt.reference.isEmpty ? '' : ' (réf. ${receipt.reference})';
      messenger.showSnackBar(SnackBar(
        behavior: SnackBarBehavior.floating,
        backgroundColor: AzColors.ink,
        content: Text(
          'Demande envoyée$ref. Un conseiller vous rappelle $when.',
          style: AzText.bodyMedium.copyWith(color: Colors.white),
        ),
      ));
    } catch (e) {
      if (mounted) setState(() => _error = errorMessage(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = widget.product;
    return AzGap(
      children: [
        Text(
          p != null && !p.onlineSubscription
              ? '${p.title} se souscrit avec l’aide d’un conseiller AssurZen. Laissez-nous votre numéro, nous vous rappelons.'
              : 'Un conseiller AssurZen vous rappelle pour finaliser votre devis avec vous.',
          style: AzText.bodyMedium.copyWith(color: AzColors.muted),
        ),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Quand préférez-vous être rappelé ?', style: AzText.label),
            const SizedBox(height: 8),
            AzChipFill<String>(options: _slots, selected: _slot, minChipWidth: 130, onSelected: (v) => setState(() => _slot = v)),
          ],
        ),
        AzPhoneField(controller: _phone, label: 'Numéro à rappeler', error: _error),
        AzButton('Demander un rappel', loading: _loading, onPressed: _send),
        ValueListenableBuilder<ContactInfo>(
          valueListenable: ContactDirectory.info,
          builder: (context, info, _) {
            final phone = info.primaryPhone;
            if (phone == null) return const SizedBox.shrink();
            return Center(
              child: AzTextLink('Ou appelez-nous maintenant : ${formatPhone(phone)}', onPressed: () => Launch.phone(context)),
            );
          },
        ),
      ],
    );
  }
}
