import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';

/// Onglet Sinistres : urgence 24h/24, déclarer, liste des dossiers.
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
          Semantics(header: true, child: const Text('Sinistres', style: AzText.headline)),
          const SizedBox(height: 14),
          const UrgencyButton(),
          const SizedBox(height: 12),
          AzButton('Déclarer un sinistre', icon: Icons.add_rounded, onPressed: _declare),
          const SizedBox(height: 24),
          const AzSectionHeader('Mes déclarations'),
          const SizedBox(height: 8),
          AsyncView<List<Claim>>(
            key: _listKey,
            load: () => context.repos.claims.getClaims(),
            loading: const AzSkeletonList(count: 2, padding: EdgeInsets.zero),
            isEmpty: (l) => l.isEmpty,
            empty: Padding(
              padding: const EdgeInsets.symmetric(vertical: 32),
              child: Text('Aucun sinistre déclaré. Nous espérons que cela dure !',
                  textAlign: TextAlign.center, style: AzText.body.copyWith(color: AzColors.muted)),
            ),
            builder: (context, claims, _) => Column(
              children: [for (final k in claims) ...[ClaimCard(claim: k), const SizedBox(height: 10)]],
            ),
          ),
        ],
      ),
    );
  }
}

/// Bouton d’urgence (décision 6.5).
class UrgencyButton extends StatelessWidget {
  const UrgencyButton({super.key});

  @override
  Widget build(BuildContext context) {
    // Numéros, disponibilité et urgences viennent du serveur : sans numéro, pas de bouton.
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
          color: AzColors.errorStrong,
          borderColor: null,
          semanticLabel: 'Urgence, appeler l’assistance AssurZen',
          onTap: () => Launch.phone(context),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          child: Row(
            children: [
              const AzIconTile(icon: Icons.call_rounded, background: Color(0x2EFFFFFF), color: Colors.white),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Urgence · Assistance', style: AzText.body.copyWith(color: Colors.white, fontWeight: FontWeight.w800)),
                    if (lines.isNotEmpty)
                      Text(
                        lines.join(' '),
                        style: AzText.caption.copyWith(color: const Color(0xFFFDECEA), fontWeight: FontWeight.w500),
                      ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right_rounded, color: Colors.white),
            ],
          ),
        );
      },
    );
  }
}

class ClaimCard extends StatelessWidget {
  const ClaimCard({super.key, required this.claim});

  final Claim claim;

  @override
  Widget build(BuildContext context) {
    return AzCard(
      onTap: () => context.push(AppRoutes.claim, claim.id),
      semanticLabel: '${claim.type}, ${claim.statusLabel}',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text(claim.type, style: AzText.titleSmall)),
              AzStatusBadge.claim(claim),
            ],
          ),
          const SizedBox(height: 4),
          Text(claim.contractLabel, style: AzText.caption.copyWith(fontSize: 13, fontWeight: FontWeight.w500)),
          const Divider(height: 22, color: AzColors.lineSoft),
          Row(
            children: [
              Text('Dossier ${claim.number}', style: AzText.caption),
              const Spacer(),
              Text('Déclaré le ${formatDate(claim.declaredAt)}', style: AzText.caption),
            ],
          ),
        ],
      ),
    );
  }
}
