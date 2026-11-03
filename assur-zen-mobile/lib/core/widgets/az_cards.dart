import 'package:flutter/material.dart';

import '../theme/az_colors.dart';
import '../theme/az_spacing.dart';
import '../theme/az_typography.dart';

/// Carte blanche à bordure fine, cliquable si [onTap] est fourni.
class AzCard extends StatelessWidget {
  const AzCard({
    super.key,
    required this.child,
    this.onTap,
    this.padding = const EdgeInsets.all(AzSpace.l),
    this.color = Colors.white,
    this.borderColor = AzColors.line,
    this.radius = AzRadius.card,
    this.semanticLabel,
  });

  final Widget child;
  final VoidCallback? onTap;
  final EdgeInsetsGeometry padding;
  final Color color;
  final Color? borderColor;
  final double radius;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final card = Material(
      color: color,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(radius),
        side: borderColor == null ? BorderSide.none : BorderSide(color: borderColor!),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(padding: padding, child: child),
      ),
    );
    if (onTap == null) return card;
    return Semantics(button: true, label: semanticLabel, child: card);
  }
}

/// Grande carte bleue de marque (héros d’accueil, synthèse de contrat).
class AzHeroCard extends StatelessWidget {
  const AzHeroCard({super.key, required this.child, this.color = AzColors.blue700, this.showDot = true});

  final Widget child;
  final Color color;
  final bool showDot;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(AzRadius.hero),
      child: Container(
        color: color,
        child: Stack(
          children: [
            Positioned(
              right: -70,
              top: -80,
              child: Container(
                width: 200,
                height: 200,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(color: const Color(0x40D6E0F3), width: 1.5),
                ),
              ),
            ),
            if (showDot)
              Positioned(
                right: 28,
                top: 26,
                child: Container(
                  width: 12,
                  height: 12,
                  decoration: const BoxDecoration(color: AzColors.orange500, shape: BoxShape.circle),
                ),
              ),
            Padding(padding: const EdgeInsets.all(20), child: child),
          ],
        ),
      ),
    );
  }
}

enum AzBannerKind { info, success, warning, error }

/// Bandeau d’information contextuel.
class AzBanner extends StatelessWidget {
  const AzBanner({super.key, required this.kind, required this.text, this.icon, this.actionLabel, this.onTap, this.title});

  final AzBannerKind kind;
  final String text;
  final String? title;
  final IconData? icon;
  final String? actionLabel;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final (Color fg, Color bg, Color ink, IconData defaultIcon) = switch (kind) {
      AzBannerKind.info => (AzColors.info, AzColors.infoBg, AzColors.info, Icons.info_outline),
      AzBannerKind.success => (AzColors.success, AzColors.successBg, AzColors.success, Icons.check_circle_outline),
      AzBannerKind.warning => (AzColors.warning, AzColors.warningBg, AzColors.warningInk, Icons.schedule_outlined),
      AzBannerKind.error => (AzColors.error, AzColors.errorBg, AzColors.errorInk, Icons.error_outline),
    };
    return AzCard(
      color: bg,
      borderColor: null,
      radius: 14,
      onTap: onTap,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon ?? defaultIcon, size: 20, color: fg),
          const SizedBox(width: 10),
          Expanded(
            child: Text.rich(
              TextSpan(children: [
                if (title != null) TextSpan(text: '$title ', style: const TextStyle(fontWeight: FontWeight.w800)),
                TextSpan(text: text),
              ]),
              style: AzText.bodyMedium.copyWith(color: ink),
            ),
          ),
          if (actionLabel != null) ...[
            const SizedBox(width: 8),
            Text(actionLabel!, style: AzText.bodyMedium.copyWith(color: fg, fontWeight: FontWeight.w800)),
          ],
        ],
      ),
    );
  }
}

/// Ligne « libellé — valeur » d’un récapitulatif.
class AzInfoRow extends StatelessWidget {
  const AzInfoRow(this.label, this.value, {super.key, this.last = false, this.valueColor});

  final String label;
  final String value;
  final bool last;
  final Color? valueColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 11),
      decoration: BoxDecoration(
        border: last ? null : const Border(bottom: BorderSide(color: AzColors.lineSoft)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              value,
              textAlign: TextAlign.right,
              style: AzText.bodyMedium.copyWith(color: valueColor ?? AzColors.ink, fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ),
    );
  }
}

/// Carte de récapitulatif contenant des [AzInfoRow].
class AzSummaryCard extends StatelessWidget {
  const AzSummaryCard({super.key, required this.rows});

  final List<(String, String)> rows;

  @override
  Widget build(BuildContext context) {
    return AzCard(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: Column(
        children: [
          for (var i = 0; i < rows.length; i++) AzInfoRow(rows[i].$1, rows[i].$2, last: i == rows.length - 1),
        ],
      ),
    );
  }
}

/// Pastille carrée d’icône sur fond teinté.
class AzIconTile extends StatelessWidget {
  const AzIconTile({
    super.key,
    required this.icon,
    this.size = 44,
    this.background = AzColors.blue50,
    this.color = AzColors.blue700,
    this.iconSize,
    this.radius,
  });

  final IconData icon;
  final double size;
  final Color background;
  final Color color;
  final double? iconSize;
  final double? radius;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(radius ?? size * 0.3)),
      alignment: Alignment.center,
      child: Icon(icon, size: iconSize ?? size * 0.5, color: color),
    );
  }
}
