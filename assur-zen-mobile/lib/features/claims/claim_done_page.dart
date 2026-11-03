import 'package:flutter/material.dart';

import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';
import '../shell/main_shell.dart';

/// Confirmation d’envoi d’une déclaration, avec le numéro de dossier.
class ClaimDonePage extends StatelessWidget {
  const ClaimDonePage({super.key, required this.claim});

  final Claim claim;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              Expanded(
                child: Center(
                  child: SingleChildScrollView(
                    child: Column(
                      children: [
                        Container(
                          width: 96,
                          height: 96,
                          decoration: const BoxDecoration(color: AzColors.successBg, shape: BoxShape.circle),
                          child: const Icon(Icons.check_rounded, size: 48, color: AzColors.success),
                        ),
                        const SizedBox(height: 18),
                        const Text('Déclaration envoyée', style: AzText.headline, textAlign: TextAlign.center),
                        const SizedBox(height: 8),
                        Text(
                          // Aucun délai de prise en charge annoncé : le serveur n'en fournit pas.
                          'Votre dossier a bien été reçu. Suivez son avancement dans « Mes sinistres ».',
                          textAlign: TextAlign.center,
                          style: AzText.body.copyWith(color: AzColors.muted),
                        ),
                        if (claim.expectedHandlingHours != null) ...[
                          const SizedBox(height: 8),
                          Text(
                            'Prise en charge sous ${claim.expectedHandlingHours} h.',
                            textAlign: TextAlign.center,
                            style: AzText.bodyMedium.copyWith(color: AzColors.muted, fontWeight: FontWeight.w700),
                          ),
                        ],
                        if (claim.failedUploads > 0) ...[
                          const SizedBox(height: 14),
                          AzBanner(
                            kind: AzBannerKind.warning,
                            text: claim.failedUploads == 1
                                ? 'Une pièce n’a pas pu être envoyée. Votre déclaration est enregistrée ; contactez un conseiller pour la transmettre.'
                                : '${claim.failedUploads} pièces n’ont pas pu être envoyées. Votre déclaration est enregistrée ; contactez un conseiller pour les transmettre.',
                          ),
                        ],
                        const SizedBox(height: 20),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                          decoration: BoxDecoration(color: AzColors.background, borderRadius: BorderRadius.circular(16)),
                          child: Column(
                            children: [
                              Text('Numéro de dossier', style: AzText.caption.copyWith(fontSize: 13)),
                              const SizedBox(height: 4),
                              SelectableText(claim.number, style: AzText.headline.copyWith(fontSize: 22, letterSpacing: 0.5)),
                            ],
                          ),
                        ),
                        const SizedBox(height: 20),
                        const Align(
                          alignment: Alignment.centerLeft,
                          child: Text('Prochaines étapes', style: AzText.titleSmall),
                        ),
                        const SizedBox(height: 8),
                        for (final (i, s) in claim.steps.skip(1).indexed)
                          Padding(
                            padding: const EdgeInsets.only(bottom: 6),
                            child: Row(
                              children: [
                                Text('${i + 1}. ', style: AzText.bodyMedium.copyWith(fontWeight: FontWeight.w700)),
                                Expanded(child: Text(s, style: AzText.bodyMedium)),
                              ],
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
              ),
              AzButton('Suivre mon dossier', onPressed: () => Navigator.of(context).pushReplacementNamed(AppRoutes.claim, arguments: claim.id)),
              const SizedBox(height: 8),
              AzButton.ghost('Retour à l’accueil', onPressed: () => context.resetTo(AppRoutes.shell, ShellTab.home)),
            ],
          ),
        ),
      ),
    );
  }
}
