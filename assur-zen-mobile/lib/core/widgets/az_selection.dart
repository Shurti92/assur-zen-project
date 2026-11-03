import 'package:flutter/material.dart';

import '../../data/models/models.dart';
import '../theme/az_colors.dart';
import '../theme/az_spacing.dart';
import '../theme/az_typography.dart';

/// Badge de statut sobre (actif, en attente, expiré, résilié…).
class AzStatusBadge extends StatelessWidget {
  const AzStatusBadge({super.key, required this.label, required this.foreground, required this.background, this.dot = true});

  factory AzStatusBadge.contract(ContractStatus status) {
    final (Color fg, Color bg) = switch (status) {
      ContractStatus.actif => (AzColors.success, AzColors.successBg),
      ContractStatus.enAttente => (AzColors.warning, AzColors.warningBg),
      ContractStatus.suspendu => (AzColors.warning, AzColors.warningBg),
      ContractStatus.expire => (AzColors.neutral, AzColors.neutralBg),
      ContractStatus.resilie => (AzColors.error, AzColors.errorBg),
    };
    return AzStatusBadge(label: status.label, foreground: fg, background: bg);
  }

  factory AzStatusBadge.document(DocumentStatus status) {
    final (Color fg, Color bg) = switch (status) {
      DocumentStatus.disponible => (AzColors.success, AzColors.successBg),
      DocumentStatus.enGeneration => (AzColors.warning, AzColors.warningBg),
      DocumentStatus.nonDisponible => (AzColors.neutral, AzColors.neutralBg),
    };
    return AzStatusBadge(label: status.label, foreground: fg, background: bg, dot: false);
  }

  factory AzStatusBadge.claim(Claim claim) {
    if (claim.closed) {
      return const AzStatusBadge(label: 'Clôturé', foreground: AzColors.neutral, background: AzColors.neutralBg);
    }
    if (claim.currentStep == 0) {
      return const AzStatusBadge(label: 'Reçue', foreground: AzColors.blue700, background: AzColors.blue50);
    }
    return AzStatusBadge(label: claim.steps[claim.currentStep], foreground: AzColors.info, background: AzColors.infoBg);
  }

  /// Badge orange « à confirmer », « exemple », « démo ».
  const AzStatusBadge.pending(this.label, {super.key})
      : foreground = AzColors.warning,
        background = AzColors.warningBg,
        dot = false;

  final String label;
  final Color foreground;
  final Color background;
  final bool dot;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 24,
      padding: const EdgeInsets.symmetric(horizontal: 10),
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(AzRadius.pill)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (dot) ...[
            Container(width: 6, height: 6, decoration: BoxDecoration(color: foreground, shape: BoxShape.circle)),
            const SizedBox(width: 6),
          ],
          Text(label, style: AzText.caption.copyWith(color: foreground, fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}

/// Puce sélectionnable (filtre, choix simple).
class AzChoiceChip extends StatelessWidget {
  const AzChoiceChip({super.key, required this.label, required this.selected, required this.onTap, this.squared = false, this.minHeight});

  final String label;
  final bool selected;
  final VoidCallback onTap;
  final bool squared;

  /// Hauteur minimale ; par défaut 40 (46 pour une puce carrée).
  final double? minHeight;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      selected: selected,
      child: Material(
        color: selected ? AzColors.blue700 : Colors.white,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(squared ? AzRadius.field : AzRadius.pill),
          side: BorderSide(color: selected ? AzColors.blue700 : AzColors.fieldBorder, width: 1.5),
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: minHeight ?? (squared ? 46 : 40)),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              child: Center(
                widthFactor: 1,
                heightFactor: 1,
                child: Text(
                  label,
                  textAlign: TextAlign.center,
                  style: AzText.bodyMedium.copyWith(
                    color: selected ? Colors.white : AzColors.ink2,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Rangée de puces qui passe à la ligne.
class AzChipWrap<T> extends StatelessWidget {
  const AzChipWrap({super.key, required this.options, required this.selected, required this.onSelected, this.labelOf});

  final List<T> options;
  final T? selected;
  final ValueChanged<T> onSelected;
  final String Function(T)? labelOf;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        for (final o in options)
          AzChoiceChip(label: labelOf?.call(o) ?? '$o', selected: o == selected, onTap: () => onSelected(o)),
      ],
    );
  }
}

/// Puces qui occupent toute la largeur disponible, quelle que soit la taille de l'écran.
///
/// Le nombre de colonnes s'adapte à la largeur : autant de puces par ligne que possible sans que
/// chacune soit plus étroite que [minChipWidth], puis les lignes sont équilibrées (4 puces sur
/// 3 colonnes possibles donnent 2 lignes de 2). Les puces d'une ligne ont la même largeur et la
/// même hauteur, même si un libellé passe sur deux lignes (texte agrandi).
class AzChipFill<T> extends StatelessWidget {
  const AzChipFill({
    super.key,
    required this.options,
    required this.selected,
    required this.onSelected,
    this.labelOf,
    this.minChipWidth = 96,
    this.maxColumns = 6,
  });

  final List<T> options;
  final T? selected;
  final ValueChanged<T> onSelected;
  final String Function(T)? labelOf;

  /// Largeur en dessous de laquelle une puce n'est pas rétrécie : on passe alors à la ligne.
  final double minChipWidth;
  final int maxColumns;

  static const double _gap = 8;

  @override
  Widget build(BuildContext context) {
    final n = options.length;
    if (n == 0) return const SizedBox.shrink();
    return LayoutBuilder(builder: (context, c) {
      final fit = ((c.maxWidth + _gap) / (minChipWidth + _gap)).floor();
      final perRow = fit.clamp(1, maxColumns).clamp(1, n);
      final rows = (n / perRow).ceil();
      final cols = (n / rows).ceil(); // lignes équilibrées
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var start = 0; start < n; start += cols)
            Padding(
              padding: EdgeInsets.only(top: start == 0 ? 0 : _gap),
              child: IntrinsicHeight(
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    for (var i = start; i < start + cols && i < n; i++) ...[
                      if (i > start) const SizedBox(width: _gap),
                      Expanded(
                        child: AzChoiceChip(
                          label: labelOf?.call(options[i]) ?? '${options[i]}',
                          selected: options[i] == selected,
                          minHeight: 46,
                          onTap: () => onSelected(options[i]),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
        ],
      );
    });
  }
}

/// Grille de puces carrées de même largeur.
class AzChipGrid<T> extends StatelessWidget {
  const AzChipGrid({super.key, required this.options, required this.selected, required this.onSelected, this.columns = 2, this.labelOf});

  final List<T> options;
  final T? selected;
  final ValueChanged<T> onSelected;
  final int columns;
  final String Function(T)? labelOf;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(builder: (context, c) {
      final w = (c.maxWidth - 8 * (columns - 1)) / columns;
      return Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          for (final o in options)
            SizedBox(
              width: w,
              child: AzChoiceChip(
                label: labelOf?.call(o) ?? '$o',
                selected: o == selected,
                squared: true,
                onTap: () => onSelected(o),
              ),
            ),
        ],
      );
    });
  }
}

/// Option sous forme de carte avec bouton radio (formule, moyen de paiement…).
class AzOptionTile extends StatelessWidget {
  const AzOptionTile({
    super.key,
    required this.title,
    required this.selected,
    required this.onTap,
    this.subtitle,
    this.leading,
    this.showRadio = true,
    this.trailing,
  });

  final String title;
  final String? subtitle;
  final bool selected;
  final VoidCallback onTap;
  final Widget? leading;
  final bool showRadio;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      selected: selected,
      child: Material(
        color: selected ? const Color(0xFFF2F5FC) : Colors.white,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AzRadius.button),
          side: BorderSide(color: selected ? AzColors.blue700 : AzColors.fieldBorder, width: selected ? 2 : 1.5),
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Row(
              children: [
                if (leading != null) ...[leading!, const SizedBox(width: 12)],
                if (showRadio && leading == null) ...[_Radio(selected: selected), const SizedBox(width: 12)],
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(title, style: AzText.titleSmall),
                      if (subtitle != null) ...[
                        const SizedBox(height: 2),
                        Text(subtitle!, style: AzText.caption.copyWith(fontWeight: FontWeight.w500, fontSize: 13, height: 18 / 13)),
                      ],
                    ],
                  ),
                ),
                if (trailing != null) trailing!,
                if (showRadio && leading != null) ...[const SizedBox(width: 12), _Radio(selected: selected)],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Radio extends StatelessWidget {
  const _Radio({required this.selected});

  final bool selected;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 22,
      height: 22,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        border: Border.all(color: selected ? AzColors.blue700 : const Color(0xFFC3CBD9), width: 2),
      ),
      alignment: Alignment.center,
      child: selected
          ? Container(width: 10, height: 10, decoration: const BoxDecoration(color: AzColors.blue700, shape: BoxShape.circle))
          : null,
    );
  }
}
