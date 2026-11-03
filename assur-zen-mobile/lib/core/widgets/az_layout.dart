import 'package:flutter/material.dart';

import '../theme/az_colors.dart';
import '../theme/az_spacing.dart';
import '../theme/az_typography.dart';

/// Barre de titre des écrans secondaires : retour (44 dp), titre, actions.
class AzTopBar extends StatelessWidget implements PreferredSizeWidget {
  const AzTopBar({super.key, this.title, this.overline, this.onBack, this.actions = const [], this.showBack = true, this.backIcon});

  final String? title;
  final String? overline;
  final VoidCallback? onBack;
  final List<Widget> actions;
  final bool showBack;
  final IconData? backIcon;

  @override
  Size get preferredSize => const Size.fromHeight(64);

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: SizedBox(
        height: 64,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: Row(
            children: [
              if (showBack)
                IconButton(
                  tooltip: 'Retour',
                  onPressed: onBack ?? () => Navigator.of(context).maybePop(),
                  icon: Icon(backIcon ?? Icons.arrow_back_ios_new_rounded, size: 20, color: AzColors.ink),
                  style: IconButton.styleFrom(minimumSize: const Size(44, 44)),
                )
              else
                const SizedBox(width: 12),
              const SizedBox(width: 4),
              Expanded(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (overline != null)
                      Text(overline!, style: AzText.caption.copyWith(fontWeight: FontWeight.w700), maxLines: 1, overflow: TextOverflow.ellipsis),
                    if (title != null)
                      Text(title!, style: AzText.section.copyWith(fontSize: 18), maxLines: 1, overflow: TextOverflow.ellipsis),
                  ],
                ),
              ),
              ...actions,
            ],
          ),
        ),
      ),
    );
  }
}

/// Titre de section avec lien d’action facultatif.
class AzSectionHeader extends StatelessWidget {
  const AzSectionHeader(this.title, {super.key, this.actionLabel, this.onAction, this.trailing});

  final String title;
  final String? actionLabel;
  final VoidCallback? onAction;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(child: Semantics(header: true, child: Text(title, style: AzText.section))),
        if (trailing != null) trailing!,
        if (actionLabel != null)
          InkWell(
            onTap: onAction,
            borderRadius: BorderRadius.circular(8),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 10),
              child: Text(actionLabel!, style: AzText.label.copyWith(color: AzColors.blue700, fontWeight: FontWeight.w700, fontSize: 14)),
            ),
          ),
      ],
    );
  }
}

/// En-tête de progression d’un parcours en étapes.
class AzProgressHeader extends StatelessWidget {
  const AzProgressHeader({super.key, required this.step, required this.total, this.label});

  final int step;
  final int total;
  final String? label;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Étape $step sur $total${label != null ? ', $label' : ''}',
      child: ExcludeSemantics(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Text('Étape $step sur $total', style: AzText.label.copyWith(color: AzColors.ink, fontWeight: FontWeight.w700)),
                const Spacer(),
                if (label != null) Text(label!, style: AzText.label.copyWith(color: AzColors.muted)),
              ],
            ),
            const SizedBox(height: 8),
            ClipRRect(
              borderRadius: BorderRadius.circular(AzRadius.pill),
              child: TweenAnimationBuilder<double>(
                tween: Tween(end: step / total),
                duration: const Duration(milliseconds: 300),
                builder: (context, v, _) => LinearProgressIndicator(
                  value: v,
                  minHeight: 6,
                  backgroundColor: AzColors.line,
                  color: AzColors.blue700,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Barre d’actions fixée en bas d’écran (respecte la barre de navigation Android).
class AzBottomBar extends StatelessWidget {
  const AzBottomBar({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: AzColors.line)),
      ),
      child: SafeArea(
        top: false,
        minimum: const EdgeInsets.only(bottom: 4),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(AzSpace.screen, 12, AzSpace.screen, 12),
          child: Column(mainAxisSize: MainAxisSize.min, children: _spaced(children, 8)),
        ),
      ),
    );
  }
}

List<Widget> _spaced(List<Widget> items, double gap) => [
      for (var i = 0; i < items.length; i++) ...[if (i > 0) SizedBox(height: gap), items[i]],
    ];

/// Colonne avec un espacement régulier entre les enfants.
class AzGap extends StatelessWidget {
  const AzGap({super.key, required this.children, this.gap = AzSpace.l, this.crossAxisAlignment = CrossAxisAlignment.stretch});

  final List<Widget> children;
  final double gap;
  final CrossAxisAlignment crossAxisAlignment;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: crossAxisAlignment,
      mainAxisSize: MainAxisSize.min,
      children: _spaced(children, gap),
    );
  }
}

/// Logo AssurZen complet (symbole + nom + « Assurances Côte d’Ivoire »).
class AzLogo extends StatelessWidget {
  const AzLogo({super.key, this.height = 48});

  final double height;

  @override
  Widget build(BuildContext context) {
    return Image.asset(
      'assets/images/logo.png',
      height: height,
      fit: BoxFit.contain,
      semanticLabel: 'AssurZen — Assurances Côte d’Ivoire',
    );
  }
}

/// Symbole AssurZen seul, dans un carré blanc arrondi.
class AzLogoMark extends StatelessWidget {
  const AzLogoMark({super.key, this.size = 44});

  final double size;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      padding: EdgeInsets.all(size * 0.12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(size * 0.32),
        border: Border.all(color: AzColors.line),
      ),
      child: Image.asset('assets/images/logo_mark.png', fit: BoxFit.contain, semanticLabel: 'AssurZen'),
    );
  }
}

/// Mention discrète « données fictives » sur les écrans de démonstration.
class AzDemoNote extends StatelessWidget {
  const AzDemoNote({super.key, this.text = 'Données de démonstration · fictives'});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Center(child: Text(text, style: AzText.caption.copyWith(fontWeight: FontWeight.w500)));
  }
}
