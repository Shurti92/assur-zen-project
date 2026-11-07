import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../shell/main_shell.dart';

/// Choix : connexion, création de compte ou visite sans compte (décision 4.6).
class WelcomePage extends StatelessWidget {
  const WelcomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: AzTheme.brandOverlay,
      child: Scaffold(
        backgroundColor: AzColors.blue700,
        body: Column(
          children: [
            Expanded(
              child: Stack(
                fit: StackFit.expand,
                children: [
                  Positioned(
                    right: -140,
                    top: -90,
                    child: Container(
                      width: 340,
                      height: 340,
                      decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: const Color(0x40D6E0F3), width: 1.5)),
                    ),
                  ),
                  SafeArea(
                    bottom: false,
                    child: Center(
                      child: SingleChildScrollView(
                        padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 16),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                              decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(18)),
                              child: const AzLogo(height: 46),
                            ),
                            const SizedBox(height: 24),
                            Text('Bienvenue sur AssurZen Mobile',
                                style: AzText.display.copyWith(color: Colors.white, fontSize: 29, height: 35 / 29)),
                            const SizedBox(height: 12),
                            Text('Devis, contrats, attestations et sinistres : tout au même endroit.',
                                style: AzText.body.copyWith(color: AzColors.blue100)),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            Container(
              decoration: const BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
              ),
              child: SafeArea(
                top: false,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(24, 28, 24, 20),
                  child: AzGap(
                    gap: 12,
                    children: [
                      AzButton('Se connecter', onPressed: () => context.push(AppRoutes.login)),
                      AzButton.secondary('Créer un compte', onPressed: () => context.push(AppRoutes.register)),
                      AzButton.ghost('Découvrir les offres sans compte', onPressed: () {
                        context.session.continueAsGuest();
                        context.resetTo(AppRoutes.shell, ShellTab.catalog);
                      }),
                      Text(
                        'En continuant, vous acceptez les Conditions générales d’utilisation et la Politique de confidentialité d’AssurZen.',
                        textAlign: TextAlign.center,
                        style: AzText.caption.copyWith(fontWeight: FontWeight.w500, height: 18 / 12),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
