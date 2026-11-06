import 'package:flutter/material.dart';

import '../theme/az_colors.dart';
import '../theme/az_spacing.dart';
import '../theme/az_typography.dart';

/// Pastille d'action pleine (couleurs unies, jamais de dégradé), tactile sur 44 dp au moins.
class AzPill extends StatelessWidget {
  const AzPill(
    this.label, {
    super.key,
    required this.onTap,
    this.icon,
    this.leadingIcon,
    this.background = Colors.white,
    this.foreground = AzColors.blue700,
    this.borderColor,
    this.expand = false,
  });

  final String label;
  final VoidCallback? onTap;

  /// Icône après le libellé (flèche, par exemple).
  final IconData? icon;

  /// Icône avant le libellé.
  final IconData? leadingIcon;
  final Color background;
  final Color foreground;
  final Color? borderColor;

  /// Occupe toute la largeur disponible (boutons côte à côte).
  final bool expand;

  @override
  Widget build(BuildContext context) {
    final content = Row(
      mainAxisSize: expand ? MainAxisSize.max : MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (leadingIcon != null) ...[Icon(leadingIcon, size: 18, color: foreground), const SizedBox(width: 6)],
        Flexible(
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: AzText.label.copyWith(color: foreground, fontWeight: FontWeight.w700),
          ),
        ),
        if (icon != null) ...[const SizedBox(width: 6), Icon(icon, size: 18, color: foreground)],
      ],
    );
    return Material(
      color: background,
      shape: StadiumBorder(side: borderColor == null ? BorderSide.none : BorderSide(color: borderColor!, width: 1.5)),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 44),
          child: Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: Center(widthFactor: expand ? null : 1, child: content)),
        ),
      ),
    );
  }
}

/// Barre d'avancement en segments égaux (étapes d'un dossier, badges).
class AzSegments extends StatelessWidget {
  const AzSegments({super.key, required this.total, required this.filled, this.height = 6});

  final int total;
  final int filled;
  final double height;

  @override
  Widget build(BuildContext context) {
    final count = total < 1 ? 1 : total;
    return Row(
      children: [
        for (var i = 0; i < count; i++) ...[
          if (i > 0) const SizedBox(width: 4),
          Expanded(
            child: Container(
              height: height,
              decoration: BoxDecoration(
                color: i < filled ? AzColors.blue700 : AzColors.blue100,
                borderRadius: BorderRadius.circular(height / 2),
              ),
            ),
          ),
        ],
      ],
    );
  }
}

/// Titre d'écran des onglets (Assurances, Sinistres, Profil).
class AzScreenTitle extends StatelessWidget {
  const AzScreenTitle(this.title, {super.key, this.subtitle});

  final String title;
  final String? subtitle;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Semantics(header: true, child: Text(title, style: AzText.display.copyWith(fontSize: 28, height: 1.15))),
        if (subtitle != null) ...[
          const SizedBox(height: 4),
          Text(subtitle!, style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
        ],
      ],
    );
  }
}

/// Marge latérale des écrans à blocs.
const EdgeInsets azScreenPadding = EdgeInsets.symmetric(horizontal: AzSpace.screen);
