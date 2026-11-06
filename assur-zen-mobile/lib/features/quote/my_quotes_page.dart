import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/moto_quote.dart';
import 'moto_quote_page.dart';
import 'voyage_quote_page.dart' show voyageRows;
import 'vtc_quote_page.dart' show vtcEnergyLabel;

/// Liste des devis enregistrés du client connecté (`GET /mobile/quotes`).
class MyQuotesPage extends StatefulWidget {
  const MyQuotesPage({super.key});

  @override
  State<MyQuotesPage> createState() => _MyQuotesPageState();
}

/// Regroupement des états de devis en filtres.
enum _QuoteFilter {
  all('Tous'),
  toAccept('À accepter'),
  inProgress('En cours'),
  subscribed('Souscrits'),
  expired('Expirés');

  const _QuoteFilter(this.label);

  final String label;

  bool matches(MotoQuote q) => switch (this) {
        _QuoteFilter.all => true,
        _QuoteFilter.toAccept => q.status == MotoQuoteStatus.calcule,
        _QuoteFilter.inProgress => q.status == MotoQuoteStatus.accepte || q.status == MotoQuoteStatus.paye,
        _QuoteFilter.subscribed => q.status == MotoQuoteStatus.converti,
        _QuoteFilter.expired => q.status == MotoQuoteStatus.expire,
      };
}

class _MyQuotesPageState extends State<MyQuotesPage> {
  _QuoteFilter _filter = _QuoteFilter.all;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AzColors.background,
      appBar: const AzTopBar(),
      body: SafeArea(
        child: AzContentWidth(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(AzSpace.screen, 0, AzSpace.screen, 4),
                child: Semantics(header: true, child: Text('Mes devis', style: AzText.display.copyWith(fontSize: 28, height: 1.1))),
              ),
              Expanded(
                child: AsyncView<List<MotoQuote>>(
                  load: () => context.repos.motoQuotes.list(),
                  isEmpty: (quotes) => quotes.isEmpty,
                  empty: AzEmptyState(
                    icon: Icons.request_quote_outlined,
                    title: 'Aucun devis enregistré',
                    message: 'Vos devis enregistrés apparaîtront ici.',
                    actionLabel: 'Obtenir un devis moto',
                    onAction: () => context.push(AppRoutes.motoQuote),
                  ),
                  builder: (context, quotes, reload) {
                    final toAccept = quotes.where(_QuoteFilter.toAccept.matches).length;
                    final shown = quotes.where(_filter.matches).toList();
                    // Les filtres sans devis ne sont pas proposés (sauf « Tous » et le filtre en cours).
                    final filters = [for (final f in _QuoteFilter.values) if (f == _QuoteFilter.all || f == _filter || quotes.any(f.matches)) f];
                    return RefreshIndicator(
                      onRefresh: reload,
                      child: ListView(
                        padding: const EdgeInsets.fromLTRB(20, 0, 20, 32),
                        children: [
                          Text(
                            '${quotes.length} devis${toAccept > 0 ? ' · $toAccept à accepter' : ''}',
                            style: AzText.bodyMedium.copyWith(color: AzColors.muted),
                          ),
                          const SizedBox(height: 12),
                          SizedBox(
                            height: 44,
                            child: ListView.separated(
                              scrollDirection: Axis.horizontal,
                              itemCount: filters.length,
                              separatorBuilder: (_, __) => const SizedBox(width: 8),
                              itemBuilder: (context, i) {
                                final f = filters[i];
                                final count = quotes.where(f.matches).length;
                                return _FilterChip(label: f.label, count: count > 0 ? count : null, selected: _filter == f, onTap: () => setState(() => _filter = f));
                              },
                            ),
                          ),
                          for (final q in shown) ...[
                            const SizedBox(height: 10),
                            _QuoteTile(
                              quote: q,
                              // Au retour, la liste est rechargée (le devis a pu être accepté).
                              onTap: () async {
                                await context.push(AppRoutes.motoQuoteDetail, q);
                                await reload();
                              },
                            ),
                          ],
                          if (shown.isEmpty)
                            Padding(
                              padding: const EdgeInsets.symmetric(vertical: 40),
                              child: Text('Aucun devis dans cette catégorie.', textAlign: TextAlign.center, style: AzText.body.copyWith(color: AzColors.muted)),
                            ),
                        ],
                      ),
                    );
                  },
                ),
              ),
            ],
          ),
        ),
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

AzStatusBadge _badge(MotoQuoteStatus s) => switch (s) {
      MotoQuoteStatus.calcule => const AzStatusBadge(label: 'À accepter', foreground: AzColors.warning, background: AzColors.warningBg),
      MotoQuoteStatus.accepte => const AzStatusBadge(label: 'Accepté', foreground: AzColors.success, background: AzColors.successBg),
      MotoQuoteStatus.paye => const AzStatusBadge(label: 'Payé', foreground: AzColors.success, background: AzColors.successBg),
      MotoQuoteStatus.expire => const AzStatusBadge(label: 'Expiré', foreground: AzColors.neutral, background: AzColors.neutralBg),
      MotoQuoteStatus.converti => const AzStatusBadge(label: 'Souscrit', foreground: AzColors.blue700, background: AzColors.blue50),
      MotoQuoteStatus.inconnu => const AzStatusBadge(label: '—', foreground: AzColors.neutral, background: AzColors.neutralBg, dot: false),
    };

class _QuoteTile extends StatelessWidget {
  const _QuoteTile({required this.quote, required this.onTap});

  final MotoQuote quote;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final vehicle = [quote.brand, quote.model].where((s) => s.isNotEmpty).join(' ');
    final id = quote.registrationNumber ?? quote.chassisNumber;
    // Voyage : destination et dates à la place du véhicule.
    final v = quote.voyage;
    final trip = v == null
        ? null
        : [
            v.countryName,
            if (v.departureDate != null && v.returnDate != null) '${formatDate(v.departureDate)} – ${formatDate(v.returnDate)}',
          ].join(' · ');
    final toAccept = quote.status == MotoQuoteStatus.calcule;
    final subscribed = quote.status == MotoQuoteStatus.converti;
    // Montant : le nombre en grand, « FCFA » en petit.
    final total = formatFcfa(quote.simulation.premium.total);
    final cut = total.lastIndexOf('FCFA');
    final amountNumber = cut > 0 ? total.substring(0, cut).trimRight() : total;
    return AzCard(
      radius: 26,
      borderColor: toAccept ? const Color(0xFFF3C7A6) : AzColors.line,
      onTap: onTap,
      semanticLabel: 'Devis ${quote.reference}',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              AzIconTile(
                icon: productIcon(v != null ? 'voyage' : 'moto'),
                background: toAccept ? AzColors.orange50 : AzColors.blue50,
                color: toAccept ? AzColors.orange700 : AzColors.blue700,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(quote.reference, style: AzText.section.copyWith(fontSize: 15)),
                    if (trip != null)
                      Text(trip, style: AzText.caption.copyWith(fontSize: 12.5, fontWeight: FontWeight.w500))
                    else if (vehicle.isNotEmpty || id != null)
                      Text([if (vehicle.isNotEmpty) vehicle, if (id != null) id].join(' · '), style: AzText.caption.copyWith(fontSize: 12.5, fontWeight: FontWeight.w500)),
                    if (quote.isFromAdvisor)
                      Text('Établi par un conseiller', style: AzText.caption.copyWith(color: AzColors.blue700, fontWeight: FontWeight.w700, fontSize: 12.5)),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              _badge(quote.status),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: Text.rich(
                  TextSpan(children: [
                    TextSpan(text: amountNumber, style: AzText.headline.copyWith(fontSize: 22)),
                    if (cut > 0) TextSpan(text: ' FCFA', style: AzText.label.copyWith(color: AzColors.ink, fontWeight: FontWeight.w600)),
                  ]),
                ),
              ),
              if (quote.validUntil != null && toAccept)
                Flexible(child: Text('Valable jusqu’au ${formatDate(quote.validUntil)}', textAlign: TextAlign.right, style: AzText.caption))
              else if (subscribed)
                AzPill('Voir mon contrat', icon: Icons.chevron_right_rounded, background: AzColors.blue50, foreground: AzColors.blue700, onTap: onTap),
            ],
          ),
          if (toAccept) ...[
            const SizedBox(height: 10),
            AzButton('Accepter et payer', variant: AzButtonVariant.accent, height: 50, radius: 16, icon: Icons.arrow_forward_rounded, onPressed: onTap),
          ],
        ],
      ),
    );
  }
}

/// Détail d'un devis enregistré, avec l'acceptation si le serveur l'autorise.
class MotoQuoteDetailPage extends StatefulWidget {
  const MotoQuoteDetailPage({super.key, required this.quote});

  final MotoQuote quote;

  @override
  State<MotoQuoteDetailPage> createState() => _MotoQuoteDetailPageState();
}

class _MotoQuoteDetailPageState extends State<MotoQuoteDetailPage> {
  late MotoQuote _quote = widget.quote;
  bool _busy = false;

  Future<void> _accept() async {
    setState(() => _busy = true);
    try {
      final accepted = await context.repos.motoQuotes.accept(_quote.reference);
      if (mounted) setState(() => _quote = accepted);
    } catch (e) {
      if (mounted) showAzSnack(context, errorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final vehicle = [_quote.brand, _quote.model].where((s) => s.isNotEmpty).join(' ');
    return Scaffold(
      backgroundColor: AzColors.background,
      appBar: const AzTopBar(),
      body: SafeArea(
        child: AzContentWidth(
          child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
          children: [
            AzSummaryCard(radius: 26, rows: [
              if (_quote.simulation.formulaName.isNotEmpty) ('Formule', _quote.simulation.formulaName),
              if (_quote.isFromAdvisor) ('Origine', 'Établi par un conseiller'),
              // Voyage : récapitulatif du voyage ; sinon les lignes du véhicule.
              if (_quote.voyage != null) ...voyageRows(_quote.voyage, null),
              if (_quote.voyage == null && vehicle.isNotEmpty) ('Véhicule', vehicle),
              if (_quote.cylinderCc > 0) ('Cylindrée', '${_quote.cylinderCc} cm³'),
              if (_quote.fiscalPower != null) ('Puissance fiscale', '${_quote.fiscalPower} CV'),
              if (_quote.energy != null) ('Énergie', vtcEnergyLabel(_quote.energy!)),
              if (_quote.seats != null) ('Places', '${_quote.seats}'),
              if (_quote.firstRegistrationYear != null) ('Première mise en circulation', '${_quote.firstRegistrationYear}'),
              if (_quote.vtcDeclared != null) ('Déclaré VTC', _quote.vtcDeclared! ? 'Oui' : 'Non'),
              if (_quote.platform != null) ('Plateforme', _quote.platform!),
              if (_quote.registrationNumber != null) ('Immatriculation', _quote.registrationNumber!),
              if (_quote.chassisNumber != null) ('Châssis', _quote.chassisNumber!),
            ]),
            const SizedBox(height: 16),
            MotoQuoteResult(
              simulation: _quote.simulation,
              quote: _quote,
              busy: _busy,
              onSave: () {},
              onAccept: _accept,
              onPay: () => payQuote(context, _quote),
            ),
          ],
        ),
        ),
      ),
    );
  }
}
