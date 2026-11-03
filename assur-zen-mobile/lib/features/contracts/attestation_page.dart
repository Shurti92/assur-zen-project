import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../core/ui.dart';
import '../../data/models/models.dart';

/// Présentation de l’attestation lors d’un contrôle : plein écran, QR code
/// lisible, consultable sans connexion (décisions 5.2 et 5.4).
class AttestationPage extends StatelessWidget {
  const AttestationPage({super.key, required this.contract});

  final Contract contract;

  @override
  Widget build(BuildContext context) {
    final c = contract;
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: AzTheme.brandOverlay,
      child: Scaffold(
        backgroundColor: AzColors.blue700,
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(AzSpace.screen),
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text('ATTESTATION D’ASSURANCE',
                          style: AzText.overline.copyWith(color: AzColors.blue100, letterSpacing: 1.2)),
                    ),
                    IconButton(
                      tooltip: 'Fermer',
                      onPressed: () => Navigator.of(context).pop(),
                      icon: const Icon(Icons.close_rounded, color: Colors.white),
                    ),
                  ],
                ),
                Expanded(
                  child: Center(
                    child: SingleChildScrollView(
                      child: Column(
                        children: [
                          Container(
                            padding: const EdgeInsets.all(16),
                            decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(20)),
                            child: QrImageView(
                              data: c.verificationCode ?? c.number,
                              size: 230,
                              padding: EdgeInsets.zero,
                              semanticsLabel: 'QR code de vérification',
                            ),
                          ),
                          const SizedBox(height: 20),
                          Text(c.insuredObject, textAlign: TextAlign.center, style: AzText.title.copyWith(color: Colors.white, fontSize: 22)),
                          const SizedBox(height: 4),
                          Text('Contrat ${c.number}', style: AzText.body.copyWith(color: AzColors.blue100, fontSize: 15)),
                          const SizedBox(height: 18),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
                            decoration: BoxDecoration(color: const Color(0x1FFFFFFF), borderRadius: BorderRadius.circular(16)),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                _Item('Assuré', c.holderName ?? '—'),
                                const SizedBox(width: 24),
                                _Item('Valide jusqu’au', formatDate(c.endDate)),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                Text('Affichable sans connexion · valeur légale à confirmer par AssurZen',
                    textAlign: TextAlign.center, style: AzText.caption.copyWith(color: AzColors.blue100, fontWeight: FontWeight.w500)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Item extends StatelessWidget {
  const _Item(this.label, this.value);

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: AzText.caption.copyWith(color: AzColors.blue100, fontWeight: FontWeight.w500)),
        Text(value, style: AzText.titleSmall.copyWith(color: Colors.white)),
      ],
    );
  }
}
