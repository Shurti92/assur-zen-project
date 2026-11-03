import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';
import '../shell/main_shell.dart';

/// « Mes assurances » : recherche, filtres de statut, liste des contrats.
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

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(AzSpace.screen, 20, AzSpace.screen, 12),
            child: Semantics(header: true, child: const Text('Mes assurances', style: AzText.headline)),
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
                    .toList();
                return RefreshIndicator(
                  onRefresh: reload,
                  child: ListView(
                    padding: const EdgeInsets.fromLTRB(AzSpace.screen, 0, AzSpace.screen, 24),
                    children: [
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
                            borderRadius: BorderRadius.circular(14),
                            borderSide: const BorderSide(color: AzColors.line),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(14),
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
                          itemBuilder: (context, i) => AzChoiceChip(
                            label: _filters[i].$2,
                            selected: _filter == _filters[i].$1,
                            onTap: () => setState(() => _filter = _filters[i].$1),
                          ),
                        ),
                      ),
                      const SizedBox(height: 12),
                      for (final c in list) ...[ContractCard(contract: c), const SizedBox(height: 10)],
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

/// Carte d’un contrat dans la liste.
class ContractCard extends StatelessWidget {
  const ContractCard({super.key, required this.contract});

  final Contract contract;

  @override
  Widget build(BuildContext context) {
    // Échéance proche : décidée par le serveur (`renewal`), pas recalculée ici.
    final days = contract.renewal?.daysLeft;
    final expiring = contract.status == ContractStatus.actif && contract.renewalOpen;
    return AzCard(
      onTap: () => context.push(AppRoutes.contract, contract.id),
      semanticLabel: '${contract.name}, ${contract.status.label}',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              AzIconTile(icon: productIcon(contract.productId)),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(contract.name, style: AzText.titleSmall),
                    Text(contract.insuredObject, style: AzText.caption.copyWith(fontSize: 13, fontWeight: FontWeight.w500)),
                  ],
                ),
              ),
              AzStatusBadge.contract(contract.status),
            ],
          ),
          const Divider(height: 24, color: AzColors.lineSoft),
          Row(
            children: [
              Expanded(child: _Field('N° de contrat', contract.number)),
              Expanded(child: _Field('Échéance', formatDate(contract.endDate))),
            ],
          ),
          if (expiring) ...[
            const SizedBox(height: 12),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
              decoration: BoxDecoration(color: AzColors.warningBg, borderRadius: BorderRadius.circular(10)),
              child: Text(days == null ? 'Échéance proche.' : (days == 0 ? 'Échéance aujourd’hui.' : 'Échéance dans $days jours.'),
                  style: AzText.label.copyWith(color: AzColors.warning, fontWeight: FontWeight.w700)),
            ),
          ],
        ],
      ),
    );
  }
}

class _Field extends StatelessWidget {
  const _Field(this.label, this.value);

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: AzText.caption.copyWith(fontWeight: FontWeight.w500)),
        const SizedBox(height: 2),
        Text(value, style: AzText.label.copyWith(color: AzColors.ink, fontWeight: FontWeight.w800)),
      ],
    );
  }
}
