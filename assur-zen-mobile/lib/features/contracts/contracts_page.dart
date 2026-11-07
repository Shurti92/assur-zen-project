import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../core/utils/file_viewer.dart';
import '../../data/models/models.dart';
import '../home/home_page.dart' show RenewalStrip;
import '../shell/main_shell.dart';

/// « Mes assurances » : résumé, recherche, filtres de statut avec compteurs, contrats.
class ContractsPage extends StatefulWidget {
  const ContractsPage({super.key});

  @override
  State<ContractsPage> createState() => _ContractsPageState();
}

class _ContractsPageState extends State<ContractsPage> {
  final _search = TextEditingController();
  ContractStatus? _filter;

  static const _filters = <(ContractStatus?, String)>[
    (null, 'Tous'),
    (ContractStatus.actif, 'Actifs'),
    (ContractStatus.enAttente, 'En attente'),
    (ContractStatus.expire, 'Expirés'),
  ];

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  static bool _expiring(Contract c) => c.status == ContractStatus.actif && c.renewalOpen;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(AzSpace.screen, 20, AzSpace.screen, 4),
            child: Semantics(header: true, child: Text('Mes assurances', style: AzText.display.copyWith(fontSize: 24, height: 1.1))),
          ),
          Expanded(
            child: AsyncView<List<Contract>>(
              load: () => context.repos.contracts.getContracts(),
              isEmpty: (list) => list.isEmpty,
              empty: AzEmptyState(
                icon: Icons.shield_outlined,
                title: 'Aucun contrat pour le moment',
                message: 'Vos contrats AssurZen apparaîtront ici. Commencez par un devis gratuit.',
                actionLabel: 'Obtenir un devis',
                onAction: () => ShellScope.maybeOf(context)?.goTo(ShellTab.catalog),
              ),
              builder: (context, contracts, reload) {
                final q = _search.text.trim().toLowerCase();
                final list = contracts
                    .where((c) => _filter == null || c.status == _filter)
                    .where((c) => q.isEmpty || '${c.number} ${c.insuredObject} ${c.name}'.toLowerCase().contains(q))
                    .toList()
                  // Les contrats à renouveler passent en tête (tri stable pour le reste).
                  ..sort((a, b) => (_expiring(a) ? 0 : 1).compareTo(_expiring(b) ? 0 : 1));
                final toRenew = contracts.where(_expiring).length;
                final strip = list.where(_expiring).firstOrNull;
                return RefreshIndicator(
                  onRefresh: reload,
                  child: ListView(
                    padding: const EdgeInsets.fromLTRB(AzSpace.screen, 0, AzSpace.screen, 24),
                    children: [
                      Text(
                        '${contracts.length} contrat${contracts.length > 1 ? 's' : ''}'
                        '${toRenew > 0 ? ' · $toRenew à renouveler' : ''}',
                        style: AzText.bodyMedium.copyWith(color: AzColors.muted),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _search,
                        onChanged: (_) => setState(() {}),
                        style: AzText.body.copyWith(fontSize: 15),
                        decoration: InputDecoration(
                          hintText: 'N° de contrat, véhicule…',
                          prefixIcon: const Icon(Icons.search_rounded, color: AzColors.muted),
                          filled: true,
                          fillColor: Colors.white,
                          contentPadding: const EdgeInsets.symmetric(vertical: 14),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(18),
                            borderSide: const BorderSide(color: AzColors.line),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(18),
                            borderSide: const BorderSide(color: AzColors.blue700, width: 2),
                          ),
                        ),
                      ),
                      const SizedBox(height: 12),
                      SizedBox(
                        height: 44,
                        child: ListView.separated(
                          scrollDirection: Axis.horizontal,
                          itemCount: _filters.length,
                          separatorBuilder: (_, __) => const SizedBox(width: 8),
                          itemBuilder: (context, i) {
                            final status = _filters[i].$1;
                            // Compteur affiché seulement s'il y a des contrats dans ce statut (ou pour « Tous »).
                            final count = status == null ? contracts.length : contracts.where((c) => c.status == status).length;
                            return _FilterChip(
                              label: _filters[i].$2,
                              count: count > 0 ? count : null,
                              selected: _filter == status,
                              onTap: () => setState(() => _filter = status),
                            );
                          },
                        ),
                      ),
                      if (strip != null) ...[
                        const SizedBox(height: 12),
                        RenewalStrip(contract: strip, onTap: () => context.push(AppRoutes.contract, strip.id)),
                      ],
                      const SizedBox(height: 2),
                      for (final c in list) ...[const SizedBox(height: 10), ContractCard(contract: c)],
                      if (list.isEmpty)
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 40),
                          child: Text('Aucun contrat trouvé. Modifiez votre recherche ou vos filtres.',
                              textAlign: TextAlign.center, style: AzText.body.copyWith(color: AzColors.muted)),
                        ),
                    ],
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

/// Filtre de statut : libellé et compteur dans une pastille.
class _FilterChip extends StatelessWidget {
  const _FilterChip({required this.label, required this.selected, required this.onTap, this.count});

  final String label;
  final int? count;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final fg = selected ? Colors.white : AzColors.ink2;
    return Semantics(
      button: true,
      selected: selected,
      child: Material(
        color: selected ? AzColors.blue700 : Colors.white,
        shape: StadiumBorder(side: BorderSide(color: selected ? AzColors.blue700 : AzColors.line)),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 40),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              child: Center(
                widthFactor: 1,
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(label, style: AzText.label.copyWith(color: fg, fontWeight: FontWeight.w700)),
                    if (count != null) ...[
                      const SizedBox(width: 6),
                      Text('$count', style: AzText.caption.copyWith(color: selected ? AzColors.blue100 : AzColors.muted, fontWeight: FontWeight.w700)),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Carte d’un contrat dans la liste : identité, numéro, échéance, actions rapides fournies par le serveur.
class ContractCard extends StatelessWidget {
  const ContractCard({super.key, required this.contract});

  final Contract contract;

  Future<void> _attestation(BuildContext context) async {
    if (contract.hasDigitalAttestation) {
      await context.push(AppRoutes.attestation, contract);
      return;
    }
    try {
      final file = await context.repos.contracts.downloadAttestation(contract.id);
      if (context.mounted) await FileViewer.show(context, file, fallbackName: 'attestation');
    } catch (e) {
      if (context.mounted) showAzSnack(context, errorMessage(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = contract;
    // Échéance proche : décidée par le serveur (`renewal`), pas recalculée ici.
    final expiring = c.status == ContractStatus.actif && c.renewalOpen;
    final canAttest = c.status == ContractStatus.actif && c.attestation?.available == true;
    final canClaim = c.actions.canDeclareClaim;
    final actions = <Widget>[
      if (expiring)
        AzPill(
          'Renouveler',
          expand: true,
          icon: Icons.arrow_forward_rounded,
          background: AzColors.orange500,
          foreground: Colors.white,
          onTap: () => context.push(AppRoutes.contract, c.id),
        ),
      if (canAttest)
        AzPill('Attestation', expand: true, leadingIcon: Icons.download_rounded, borderColor: AzColors.line, onTap: () => _attestation(context)),
      if (!expiring && canClaim)
        AzPill('Sinistre', expand: true, leadingIcon: Icons.report_outlined, borderColor: AzColors.line, onTap: () => context.push(AppRoutes.claimNew, c.id)),
      if (((expiring ? 1 : 0) + (canAttest ? 1 : 0) + (!expiring && canClaim ? 1 : 0)) < 2)
        AzPill('Détails', expand: true, icon: Icons.chevron_right_rounded, borderColor: AzColors.line, onTap: () => context.push(AppRoutes.contract, c.id)),
    ];
    return AzCard(
      radius: 26,
      borderColor: expiring ? const Color(0xFFF3C7A6) : AzColors.line,
      onTap: () => context.push(AppRoutes.contract, c.id),
      semanticLabel: '${c.name}, ${c.status.label}',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              AzIconTile(
                icon: productIcon(c.productId),
                background: expiring ? AzColors.orange50 : AzColors.blue50,
                color: expiring ? AzColors.orange700 : AzColors.blue700,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(c.name, style: AzText.titleSmall),
                    Text(c.insuredObject, style: AzText.caption.copyWith(fontSize: 13, fontWeight: FontWeight.w500)),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              if (expiring) const AzStatusBadge.pending('Bientôt échu') else AzStatusBadge.contract(c.status),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(child: _Fact('N° de contrat', c.number)),
              const SizedBox(width: 8),
              Expanded(child: _Fact('Échéance', formatDate(c.endDate))),
            ],
          ),
          const SizedBox(height: 12),
          // Deux boutons, comme la maquette : Renouveler (si échéance proche), Attestation, Sinistre, sinon Détails.
          Row(
            children: [
              for (var i = 0; i < actions.length; i++) ...[
                if (i > 0) const SizedBox(width: 8),
                Expanded(child: actions[i]),
              ],
            ],
          ),
        ],
      ),
    );
  }
}

class _Fact extends StatelessWidget {
  const _Fact(this.label, this.value);

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(color: AzColors.background, borderRadius: BorderRadius.circular(14)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: AzText.caption.copyWith(fontWeight: FontWeight.w500, fontSize: 11.5)),
          const SizedBox(height: 2),
          Text(value, maxLines: 1, overflow: TextOverflow.ellipsis, style: AzText.label.copyWith(color: AzColors.ink, fontWeight: FontWeight.w800)),
        ],
      ),
    );
  }
}
