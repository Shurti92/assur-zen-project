import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/ui.dart';

/// Cadre commun des écrans d'accès (connexion, code PIN oublié, créer un compte, code SMS) :
/// un bandeau bleu plein avec le titre et le message, puis une feuille blanche aux coins arrondis
/// qui porte le formulaire. Couleurs pleines, aucun dégradé.
class AuthLayout extends StatelessWidget {
  const AuthLayout({
    super.key,
    required this.title,
    required this.subtitle,
    required this.children,
    this.showMark = false,
    this.showBack = false,
    this.onBack,
    this.step,
    this.steps,
    this.titleSize = 30,
    this.sheetColor = Colors.white,
  });

  final String title;
  final String subtitle;

  /// Contenu de la feuille blanche, empilé avec 14 dp d'écart.
  final List<Widget> children;

  /// Logo AssurZen en haut du bandeau (écran de connexion).
  final bool showMark;
  final bool showBack;

  /// Action du bouton retour ; par défaut, revient à l'écran précédent.
  final VoidCallback? onBack;

  /// « Étape [step] sur [steps] » : trait de progression sous le message.
  final int? step;
  final int? steps;
  final double titleSize;

  /// Couleur de la feuille qui porte le contenu (blanc pour les formulaires, fond de l'appli pour les pages à cartes).
  final Color sheetColor;

  static const double _overlap = 32;

  @override
  Widget build(BuildContext context) {
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light.copyWith(
        statusBarColor: Colors.transparent,
        systemNavigationBarColor: Colors.white,
        systemNavigationBarIconBrightness: Brightness.dark,
      ),
      child: Scaffold(
        backgroundColor: sheetColor,
        body: SingleChildScrollView(
          keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _Header(
                title: title,
                subtitle: subtitle,
                showMark: showMark,
                showBack: showBack,
                onBack: onBack,
                step: step,
                steps: steps,
                titleSize: titleSize,
                bottomGap: _overlap,
              ),
              // La feuille remonte sur le bandeau : ses coins arrondis laissent voir le bleu.
              Transform.translate(
                offset: const Offset(0, -_overlap),
                child: Container(
                  decoration: BoxDecoration(
                    color: sheetColor,
                    borderRadius: const BorderRadius.vertical(top: Radius.circular(32)),
                  ),
                  padding: EdgeInsets.fromLTRB(22, 24, 22, 24 + MediaQuery.paddingOf(context).bottom + _overlap),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      for (var i = 0; i < children.length; i++) ...[if (i > 0) const SizedBox(height: 14), children[i]],
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({
    required this.title,
    required this.subtitle,
    required this.showMark,
    required this.showBack,
    required this.onBack,
    required this.step,
    required this.steps,
    required this.titleSize,
    required this.bottomGap,
  });

  final String title;
  final String subtitle;
  final bool showMark;
  final bool showBack;
  final VoidCallback? onBack;
  final int? step;
  final int? steps;
  final double titleSize;
  final double bottomGap;

  @override
  Widget build(BuildContext context) {
    final top = MediaQuery.paddingOf(context).top;
    return ClipRect(
      child: Container(
        color: AzColors.blue700,
        child: Stack(
          children: [
            // Décor plein : un cercle fin et un point orange, comme le grand bloc de l'accueil.
            Positioned(
              right: -60,
              top: -40,
              child: Container(
                width: 190,
                height: 190,
                decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: const Color(0x47D6E0F3), width: 1.5)),
              ),
            ),
            const Positioned(
              right: 30,
              top: 46,
              child: SizedBox(width: 12, height: 12, child: DecoratedBox(decoration: BoxDecoration(color: AzColors.orange500, shape: BoxShape.circle))),
            ),
            Padding(
              padding: EdgeInsets.fromLTRB(22, top + 10, 22, bottomGap + 4),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      if (showBack) _BackButton(onBack: onBack),
                      if (showBack && showMark) const SizedBox(width: 12),
                      if (showMark) ...[
                        const AzLogoMark(size: 34),
                        const SizedBox(width: 10),
                        Text('AssurZen', style: AzText.section.copyWith(color: Colors.white, fontSize: 17)),
                      ],
                    ],
                  ),
                  SizedBox(height: showMark ? 22 : 14),
                  Semantics(
                    header: true,
                    child: Text(title, style: AzText.display.copyWith(color: Colors.white, fontSize: titleSize, height: 1.12, letterSpacing: -0.4)),
                  ),
                  const SizedBox(height: 6),
                  Text(subtitle, style: AzText.body.copyWith(color: AzColors.blue100, fontSize: 14.5)),
                  if (step != null && steps != null) ...[
                    const SizedBox(height: 14),
                    Semantics(
                      label: 'Étape $step sur $steps',
                      child: Row(
                        children: [
                          for (var i = 0; i < steps!; i++) ...[
                            if (i > 0) const SizedBox(width: 4),
                            Expanded(
                              child: Container(
                                height: 5,
                                decoration: BoxDecoration(
                                  color: i < step! ? Colors.white : const Color(0x40FFFFFF),
                                  borderRadius: BorderRadius.circular(3),
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _BackButton extends StatelessWidget {
  const _BackButton({required this.onBack});

  final VoidCallback? onBack;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: 'Retour',
      child: Semantics(
        button: true,
        label: 'Retour',
        excludeSemantics: true,
        child: Material(
          color: const Color(0x24FFFFFF),
          shape: const CircleBorder(),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onBack ?? () => Navigator.of(context).maybePop(),
            child: const SizedBox(width: 44, height: 44, child: Icon(Icons.arrow_back_rounded, color: Colors.white, size: 22)),
          ),
        ),
      ),
    );
  }
}

/// Bouton principal des écrans d'accès : orange, 54 dp, coins de 16 dp.
class AuthPrimaryButton extends StatelessWidget {
  const AuthPrimaryButton(this.label, {super.key, required this.onPressed, this.loading = false});

  final String label;
  final VoidCallback? onPressed;
  final bool loading;

  @override
  Widget build(BuildContext context) => AzButton(label, variant: AzButtonVariant.accent, height: 54, radius: 16, loading: loading, onPressed: onPressed);
}

/// Bouton secondaire : fond bleu très clair, texte bleu.
class AuthSecondaryButton extends StatelessWidget {
  const AuthSecondaryButton(this.label, {super.key, required this.onPressed, this.icon});

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;

  @override
  Widget build(BuildContext context) => AzButton(label, variant: AzButtonVariant.tonal, height: 54, radius: 16, icon: icon, onPressed: onPressed);
}
