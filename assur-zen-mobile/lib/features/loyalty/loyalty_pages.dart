import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/network/api_exception.dart';
import '../../core/ui.dart';
import '../../data/models/loyalty.dart';

String _pts(int n) => '$n point${n.abs() > 1 ? 's' : ''}';

String _signed(int n) => n > 0 ? '+$n' : '$n';

/// Page Zen Points : solde, niveau, points qui expirent, gestes qui rapportent, derniers mouvements.
/// Tout vient du serveur ; sans adhésion, seule l'invitation et les conditions sont proposées.
class LoyaltyPage extends StatefulWidget {
  const LoyaltyPage({super.key});

  @override
  State<LoyaltyPage> createState() => _LoyaltyPageState();
}

class _LoyaltyPageState extends State<LoyaltyPage> {
  LoyaltyOverview? _overview;
  LoyaltyRules? _rules;
  LoyaltyHistory? _history;
  LoyaltyDiscounts? _discounts;
  Object? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _error = null;
      _loading = true;
    });
    final repo = context.repos.loyalty;
    try {
      final overview = await repo.getOverview();
      LoyaltyRules? rules;
      LoyaltyHistory? history;
      LoyaltyDiscounts? discounts;
      if (overview != null && overview.joined) {
        // Ces sections sont facultatives : une erreur masque seulement la section.
        try {
          rules = await repo.getRules();
        } catch (_) {}
        try {
          history = await repo.getHistory(size: 3);
        } catch (_) {}
        try {
          discounts = await repo.getDiscounts();
        } catch (_) {}
      }
      if (!mounted) return;
      setState(() {
        _overview = overview;
        _rules = rules;
        _history = history;
        _discounts = discounts;
        _loading = false;
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e;
          _loading = false;
        });
      }
    }
  }

  Future<void> _join() async {
    final o = _overview;
    if (o == null) return;
    final joined = await showAzSheet<bool>(
      context,
      title: 'Rejoindre les Zen Points',
      builder: (_) => _JoinSheet(consent: o.consent),
    );
    if (joined == true && mounted) _load();
  }

  @override
  Widget build(BuildContext context) {
    final o = _overview;
    return Scaffold(
      appBar: const AzTopBar(title: 'Zen Points'),
      body: _loading
          ? const AzSkeletonList(count: 3)
          : _error != null
              ? AzErrorState(message: errorMessage(_error!), onRetry: _load)
              : o == null
                  ? const AzEmptyState(
                      icon: Icons.stars_outlined,
                      title: 'Programme indisponible',
                      message: 'Les Zen Points ne sont pas proposés pour le moment.',
                    )
                  : RefreshIndicator(
                      onRefresh: _load,
                      child: ListView(
                        padding: EdgeInsets.fromLTRB(AzSpace.screen, 4, AzSpace.screen, 32 + MediaQuery.viewPaddingOf(context).bottom),
                        children: o.joined ? _joined(o) : _invitation(o),
                      ),
                    ),
    );
  }

  List<Widget> _invitation(LoyaltyOverview o) => [
        Text('Gagnez des points en prenant soin de votre protection.', style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
        const SizedBox(height: 16),
        AzCard(
          radius: 26,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const AzIconTile(icon: Icons.stars_rounded, size: 48),
              const SizedBox(height: 12),
              const Text('Rejoignez les Zen Points', style: AzText.titleSmall),
              const SizedBox(height: 6),
              Text(
                'Vos bons gestes sont récompensés. Les points n’ont aucune valeur en argent.',
                style: AzText.bodyMedium.copyWith(color: AzColors.muted),
              ),
              const SizedBox(height: 14),
              AzButton('Rejoindre', height: 50, radius: 16, onPressed: o.consent.canAccept ? _join : null),
            ],
          ),
        ),
      ];

  List<Widget> _joined(LoyaltyOverview o) {
    final level = o.level;
    final actions = (_rules?.actions ?? const <LoyaltyAction>[]).take(3).toList();
    final entries = _history?.items ?? const <LoyaltyEntry>[];
    return [
      Text('Gagnez des points en prenant soin de votre protection.', style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
      const SizedBox(height: 14),
      _Hero(overview: o),
      if (o.expiring != null) ...[
        const SizedBox(height: 10),
        AzBanner(
          kind: AzBannerKind.warning,
          text: o.expiring!.date == null
              ? '${_pts(o.expiring!.points)} vont expirer bientôt.'
              : '${_pts(o.expiring!.points)} expirent le ${formatDate(o.expiring!.date)}.',
        ),
      ],
      if (_discounts != null) ...[
        const SizedBox(height: 20),
        _DiscountsCard(discounts: _discounts!),
      ],
      if (level != null && actions.isNotEmpty) ...[
        const SizedBox(height: 20),
        AzSectionHeader('Comment gagner', trailing: AzTextLink('Tout voir', onPressed: () => context.push(AppRoutes.loyaltyRules))),
        const SizedBox(height: 8),
        AzCard(
          radius: 26,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
          child: Column(
            children: [
              for (var i = 0; i < actions.length; i++) ...[
                if (i > 0) const Divider(height: 1, color: AzColors.lineSoft),
                _LineRow(title: actions[i].label, subtitle: actions[i].limit, trailing: _signed(actions[i].points), color: AzColors.blue700),
              ],
            ],
          ),
        ),
      ],
      if (entries.isNotEmpty) ...[
        const SizedBox(height: 20),
        AzSectionHeader('Historique', trailing: AzTextLink('Tout voir', onPressed: () => context.push(AppRoutes.loyaltyHistory))),
        const SizedBox(height: 8),
        AzCard(
          radius: 26,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
          child: Column(
            children: [
              for (var i = 0; i < entries.length; i++) ...[
                if (i > 0) const Divider(height: 1, color: AzColors.lineSoft),
                _EntryRow(entry: entries[i]),
              ],
            ],
          ),
        ),
      ],
    ];
  }
}

/// Remises du niveau du client, par produit et par durée. Le serveur ne liste que celles auxquelles
/// le client a droit ; le libellé du produit est celui de la clé connue, sinon « Assurance ».
class _DiscountsCard extends StatelessWidget {
  const _DiscountsCard({required this.discounts});

  final LoyaltyDiscounts discounts;

  static String _product(String key) => switch (key) {
        'moto' => 'Moto et tricycle',
        'automobile-vtc' => 'Auto VTC',
        _ => 'Assurance',
      };

  @override
  Widget build(BuildContext context) {
    final tiers = discounts.tiers;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const AzSectionHeader('Vos remises assurance auto'),
        const SizedBox(height: 8),
        AzCard(
          radius: 26,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
          child: Column(
            children: [
              for (var i = 0; i < tiers.length; i++) ...[
                if (i > 0) const Divider(height: 1, color: AzColors.lineSoft),
                _LineRow(
                  title: 'Durée ${tiers[i].durationLabel}',
                  subtitle: _product(tiers[i].productKey),
                  trailing: '−${formatPercent(tiers[i].percent)} %',
                  color: AzColors.blue700,
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 10),
        const AzBanner(kind: AzBannerKind.success, text: 'Appliquées automatiquement à votre prochain devis.'),
      ],
    );
  }
}

/// Carte bleue : niveau, solde, progression vers le niveau suivant.
class _Hero extends StatelessWidget {
  const _Hero({required this.overview});

  final LoyaltyOverview overview;

  @override
  Widget build(BuildContext context) {
    final level = overview.level;
    final next = level?.next;
    // Progression vers le niveau suivant, en 10 segments (calcul d'affichage seulement).
    final filled = (next == null || next.threshold <= 0 || level == null) ? 10 : ((level.qualifyingPoints / next.threshold) * 10).floor().clamp(0, 10);
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(color: AzColors.blue700, borderRadius: BorderRadius.circular(28)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (level != null)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
              decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.16), borderRadius: BorderRadius.circular(999)),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.stars_rounded, color: Colors.white, size: 16),
                  const SizedBox(width: 6),
                  Flexible(child: Text('Niveau ${level.label}', maxLines: 1, overflow: TextOverflow.ellipsis, style: AzText.label.copyWith(color: Colors.white, fontWeight: FontWeight.w700))),
                ],
              ),
            ),
          const SizedBox(height: 12),
          FittedBox(
            fit: BoxFit.scaleDown,
            alignment: Alignment.centerLeft,
            child: Text('${overview.balance ?? 0} points', style: AzText.display.copyWith(color: Colors.white)),
          ),
          if (level != null) ...[
            const SizedBox(height: 12),
            Row(
              children: [
                for (var i = 0; i < 10; i++) ...[
                  if (i > 0) const SizedBox(width: 5),
                  Expanded(child: Container(height: 7, decoration: BoxDecoration(color: i < filled ? Colors.white : Colors.white.withValues(alpha: 0.22), borderRadius: BorderRadius.circular(4)))),
                ],
              ],
            ),
            const SizedBox(height: 8),
            Text(
              next == null ? 'Vous avez atteint le niveau maximal.' : 'Encore ${_pts(next.pointsToGo)} pour passer ${next.label}.',
              style: AzText.caption.copyWith(color: const Color(0xFFD6E0F3), fontSize: 13, fontWeight: FontWeight.w500),
            ),
          ],
        ],
      ),
    );
  }
}

class _LineRow extends StatelessWidget {
  const _LineRow({required this.title, this.subtitle, required this.trailing, required this.color});

  final String title;
  final String? subtitle;
  final String trailing;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 56),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: AzText.body.copyWith(fontSize: 14.5, fontWeight: FontWeight.w600)),
                  if (subtitle != null) Text(subtitle!, style: AzText.caption.copyWith(fontWeight: FontWeight.w500)),
                ],
              ),
            ),
            const SizedBox(width: 10),
            Text(trailing, style: AzText.body.copyWith(fontSize: 14.5, fontWeight: FontWeight.w800, color: color)),
          ],
        ),
      ),
    );
  }
}

class _EntryRow extends StatelessWidget {
  const _EntryRow({required this.entry});

  final LoyaltyEntry entry;

  @override
  Widget build(BuildContext context) {
    final negative = entry.points < 0;
    return _LineRow(
      title: entry.label,
      subtitle: formatDate(entry.date),
      trailing: _signed(entry.points),
      color: negative ? AzColors.error : AzColors.success,
    );
  }
}

/// Feuille d'adhésion : conditions du serveur, version datée, acceptation explicite.
class _JoinSheet extends StatefulWidget {
  const _JoinSheet({required this.consent});

  final LoyaltyConsent consent;

  @override
  State<_JoinSheet> createState() => _JoinSheetState();
}

class _JoinSheetState extends State<_JoinSheet> {
  bool _busy = false;
  String? _notice;

  Future<void> _accept() async {
    final repo = context.repos.loyalty;
    setState(() {
      _busy = true;
      _notice = null;
    });
    try {
      await repo.setConsent(accepted: true, version: widget.consent.currentVersion);
      if (mounted) Navigator.of(context).pop(true);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _notice = e.code == 'CONSENT_VERSION_MISMATCH' ? 'Les conditions ont changé. Fermez cette fenêtre et relisez-les avant d’accepter.' : errorMessage(e));
    } catch (e) {
      if (mounted) setState(() => _notice = errorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = widget.consent.text;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (text != null) Text(text, style: AzText.body),
        if (_notice != null) ...[
          const SizedBox(height: 12),
          AzBanner(kind: AzBannerKind.warning, text: _notice!),
        ],
        const SizedBox(height: 16),
        AzButton('J’accepte et je rejoins', loading: _busy, onPressed: text == null ? null : _accept),
        const SizedBox(height: 8),
        AzButton.secondary('Plus tard', onPressed: _busy ? null : () => Navigator.of(context).pop(false)),
      ],
    );
  }
}

/// « Comment ça marche » : niveaux et liste complète des gestes, tels que publiés par le serveur.
class LoyaltyRulesPage extends StatelessWidget {
  const LoyaltyRulesPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: const AzTopBar(title: 'Comment ça marche'),
      body: AsyncView<LoyaltyRules>(
        load: () => context.repos.loyalty.getRules(),
        loading: const AzSkeletonList(count: 3),
        builder: (context, rules, reload) => ListView(
          padding: EdgeInsets.fromLTRB(AzSpace.screen, 4, AzSpace.screen, 32 + MediaQuery.viewPaddingOf(context).bottom),
          children: [
            Text('Votre niveau dépend des points gagnés sur 12 mois.', style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
            if (rules.levels.isNotEmpty) ...[
              const SizedBox(height: 14),
              for (final l in rules.levels) ...[
                AzCard(
                  radius: 26,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          const AzIconTile(icon: Icons.stars_rounded, size: 40),
                          const SizedBox(width: 12),
                          Expanded(child: Text(l.label, style: AzText.titleSmall)),
                          Text(l.threshold == 0 ? 'dès 0 point' : 'dès ${l.threshold}', style: AzText.caption.copyWith(fontWeight: FontWeight.w700)),
                        ],
                      ),
                      for (final p in l.perks)
                        Padding(
                          padding: const EdgeInsets.only(top: 8),
                          child: Text('• $p', style: AzText.bodyMedium),
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: 10),
              ],
            ],
            if (rules.actions.isNotEmpty) ...[
              const SizedBox(height: 10),
              const AzSectionHeader('Tous les gestes'),
              const SizedBox(height: 8),
              AzCard(
                radius: 26,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
                child: Column(
                  children: [
                    for (var i = 0; i < rules.actions.length; i++) ...[
                      if (i > 0) const Divider(height: 1, color: AzColors.lineSoft),
                      _LineRow(title: rules.actions[i].label, subtitle: rules.actions[i].limit, trailing: _signed(rules.actions[i].points), color: AzColors.blue700),
                    ],
                  ],
                ),
              ),
            ],
            if ((rules.expiryMonths ?? 0) > 0) ...[
              const SizedBox(height: 12),
              AzBanner(kind: AzBannerKind.info, text: 'Les points durent ${rules.expiryMonths} mois.'),
            ],
          ],
        ),
      ),
    );
  }
}

/// Historique complet des points, de la plus récente à la plus ancienne.
class LoyaltyHistoryPage extends StatefulWidget {
  const LoyaltyHistoryPage({super.key});

  @override
  State<LoyaltyHistoryPage> createState() => _LoyaltyHistoryPageState();
}

class _LoyaltyHistoryPageState extends State<LoyaltyHistoryPage> {
  final List<LoyaltyEntry> _items = [];
  int _total = 0;
  int _page = 0;
  bool _loading = true;
  Object? _error;

  @override
  void initState() {
    super.initState();
    _more();
  }

  Future<void> _more() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final h = await context.repos.loyalty.getHistory(page: _page);
      if (!mounted) return;
      setState(() {
        _items.addAll(h.items);
        _total = h.total;
        _page++;
        _loading = false;
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e;
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: const AzTopBar(title: 'Historique'),
      body: _error != null && _items.isEmpty
          ? AzErrorState(message: errorMessage(_error!), onRetry: _more)
          : _loading && _items.isEmpty
              ? const AzSkeletonList(count: 4)
              : _items.isEmpty
                  ? const AzEmptyState(icon: Icons.history_rounded, title: 'Aucun mouvement', message: 'Vos points gagnés apparaîtront ici.')
                  : ListView(
                      padding: EdgeInsets.fromLTRB(AzSpace.screen, 4, AzSpace.screen, 32 + MediaQuery.viewPaddingOf(context).bottom),
                      children: [
                        AzCard(
                          radius: 26,
                          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
                          child: Column(
                            children: [
                              for (var i = 0; i < _items.length; i++) ...[
                                if (i > 0) const Divider(height: 1, color: AzColors.lineSoft),
                                _EntryRow(entry: _items[i]),
                              ],
                            ],
                          ),
                        ),
                        if (_items.length < _total) ...[
                          const SizedBox(height: 12),
                          AzButton('Voir plus', variant: AzButtonVariant.tonal, height: 50, radius: 16, loading: _loading, onPressed: _more),
                        ],
                      ],
                    ),
    );
  }
}
