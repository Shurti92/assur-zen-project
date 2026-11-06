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

/// Accordéon en carte arrondie : titre, résumé et pastille, contenu qui se déplie. Encadré en bleu quand il est ouvert.
/// Sans contenu à déplier, la ligne reste une carte simple (pas de flèche).
class AzAccordion extends StatefulWidget {
  const AzAccordion({super.key, required this.title, this.subtitle, this.pill, this.body = const [], this.initiallyOpen = false});

  final String title;
  final String? subtitle;
  final Widget? pill;
  final List<Widget> body;
  final bool initiallyOpen;

  @override
  State<AzAccordion> createState() => _AzAccordionState();
}

class _AzAccordionState extends State<AzAccordion> {
  late bool _open = widget.initiallyOpen && widget.body.isNotEmpty;

  @override
  Widget build(BuildContext context) {
    final head = Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(widget.title, style: AzText.titleSmall.copyWith(fontSize: 15, height: 1.3)),
              if (widget.subtitle != null)
                Text(widget.subtitle!, maxLines: 2, overflow: TextOverflow.ellipsis, style: AzText.caption.copyWith(fontSize: 12.5, fontWeight: FontWeight.w500)),
            ],
          ),
        ),
        if (widget.pill != null) ...[const SizedBox(width: 8), widget.pill!],
      ],
    );
    Widget card(Widget child) => Material(
          color: Colors.white,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(26),
            side: BorderSide(color: _open ? AzColors.blue700 : AzColors.line, width: _open ? 2 : 1),
          ),
          clipBehavior: Clip.antiAlias,
          child: child,
        );
    if (widget.body.isEmpty) {
      return card(Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14), child: head));
    }
    return card(
      Theme(
        data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
        child: ExpansionTile(
          initiallyExpanded: _open,
          onExpansionChanged: (v) => setState(() => _open = v),
          tilePadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 14),
          expandedAlignment: Alignment.centerLeft,
          expandedCrossAxisAlignment: CrossAxisAlignment.start,
          iconColor: AzColors.blue700,
          collapsedIconColor: AzColors.muted,
          title: head,
          children: [
            const Divider(height: 1, color: AzColors.lineSoft),
            const SizedBox(height: 10),
            ...widget.body,
          ],
        ),
      ),
    );
  }
}
