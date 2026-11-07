import 'package:flutter/material.dart';

import '../theme/az_colors.dart';
import '../theme/az_spacing.dart';
import '../theme/az_typography.dart';

enum AzButtonVariant { primary, secondary, ghost, danger, dangerOutline, accent, tonal }

/// Bouton AssurZen. Hauteur 52 dp, zone tactile ≥ 48 dp.
class AzButton extends StatelessWidget {
  const AzButton(
    this.label, {
    super.key,
    this.onPressed,
    this.icon,
    this.variant = AzButtonVariant.primary,
    this.loading = false,
    this.expand = true,
    this.height = 52,
    this.radius = AzRadius.button,
    this.horizontalPadding = 18,
  });

  const AzButton.secondary(this.label, {super.key, this.onPressed, this.icon, this.loading = false, this.expand = true, this.height = 52, this.radius = AzRadius.button, this.horizontalPadding = 18})
      : variant = AzButtonVariant.secondary;

  const AzButton.ghost(this.label, {super.key, this.onPressed, this.icon, this.loading = false, this.expand = true, this.height = 48, this.radius = AzRadius.button, this.horizontalPadding = 18})
      : variant = AzButtonVariant.ghost;

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final AzButtonVariant variant;
  final bool loading;
  final bool expand;
  final double height;
  final double radius;

  /// Marge intérieure gauche et droite (réduite quand deux boutons se partagent la largeur).
  final double horizontalPadding;

  Widget _label(Color fg) => Text(
        label,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: AzText.button.copyWith(color: fg, fontSize: height < 48 ? 14 : 16),
      );

  @override
  Widget build(BuildContext context) {
    final enabled = onPressed != null && !loading;
    final (Color bg, Color fg, Color? border) = switch (variant) {
      AzButtonVariant.primary => (enabled || loading ? AzColors.blue700 : AzColors.disabled, Colors.white, null),
      AzButtonVariant.accent => (enabled || loading ? AzColors.orange500 : AzColors.disabled, Colors.white, null),
      AzButtonVariant.secondary => (Colors.white, enabled ? AzColors.blue700 : AzColors.disabled, enabled ? AzColors.blue700 : AzColors.fieldBorder),
      AzButtonVariant.tonal => (enabled ? AzColors.blue50 : AzColors.disabled, enabled ? AzColors.blue700 : Colors.white, null),
      AzButtonVariant.ghost => (Colors.transparent, enabled ? AzColors.blue700 : AzColors.disabled, null),
      AzButtonVariant.danger => (enabled ? AzColors.errorStrong : AzColors.disabled, Colors.white, null),
      AzButtonVariant.dangerOutline => (Colors.white, AzColors.errorStrong, AzColors.errorStrong),
    };

    final content = LayoutBuilder(
      builder: (context, box) => Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (loading) ...[
          SizedBox(
            width: 18,
            height: 18,
            child: CircularProgressIndicator(strokeWidth: 2.4, color: fg),
          ),
          const SizedBox(width: 10),
        ] else if (icon != null) ...[
          Icon(icon, size: 20, color: fg),
          const SizedBox(width: 8),
        ],
        // Le libellé peut rétrécir (points de suspension) dès que la largeur est bornée : bouton pleine largeur,
        // ou compact dans un écran étroit. Dans une Row, un bouton compact reçoit une largeur non bornée : pas de Flexible.
        if (expand || box.hasBoundedWidth) Flexible(child: _label(fg)) else _label(fg),
      ],
      ),
    );

    return Semantics(
      button: true,
      enabled: enabled,
      label: label,
      excludeSemantics: true,
      child: SizedBox(
        height: height,
        width: expand ? double.infinity : null,
        child: Material(
          color: bg,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(radius),
            side: border == null ? BorderSide.none : BorderSide(color: border, width: 1.5),
          ),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: enabled ? onPressed : null,
            child: Padding(
              padding: EdgeInsets.symmetric(horizontal: horizontalPadding),
              child: Center(widthFactor: 1, child: content),
            ),
          ),
        ),
      ),
    );
  }
}

/// Bouton icône carré (44 dp) avec badge facultatif.
class AzIconButton extends StatelessWidget {
  const AzIconButton({
    super.key,
    required this.icon,
    required this.tooltip,
    required this.onPressed,
    this.badge,
    this.filled = true,
    this.color = AzColors.ink,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback? onPressed;
  final int? badge;
  final bool filled;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: Semantics(
        button: true,
        label: badge != null && badge! > 0 ? '$tooltip, $badge non lues' : tooltip,
        excludeSemantics: true,
        child: Material(
          color: filled ? Colors.white : Colors.transparent,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
            side: filled ? const BorderSide(color: AzColors.line) : BorderSide.none,
          ),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onPressed,
            child: SizedBox(
              width: 44,
              height: 44,
              child: Stack(
                clipBehavior: Clip.none,
                alignment: Alignment.center,
                children: [
                  Icon(icon, size: 22, color: color),
                  if (badge != null && badge! > 0)
                    Positioned(
                      top: 7,
                      right: 7,
                      child: Container(
                        constraints: const BoxConstraints(minWidth: 16, minHeight: 16),
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        decoration: BoxDecoration(color: AzColors.errorStrong, borderRadius: BorderRadius.circular(8)),
                        alignment: Alignment.center,
                        child: Text(
                          '${badge!}',
                          style: const TextStyle(fontFamily: AzText.family, color: Colors.white, fontSize: 10, fontWeight: FontWeight.w800),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Lien texte (« Tout voir », « Modifier »…), zone tactile 44 dp.
class AzTextLink extends StatelessWidget {
  const AzTextLink(this.label, {super.key, required this.onPressed, this.color = AzColors.blue700, this.fontSize = 14});

  final String label;
  final VoidCallback? onPressed;
  final Color color;
  final double fontSize;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      child: InkWell(
        onTap: onPressed,
        borderRadius: BorderRadius.circular(8),
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 44),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Align(
              widthFactor: 1,
              child: Text(label, style: AzText.label.copyWith(color: color, fontWeight: FontWeight.w700, fontSize: fontSize)),
            ),
          ),
        ),
      ),
    );
  }
}
