import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';

/// Onglet Sinistres : déclarer, appeler l'assistance, suivre les dossiers.
class ClaimsPage extends StatefulWidget {
  const ClaimsPage({super.key});

  @override
  State<ClaimsPage> createState() => _ClaimsPageState();
}

class _ClaimsPageState extends State<ClaimsPage> {
  Key _listKey = UniqueKey();

  Future<void> _declare() async {
    await context.push(AppRoutes.claimNew);
    if (mounted) setState(() => _listKey = UniqueKey());
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(AzSpace.screen, 20, AzSpace.screen, 24),
        children: [
          Semantics(header: true, child: Text('Sinistres', style: AzText.display.copyWith(fontSize: 24, height: 1.1))),
          const SizedBox(height: 4),
          Text('Un accident, un vol ? Nous vous accompagnons.', style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
          const SizedBox(height: 14),
          // Déclarer à gauche, urgence à droite (le bloc d'urgence disparaît sans numéro fourni par le serveur).
          ValueListenableBuilder<ContactInfo>(
            valueListenable: ContactDirectory.info,
            builder: (context, info, _) {
              final urgency = info.primaryPhone != null;
              return _MinHeight(
                height: 150,
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Expanded(flex: 5, child: _DeclareTile(onTap: _declare)),
                    if (urgency) ...[
                      const SizedBox(width: 10),
                      const Expanded(flex: 4, child: UrgencyButton()),
                    ],
                  ],
                ),
              );
            },
          ),
          AsyncView<List<Claim>>(
            key: _listKey,
            load: () => context.repos.claims.getClaims(),
            loading: const Padding(
              padding: EdgeInsets.only(top: 22),
              child: AzSkeletonList(count: 2, padding: EdgeInsets.zero),
            ),
            isEmpty: (l) => l.isEmpty,
            empty: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(height: 22),
                const _ListHeader(),
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 32),
                  child: Text('Aucun sinistre déclaré. Nous espérons que cela dure !',
                      textAlign: TextAlign.center, style: AzText.body.copyWith(color: AzColors.muted)),
                ),
              ],
            ),
            builder: (context, claims, _) => Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(height: 22),
                _ListHeader(count: claims.length),
                for (final k in claims) ...[const SizedBox(height: 10), ClaimCard(claim: k)],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// « Mes déclarations » et le nombre de dossiers.
class _ListHeader extends StatelessWidget {
  const _ListHeader({this.count});

  final int? count;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.baseline,
        textBaseline: TextBaseline.alphabetic,
        children: [
          Expanded(child: Semantics(header: true, child: Text('Mes déclarations', style: AzText.section.copyWith(fontSize: 17)))),
          if (count != null)
            Text('$count dossier${count! > 1 ? 's' : ''}', style: AzText.label.copyWith(color: AzColors.blue700, fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}

/// Hauteur minimale qui grandit avec le contenu (texte agrandi).
class _MinHeight extends StatelessWidget {
  const _MinHeight({required this.height, required this.child});

  final double height;
  final Widget child;

  @override
  Widget build(BuildContext context) => IntrinsicHeight(child: ConstrainedBox(constraints: BoxConstraints(minHeight: height), child: child));
}

/// Grand bloc orange : déclarer un sinistre.
class _DeclareTile extends StatelessWidget {
  const _DeclareTile({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return AzCard(
      color: AzColors.orange500,
      borderColor: null,
      radius: 26,
      onTap: onTap,
      semanticLabel: 'Déclarer un sinistre',
      padding: const EdgeInsets.all(16),
      child: SizedBox(
        width: double.infinity,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const AzIconTile(icon: Icons.report_outlined, background: Color(0x33FFFFFF), color: Colors.white, size: 44),
            const Spacer(),
            Text('Déclarer un sinistre', style: AzText.section.copyWith(color: Colors.white, fontSize: 19, height: 1.2)),
            const SizedBox(height: 6),
            Text('En 4 étapes, avec photos et documents', style: AzText.caption.copyWith(color: const Color(0xFFFFE3D0), fontWeight: FontWeight.w500, fontSize: 12.5)),
          ],
        ),
      ),
    );
  }
}

/// Bloc d’urgence (décision 6.5) : appelle l'assistance.
class UrgencyButton extends StatelessWidget {
  const UrgencyButton({super.key});

  @override
  Widget build(BuildContext context) {
    // Numéros, disponibilité et urgences viennent du serveur : sans numéro, pas de bloc.
    return ValueListenableBuilder<ContactInfo>(
      valueListenable: ContactDirectory.info,
      builder: (context, info, _) {
        if (info.primaryPhone == null) return const SizedBox.shrink();
        final availability = info.availabilityText;
        final lines = [
          if (availability != null) availability,
          if (info.emergencyNumbers.isNotEmpty)
            'Urgences : ${info.emergencyNumbers.map((e) => e.label.isEmpty ? e.number : '${e.label} ${e.number}').join(', ')}.',
        ];
        return AzCard(
          color: AzColors.ink,
          borderColor: null,
          radius: 26,
          semanticLabel: 'Urgence, appeler l’assistance AssurZen',
          onTap: () => Launch.phone(context),
          padding: const EdgeInsets.all(16),
          child: SizedBox(
            width: double.infinity,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const AzIconTile(icon: Icons.call_rounded, background: Color(0x24FFFFFF), color: Color(0xFFFFB27A), size: 44),
                const Spacer(),
                Text('Urgence · Assistance', style: AzText.titleSmall.copyWith(color: Colors.white, fontSize: 15, height: 1.2)),
                if (lines.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(
                    lines.join(' '),
                    maxLines: 4,
                    overflow: TextOverflow.ellipsis,
                    style: AzText.caption.copyWith(color: const Color(0xFFB9C3DA), fontWeight: FontWeight.w500, fontSize: 12.5),
                  ),
                ],
              ],
            ),
          ),
        );
      },
    );
  }
}

/// Dossier de sinistre : type, numéro et date, avancement en étapes (fournies par le serveur).
class ClaimCard extends StatelessWidget {
  const ClaimCard({super.key, required this.claim});

  final Claim claim;

  @override
  Widget build(BuildContext context) {
    final k = claim;
    final total = k.steps.length;
    final filled = k.closed ? total : (k.currentStep + 1).clamp(0, total);
    return AzCard(
      radius: 26,
      onTap: () => context.push(AppRoutes.claim, k.id),
      semanticLabel: '${k.type}, ${k.statusLabel}',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              AzIconTile(
                icon: Icons.report_outlined,
                background: k.closed ? AzColors.blue50 : AzColors.orange50,
                color: k.closed ? AzColors.blue700 : AzColors.orange700,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(k.type, style: AzText.titleSmall.copyWith(fontSize: 15.5)),
                    Text('Dossier ${k.number} · déclaré le ${formatDate(k.declaredAt)}', style: AzText.caption.copyWith(fontSize: 12.5, fontWeight: FontWeight.w500)),
                  ],
                ),
              ),
              if (k.closed) ...[
                const SizedBox(width: 8),
                const AzStatusBadge(label: 'Clôturé', foreground: AzColors.blue700, background: AzColors.blue50, dot: false),
              ],
            ],
          ),
          if (total > 0) ...[
            const SizedBox(height: 12),
            AzSegments(total: total, filled: filled),
            const SizedBox(height: 6),
            Row(
              children: [
                Text(k.closed ? 'Terminé' : 'Étape $filled sur $total', style: AzText.caption.copyWith(color: AzColors.ink, fontWeight: FontWeight.w700, fontSize: 12.5)),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    k.closed ? '$total étapes sur $total' : k.statusLabel,
                    textAlign: TextAlign.right,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: AzText.caption.copyWith(fontSize: 12.5, fontWeight: FontWeight.w500),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
