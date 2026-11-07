import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/env.dart';
import '../../app/routes.dart';
import '../../core/network/api_exception.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';
import '../catalog/catalog_page.dart' show openProduct;
import '../engagement/engagement_card.dart';
import '../promo/promo_popup.dart';
import '../quote/quote_flow_page.dart';
import '../shell/main_shell.dart';

/// Accueil : une chose à faire, les contrats en cartes, les raccourcis, la protection, l'aide.
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
  int _engagementVersion = 0;

  @override
  void initState() {
    super.initState();
    // Pop-up publicitaire du back-office : une vérification par lancement, jamais bloquante.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) PromoPopups.maybeShow(context);
    });
  }

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
      _engagementVersion++;
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
                if (loading || data == null) ...[
                  const Padding(
                    padding: EdgeInsets.symmetric(horizontal: AzSpace.screen),
                    child: AzSkeleton(height: 168, radius: 26),
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
    final shell = ShellScope.maybeOf(context);
    const pad = EdgeInsets.symmetric(horizontal: AzSpace.screen);

    return [
      // Visiteur sans contrat : invitation à demander un devis. Sinon, la chose à faire en premier.
      if (active.isEmpty)
        Padding(
          padding: pad,
          child: _Welcome(onTap: () => shell?.goTo(ShellTab.catalog)),
        )
      else if (expiring.isNotEmpty)
        Padding(padding: pad, child: _TodoCard(contract: expiring.first, onTap: () => context.push(AppRoutes.contract, expiring.first.id))),
      if (data.draft != null) ...[
        const SizedBox(height: 12),
        Padding(padding: pad, child: _DraftCard(draft: data.draft!, products: data.products, onChanged: _refresh)),
      ],
      if (active.isNotEmpty) ...[
        const SizedBox(height: 22),
        Padding(
          padding: pad,
          child: AzSectionHeader(
            'Mes assurances · ${active.length} active${active.length > 1 ? 's' : ''}',
            actionLabel: 'Tout voir',
            onAction: () => shell?.goTo(ShellTab.contracts),
          ),
        ),
        const SizedBox(height: 6),
        SizedBox(
          height: 176,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: pad,
            itemCount: active.length,
            separatorBuilder: (_, __) => const SizedBox(width: 12),
            itemBuilder: (context, i) => _ContractPass(contract: active[i], color: _passColors[i % _passColors.length]),
          ),
        ),
      ],
      const SizedBox(height: 22),
      const Padding(padding: pad, child: AzSectionHeader('Raccourcis')),
      const SizedBox(height: 8),
      Padding(padding: pad, child: _QuickActions(hasContracts: active.isNotEmpty, firstContract: active.isEmpty ? null : active.first)),
      // Invisible tant que le serveur n'active pas l'engagement ; ne gêne jamais l'accueil.
      EngagementCard(key: ValueKey(_engagementVersion)),
      const SizedBox(height: 22),
      const Padding(padding: pad, child: _HelpBlock()),
      const SizedBox(height: 16),
      if (Env.useMock) const AzDemoNote(),
    ];
  }
}

const _months = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];
const _weekdays = ['lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi', 'dimanche'];

/// « Vendredi 6 novembre » (pour l'en-tête).
String _todayLabel(DateTime d) => '${_weekdays[d.weekday - 1]} ${d.day} ${_months[d.month - 1]}';

class _Header extends StatelessWidget {
  const _Header();

  @override
  Widget build(BuildContext context) {
    final s = AppScope.of(context).session;
    final name = firstName(s.user?.fullName ?? '');
    return Padding(
      padding: const EdgeInsets.fromLTRB(AzSpace.screen, 12, AzSpace.screen, 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(_todayLabel(DateTime.now()).toUpperCase(), style: AzText.overline.copyWith(fontSize: 12)),
                const SizedBox(height: 4),
                Text('${greeting()} $name,', maxLines: 2, overflow: TextOverflow.ellipsis, style: AzText.display.copyWith(fontSize: 25, height: 1.1)),
                const SizedBox(height: 2),
                Text('voici où vous en êtes.', style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
              ],
            ),
          ),
          const SizedBox(width: 8),
          ListenableBuilder(
            listenable: s,
            builder: (context, _) => _Bell(
              unread: s.unreadCount,
              onTap: () async {
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

/// Cloche : icône seule, point orange quand il y a des notifications non lues.
class _Bell extends StatelessWidget {
  const _Bell({required this.unread, required this.onTap});

  final int unread;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: 'Notifications',
      child: Semantics(
        button: true,
        label: unread > 0 ? 'Notifications, $unread non lues' : 'Notifications',
        excludeSemantics: true,
        child: InkResponse(
          onTap: onTap,
          radius: 26,
          child: SizedBox(
            width: 44,
            height: 44,
            child: Stack(
              alignment: Alignment.center,
              children: [
                const Icon(Icons.notifications_none_rounded, color: AzColors.ink, size: 26),
                if (unread > 0)
                  Positioned(
                    top: 8,
                    right: 9,
                    child: Container(
                      width: 12,
                      height: 12,
                      decoration: BoxDecoration(color: AzColors.orange500, shape: BoxShape.circle, border: Border.all(color: AzColors.background, width: 2)),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Visiteur sans contrat : invitation à demander un devis.
class _Welcome extends StatelessWidget {
  const _Welcome({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return AzCard(
      color: AzColors.blue700,
      borderColor: null,
      radius: 26,
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Bienvenue chez AssurZen', style: AzText.label.copyWith(color: AzColors.blue100)),
          const SizedBox(height: 4),
          Text('Protégez ce qui compte pour vous.', style: AzText.display.copyWith(color: Colors.white, fontSize: 21, height: 1.15)),
          const SizedBox(height: 6),
          Text('Obtenez un devis auto ou moto en quelques minutes, sans vous déplacer.', style: AzText.bodyMedium.copyWith(color: AzColors.blue100)),
          const SizedBox(height: 16),
          AzPill('Obtenir un devis', icon: Icons.arrow_forward_rounded, onTap: onTap),
        ],
      ),
    );
  }
}

/// « À faire » : l'échéance la plus proche, avec le bouton Renouveler.
class _TodoCard extends StatelessWidget {
  const _TodoCard({required this.contract, required this.onTap});

  final Contract contract;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = contract;
    final days = c.renewal?.daysLeft;
    final chip = switch (days) {
      null => 'Bientôt',
      0 => 'Aujourd’hui',
      1 => '1 jour',
      final d => '$d jours',
    };
    return AzCard(
      color: AzColors.orange50,
      borderColor: null,
      radius: 26,
      padding: const EdgeInsets.all(18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(child: Text('À FAIRE', style: AzText.overline.copyWith(color: AzColors.orange700))),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                decoration: BoxDecoration(color: AzColors.orange500, borderRadius: BorderRadius.circular(AzRadius.pill)),
                child: Text(chip, style: AzText.caption.copyWith(color: Colors.white, fontWeight: FontWeight.w700)),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Semantics(header: true, child: Text('Renouveler ${c.name}', style: AzText.title.copyWith(fontSize: 18, height: 1.2))),
          const SizedBox(height: 4),
          Text(c.endDate == null ? 'Échéance proche' : 'Échéance le ${formatDate(c.endDate)}', style: AzText.bodyMedium.copyWith(color: AzColors.warningInk)),
          const SizedBox(height: 14),
          AzButton('Renouveler', icon: Icons.arrow_forward_rounded, height: 50, radius: 16, onPressed: onTap),
        ],
      ),
    );
  }
}

/// Bande orange « échéance proche » avec l'action Renouveler (liste des assurances).
class RenewalStrip extends StatelessWidget {
  const RenewalStrip({super.key, required this.contract, required this.onTap});

  final Contract contract;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = contract;
    // Le nom vient du serveur (« Assurance Moto ») ou de la démo (« Moto ») : pas de préfixe en double.
    final name = c.name.toLowerCase().startsWith('assurance') ? c.name : 'Assurance ${c.name}';
    final when = switch (c.renewal?.daysLeft) {
      null => 'échéance proche',
      0 => 'échéance aujourd’hui',
      final d => 'échéance dans $d jours',
    };
    return AzCard(
      color: AzColors.orange50,
      borderColor: null,
      radius: 20,
      onTap: onTap,
      semanticLabel: '$name, $when. Renouveler',
      padding: const EdgeInsets.fromLTRB(14, 10, 10, 10),
      child: Row(
        children: [
          const Icon(Icons.schedule_rounded, color: AzColors.orange700, size: 22),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(name, style: AzText.bodyMedium.copyWith(color: AzColors.warningInk, fontWeight: FontWeight.w800, height: 1.3)),
                Text(when, style: AzText.label.copyWith(color: AzColors.warningInk, fontWeight: FontWeight.w500, height: 1.3)),
              ],
            ),
          ),
          const SizedBox(width: 8),
          ExcludeSemantics(child: AzPill('Renouveler', onTap: onTap, background: AzColors.orange500, foreground: Colors.white)),
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
      radius: 26,
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

/// Couleurs pleines des cartes de contrat, tour à tour.
const _passColors = [AzColors.blue700, AzColors.ink, AzColors.orange500];

/// Carte de contrat « portefeuille » : couleur pleine, produit, assuré, échéance.
class _ContractPass extends StatelessWidget {
  const _ContractPass({required this.contract, required this.color});

  final Contract contract;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final c = contract;
    return SizedBox(
      width: 258,
      child: AzCard(
        color: color,
        borderColor: null,
        radius: 26,
        onTap: () => context.push(AppRoutes.contract, c.id),
        semanticLabel: '${c.name}, ${c.status.label}',
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                AzIconTile(icon: productIcon(c.productId), size: 38, background: const Color(0x29FFFFFF), color: Colors.white, radius: 13),
                const Spacer(),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(color: const Color(0x2EFFFFFF), borderRadius: BorderRadius.circular(AzRadius.pill)),
                  child: Text(c.status.label, style: AzText.caption.copyWith(color: Colors.white, fontWeight: FontWeight.w700)),
                ),
              ],
            ),
            const Spacer(),
            Text(c.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: AzText.title.copyWith(color: Colors.white, fontSize: 18, height: 1.2)),
            Text(c.insuredObject, maxLines: 1, overflow: TextOverflow.ellipsis, style: AzText.caption.copyWith(color: const Color(0xD1FFFFFF), fontSize: 12.5, fontWeight: FontWeight.w500)),
            const SizedBox(height: 10),
            Row(
              children: [
                Text('Échéance', style: AzText.caption.copyWith(color: const Color(0xD1FFFFFF), fontSize: 12.5, fontWeight: FontWeight.w500)),
                const Spacer(),
                Text(formatDate(c.endDate), style: AzText.caption.copyWith(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w800)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Raccourcis en blocs (maquette A) : « Obtenir un devis » domine (orange), puis Souscrire, Sinistre et une rangée de trois.
class _QuickActions extends StatelessWidget {
  const _QuickActions({required this.hasContracts, this.firstContract});

  final bool hasContracts;
  final Contract? firstContract;

  @override
  Widget build(BuildContext context) {
    final shell = ShellScope.maybeOf(context);
    final mini = <(IconData, String, VoidCallback)>[
      // Les devis enregistrés n'existent que pour un client connecté : sans compte, la tuile est masquée.
      if (context.session.isLoggedIn) (Icons.request_quote_outlined, 'Mes devis', () => context.push(AppRoutes.myQuotes)),
      (Icons.shield_outlined, 'Mes contrats', () => shell?.goTo(ShellTab.contracts)),
      (Icons.description_outlined, 'Mes documents', () {
        if (firstContract == null) {
          showAzSnack(context, 'Aucun document : souscrivez d’abord un contrat.');
        } else {
          context.push(AppRoutes.documents);
        }
      }),
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _MinHeight(
          height: 172,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Expanded(
                child: _BlockTile(
                  icon: Icons.calculate_outlined,
                  label: 'Obtenir un devis',
                  caption: 'En quelques minutes',
                  background: AzColors.orange500,
                  foreground: Colors.white,
                  display: true,
                  onTap: () => shell?.goTo(ShellTab.catalog),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  children: [
                    Expanded(
                      child: _BlockTile(icon: Icons.add_rounded, label: 'Souscrire', onTap: () => openProduct(context, 'auto')),
                    ),
                    const SizedBox(height: 10),
                    Expanded(
                      child: _BlockTile(
                        icon: Icons.report_outlined,
                        label: 'Déclarer un sinistre',
                        iconColor: AzColors.orange700,
                        onTap: () {
                          if (!hasContracts) {
                            showAzSnack(context, 'Un contrat actif est nécessaire pour déclarer un sinistre.');
                          } else {
                            context.push(AppRoutes.claimNew);
                          }
                        },
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 10),
        _MinHeight(
          height: 96,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (var i = 0; i < mini.length; i++) ...[
                if (i > 0) const SizedBox(width: 10),
                Expanded(child: _BlockTile(icon: mini[i].$1, label: mini[i].$2, onTap: mini[i].$3)),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

/// Bloc cliquable : icône en haut, libellé en bas.
class _BlockTile extends StatelessWidget {
  const _BlockTile({
    required this.icon,
    required this.label,
    required this.onTap,
    this.caption,
    this.background = Colors.white,
    this.foreground = AzColors.ink,
    this.iconColor,
    this.display = false,
  });

  final IconData icon;
  final String label;
  final String? caption;
  final VoidCallback onTap;
  final Color background;
  final Color foreground;
  final Color? iconColor;

  /// Libellé en Sora (bloc dominant).
  final bool display;

  @override
  Widget build(BuildContext context) {
    final onColor = background == Colors.white;
    return AzCard(
      color: background,
      borderColor: onColor ? AzColors.line : null,
      radius: AzRadius.bento,
      onTap: onTap,
      semanticLabel: label,
      padding: const EdgeInsets.all(14),
      child: SizedBox(
        width: double.infinity,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, size: display ? 28 : 24, color: iconColor ?? (onColor ? AzColors.blue700 : foreground)),
            const Spacer(),
            Text(
              label,
              maxLines: 2,
              style: display
                  ? AzText.section.copyWith(color: foreground, fontSize: 17, height: 1.2)
                  : AzText.label.copyWith(color: foreground, fontWeight: FontWeight.w700, height: 17 / 13),
            ),
            if (caption != null) ...[
              const SizedBox(height: 4),
              Text(caption!, style: AzText.caption.copyWith(color: const Color(0xFFFFE3D0), fontWeight: FontWeight.w500)),
            ],
          ],
        ),
      ),
    );
  }
}

/// Hauteur minimale qui grandit avec le contenu (texte agrandi, libellés sur deux lignes).
class _MinHeight extends StatelessWidget {
  const _MinHeight({required this.height, required this.child});

  final double height;
  final Widget child;

  @override
  Widget build(BuildContext context) => IntrinsicHeight(child: ConstrainedBox(constraints: BoxConstraints(minHeight: height), child: child));
}

/// « Besoin d'aide ? » : horaires, puis Appeler et WhatsApp, tous fournis par le serveur.
class _HelpBlock extends StatelessWidget {
  const _HelpBlock();

  @override
  Widget build(BuildContext context) {
    // Coordonnées et disponibilité fournies par le serveur : absentes = bloc masqué.
    return ValueListenableBuilder<ContactInfo>(
      valueListenable: ContactDirectory.info,
      builder: (context, info, _) {
        final canCall = info.primaryPhone != null;
        final canChat = info.whatsapp != null;
        if (!canCall && !canChat) return const SizedBox.shrink();
        final availability = info.availabilityText;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Semantics(header: true, child: Text('Besoin d’aide ?', style: AzText.section.copyWith(fontSize: 16))),
            if (availability != null) Text(availability, style: AzText.bodyMedium.copyWith(color: AzColors.muted, fontSize: 13)),
            const SizedBox(height: 12),
            Row(
              children: [
                if (canCall)
                  Expanded(
                    child: _HelpButton(label: 'Appeler', icon: Icons.call_outlined, onTap: () => Launch.phone(context)),
                  ),
                if (canCall && canChat) const SizedBox(width: 10),
                if (canChat)
                  Expanded(
                    child: _HelpButton(label: 'WhatsApp', icon: Icons.chat_outlined, filled: true, onTap: () => Launch.whatsapp(context)),
                  ),
              ],
            ),
          ],
        );
      },
    );
  }
}

class _HelpButton extends StatelessWidget {
  const _HelpButton({required this.label, required this.icon, required this.onTap, this.filled = false});

  final String label;
  final IconData icon;
  final VoidCallback onTap;
  final bool filled;

  @override
  Widget build(BuildContext context) {
    final fg = filled ? Colors.white : AzColors.blue700;
    return Semantics(
      button: true,
      label: label,
      excludeSemantics: true,
      child: Material(
        color: filled ? AzColors.blue700 : Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16), side: const BorderSide(color: AzColors.blue700, width: 2)),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 48),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(icon, color: fg, size: 18),
                const SizedBox(width: 8),
                Flexible(child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis, style: AzText.button.copyWith(color: fg, fontSize: 14))),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
