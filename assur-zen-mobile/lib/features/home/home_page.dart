import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/env.dart';
import '../../app/routes.dart';
import '../../core/network/api_exception.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';
import '../catalog/catalog_page.dart' show openProduct;
import '../quote/quote_flow_page.dart';
import '../shell/main_shell.dart';

/// Accueil : protections, raccourcis, solutions, aide.
class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomeData {
  const _HomeData(this.contracts, this.products, this.draft);

  final List<Contract> contracts;
  final List<Product> products;
  final QuoteDraft? draft;
}

class _HomePageState extends State<HomePage> {
  late Future<_HomeData> _future = _load();

  Future<_HomeData> _load() async {
    final repos = context.repos;
    final draft = context.store.readDraft();
    final contracts = await repos.contracts.getContracts();
    // Le catalogue n’est pas encore branché sur le backend : son absence ne doit pas masquer les contrats.
    List<Product> products;
    try {
      products = await repos.catalog.getProducts();
    } catch (_) {
      products = const [];
    }
    return _HomeData(contracts, products, draft);
  }

  Future<void> _refresh() async {
    final next = _load();
    // Corps en bloc : une flèche renverrait le Future assigné, ce que setState refuse.
    setState(() {
      _future = next;
    });
    await next.catchError((_) => const _HomeData([], [], null));
    if (mounted) await context.session.refreshUnread();
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: RefreshIndicator(
        color: AzColors.blue700,
        onRefresh: _refresh,
        child: FutureBuilder<_HomeData>(
          future: _future,
          builder: (context, snap) {
            if (snap.hasError) {
              return ListView(children: [
                const _Header(),
                AzErrorState(message: errorMessage(snap.error!), offline: snap.error is ApiException && (snap.error! as ApiException).isNetwork, onRetry: _refresh),
              ]);
            }
            final data = snap.data;
            final loading = snap.connectionState != ConnectionState.done;
            return ListView(
              padding: const EdgeInsets.only(bottom: 24),
              children: [
                const _Header(),
                const SizedBox(height: 4),
                if (loading || data == null) ...[
                  const Padding(
                    padding: EdgeInsets.symmetric(horizontal: AzSpace.screen),
                    child: AzSkeleton(height: 168, radius: AzRadius.hero),
                  ),
                  const AzSkeletonList(count: 2),
                ] else
                  ..._content(context, data),
              ],
            );
          },
        ),
      ),
    );
  }

  List<Widget> _content(BuildContext context, _HomeData data) {
    final active = data.contracts.where((c) => c.status == ContractStatus.actif).toList();
    // Échéances proches : le serveur décide (`renewal.eligible`), triées par jours restants.
    final expiring = active.where((c) => c.renewalOpen).toList()
      ..sort((a, b) => (a.renewal?.daysLeft ?? 999).compareTo(b.renewal?.daysLeft ?? 999));
    final next = active.where((c) => c.endDate != null).toList()..sort((a, b) => a.endDate!.compareTo(b.endDate!));
    final name = firstName(context.session.user?.fullName ?? '');
    final shell = ShellScope.maybeOf(context);
    const pad = EdgeInsets.symmetric(horizontal: AzSpace.screen);

    return [
      Padding(
        padding: pad,
        child: active.isNotEmpty
            ? _Hero(
                caption: 'Vos protections',
                title: 'Vous êtes protégé(e), $name.',
                text: '${active.length} contrat${active.length > 1 ? 's' : ''} actif${active.length > 1 ? 's' : ''}'
                    '${next.isNotEmpty ? ' · prochaine échéance le ${formatDate(next.first.endDate)}' : ''}',
                cta: 'Voir mes assurances',
                onTap: () => shell?.goTo(ShellTab.contracts),
              )
            : _Hero(
                caption: 'Bienvenue chez AssurZen',
                title: 'Protégez ce qui compte pour vous.',
                text: 'Obtenez un devis auto ou moto en quelques minutes, sans vous déplacer.',
                cta: 'Obtenir un devis',
                onTap: () => shell?.goTo(ShellTab.catalog),
              ),
      ),
      for (final c in expiring.take(1)) ...[
        const SizedBox(height: 12),
        Padding(
          padding: pad,
          child: AzBanner(
            kind: AzBannerKind.warning,
            // Le nom vient du serveur (« Assurance Moto ») ou de la démo (« Moto ») : pas de préfixe en double.
            title: '${c.name.toLowerCase().startsWith('assurance') ? c.name : 'Assurance ${c.name}'} :',
            text: switch (c.renewal?.daysLeft) {
              null => 'échéance proche.',
              0 => 'échéance aujourd’hui.',
              final d => 'échéance dans $d jours.',
            },
            actionLabel: 'Renouveler',
            onTap: () => context.push(AppRoutes.contract, c.id),
          ),
        ),
      ],
      if (data.draft != null) ...[
        const SizedBox(height: 12),
        Padding(padding: pad, child: _DraftCard(draft: data.draft!, products: data.products, onChanged: _refresh)),
      ],
      const SizedBox(height: 24),
      const Padding(padding: pad, child: AzSectionHeader('Que souhaitez-vous faire ?')),
      const SizedBox(height: 10),
      Padding(padding: pad, child: _QuickActions(hasContracts: active.isNotEmpty, firstContract: active.isEmpty ? null : active.first)),
      if (active.isNotEmpty) ...[
        const SizedBox(height: 24),
        Padding(
          padding: pad,
          child: AzSectionHeader('Mes assurances', actionLabel: 'Tout voir', onAction: () => shell?.goTo(ShellTab.contracts)),
        ),
        const SizedBox(height: 6),
        SizedBox(
          height: 180,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: pad,
            itemCount: active.length,
            separatorBuilder: (_, __) => const SizedBox(width: 12),
            itemBuilder: (context, i) => _ContractMini(contract: active[i]),
          ),
        ),
      ],
      const SizedBox(height: 24),
      Padding(
        padding: pad,
        child: AzSectionHeader('Nos solutions d’assurance', actionLabel: 'Catalogue', onAction: () => shell?.goTo(ShellTab.catalog)),
      ),
      const SizedBox(height: 6),
      SizedBox(
        height: 150,
        child: ListView.separated(
          scrollDirection: Axis.horizontal,
          padding: pad,
          itemCount: data.products.length,
          separatorBuilder: (_, __) => const SizedBox(width: 12),
          itemBuilder: (context, i) => _ProductMini(product: data.products[i]),
        ),
      ),
      const SizedBox(height: 24),
      const Padding(padding: pad, child: _HelpCard()),
      const SizedBox(height: 16),
      if (Env.useMock) const AzDemoNote(),
    ];
  }
}

class _Header extends StatelessWidget {
  const _Header();

  @override
  Widget build(BuildContext context) {
    final s = AppScope.of(context).session;
    return Padding(
      padding: const EdgeInsets.fromLTRB(AzSpace.screen, 16, AzSpace.screen, 16),
      child: Row(
        children: [
          const AzLogoMark(size: 44),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(greeting(), style: AzText.label.copyWith(color: AzColors.muted)),
                Text(firstName(s.user?.fullName ?? ''), style: AzText.title.copyWith(fontSize: 19), maxLines: 1, overflow: TextOverflow.ellipsis),
              ],
            ),
          ),
          ListenableBuilder(
            listenable: s,
            builder: (context, _) => AzIconButton(
              icon: Icons.notifications_none_rounded,
              tooltip: 'Notifications',
              badge: s.unreadCount,
              onPressed: () async {
                await context.push(AppRoutes.notifications);
                await s.refreshUnread();
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _Hero extends StatelessWidget {
  const _Hero({required this.caption, required this.title, required this.text, required this.cta, required this.onTap});

  final String caption;
  final String title;
  final String text;
  final String cta;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return AzHeroCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(caption, style: AzText.label.copyWith(color: AzColors.blue100)),
          const SizedBox(height: 6),
          Padding(
            padding: const EdgeInsets.only(right: 24),
            child: Text(title, style: AzText.title.copyWith(color: Colors.white, fontSize: 22)),
          ),
          const SizedBox(height: 6),
          Text(text, style: AzText.bodyMedium.copyWith(color: const Color(0xFFEEF2FA))),
          const SizedBox(height: 14),
          Material(
            color: Colors.white,
            borderRadius: BorderRadius.circular(12),
            child: InkWell(
              onTap: onTap,
              borderRadius: BorderRadius.circular(12),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 11),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(cta, style: AzText.bodyMedium.copyWith(color: AzColors.blue700, fontWeight: FontWeight.w700)),
                    const SizedBox(width: 6),
                    const Icon(Icons.chevron_right_rounded, size: 18, color: AzColors.blue700),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _DraftCard extends StatelessWidget {
  const _DraftCard({required this.draft, required this.products, required this.onChanged});

  final QuoteDraft draft;
  final List<Product> products;
  final Future<void> Function() onChanged;

  @override
  Widget build(BuildContext context) {
    final p = products.where((x) => x.id == draft.productId).firstOrNull;
    final left = Env.draftLifetime.inDays - DateTime.now().difference(draft.savedAt).inDays;
    return AzCard(
      onTap: () async {
        await context.push(AppRoutes.quote, QuoteArgs(productId: draft.productId, draft: draft));
        await onChanged();
      },
      child: Row(
        children: [
          const AzIconTile(icon: Icons.calculate_outlined),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Reprendre votre devis', style: AzText.titleSmall),
                const SizedBox(height: 2),
                Text(
                  '${p?.title ?? 'Devis'} · étape ${draft.step.clamp(1, 4)} sur 4 · conservé encore $left jours',
                  style: AzText.caption.copyWith(fontWeight: FontWeight.w500, fontSize: 13),
                ),
              ],
            ),
          ),
          Text('Reprendre', style: AzText.bodyMedium.copyWith(color: AzColors.orange700, fontWeight: FontWeight.w800)),
        ],
      ),
    );
  }
}

class _QuickActions extends StatelessWidget {
  const _QuickActions({required this.hasContracts, this.firstContract});

  final bool hasContracts;
  final Contract? firstContract;

  @override
  Widget build(BuildContext context) {
    final shell = ShellScope.maybeOf(context);
    final items = <(IconData, String, bool, VoidCallback)>[
      (Icons.calculate_outlined, 'Obtenir un devis', false, () => shell?.goTo(ShellTab.catalog)),
      (Icons.add_circle_outline, 'Souscrire', false, () => openProduct(context, 'auto')),
      (Icons.shield_outlined, 'Mes contrats', false, () => shell?.goTo(ShellTab.contracts)),
      (Icons.description_outlined, 'Mes documents', false, () {
        if (firstContract == null) {
          showAzSnack(context, 'Aucun document : souscrivez d’abord un contrat.');
        } else {
          context.push(AppRoutes.contract, firstContract!.id);
        }
      }),
      (Icons.report_outlined, 'Déclarer un sinistre', true, () {
        if (!hasContracts) {
          showAzSnack(context, 'Un contrat actif est nécessaire pour déclarer un sinistre.');
        } else {
          context.push(AppRoutes.claimNew);
        }
      }),
      (Icons.support_agent_outlined, 'Contacter un conseiller', false, () => context.push(AppRoutes.support)),
    ];
    return LayoutBuilder(builder: (context, c) {
      final columns = c.maxWidth < 330 ? 2 : 3; // petits écrans (320 dp)
      final w = (c.maxWidth - 10 * (columns - 1)) / columns;
      return Wrap(
        spacing: 10,
        runSpacing: 10,
        children: [
          for (final (icon, label, accent, onTap) in items)
            SizedBox(
              width: w,
              child: AzCard(
                onTap: onTap,
                semanticLabel: label,
                padding: const EdgeInsets.fromLTRB(12, 14, 10, 14),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(minHeight: 76),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      AzIconTile(
                        icon: icon,
                        size: 40,
                        background: accent ? AzColors.orange50 : AzColors.blue50,
                        color: accent ? AzColors.orange700 : AzColors.blue700,
                      ),
                      const SizedBox(height: 10),
                      Text(label, maxLines: 2, style: AzText.label.copyWith(color: AzColors.ink, fontWeight: FontWeight.w700, height: 17 / 13)),
                    ],
                  ),
                ),
              ),
            ),
        ],
      );
    });
  }
}

class _ContractMini extends StatelessWidget {
  const _ContractMini({required this.contract});

  final Contract contract;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 248,
      child: AzCard(
        onTap: () => context.push(AppRoutes.contract, contract.id),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                AzIconTile(icon: productIcon(contract.productId), size: 40),
                const Spacer(),
                AzStatusBadge.contract(contract.status),
              ],
            ),
            const SizedBox(height: 10),
            Text(contract.name, style: AzText.titleSmall),
            Text(contract.insuredObject, maxLines: 1, overflow: TextOverflow.ellipsis, style: AzText.caption.copyWith(fontSize: 13, fontWeight: FontWeight.w500)),
            const Spacer(),
            const Divider(height: 14, color: AzColors.lineSoft),
            Row(
              children: [
                const Text('Échéance', style: AzText.caption),
                const Spacer(),
                Text(formatDate(contract.endDate), style: AzText.caption.copyWith(color: AzColors.ink, fontWeight: FontWeight.w800)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ProductMini extends StatelessWidget {
  const _ProductMini({required this.product});

  final Product product;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: product.title,
      excludeSemantics: true,
      child: InkWell(
        onTap: () => openProduct(context, product.id),
        borderRadius: BorderRadius.circular(18),
        child: SizedBox(
          width: 132,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                height: 112,
                width: 132,
                decoration: BoxDecoration(color: productTint(product.id), borderRadius: BorderRadius.circular(18)),
                child: Icon(productIcon(product.id, code: product.iconCode), size: 46, color: AzColors.blue700),
              ),
              const SizedBox(height: 8),
              Text(product.shortName, style: AzText.bodyMedium.copyWith(color: AzColors.ink, fontWeight: FontWeight.w700)),
            ],
          ),
        ),
      ),
    );
  }
}

class _HelpCard extends StatelessWidget {
  const _HelpCard();

  @override
  Widget build(BuildContext context) {
    // Coordonnées et disponibilité fournies par le serveur : absentes = carte masquée.
    return ValueListenableBuilder<ContactInfo>(
      valueListenable: ContactDirectory.info,
      builder: (context, info, _) {
        final canCall = info.primaryPhone != null;
        final canChat = info.whatsapp != null;
        if (!canCall && !canChat) return const SizedBox.shrink();
        final availability = info.availabilityText;
        return AzCard(
          padding: const EdgeInsets.all(18),
          child: Column(
            children: [
              Row(
                children: [
                  const AzIconTile(icon: Icons.help_outline_rounded, background: AzColors.orange50, color: AzColors.orange700),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Besoin d’aide ?', style: AzText.titleSmall),
                        if (availability != null)
                          Text(availability, style: AzText.caption.copyWith(fontSize: 13, fontWeight: FontWeight.w500)),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  if (canCall)
                    Expanded(child: AzButton.secondary('Appeler', icon: Icons.call_outlined, height: 46, onPressed: () => Launch.phone(context))),
                  if (canCall && canChat) const SizedBox(width: 10),
                  if (canChat)
                    Expanded(child: AzButton('WhatsApp', icon: Icons.chat_outlined, height: 46, onPressed: () => Launch.whatsapp(context))),
                ],
              ),
            ],
          ),
        );
      },
    );
  }
}
