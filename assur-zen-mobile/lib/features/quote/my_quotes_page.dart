import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/moto_quote.dart';
import 'moto_quote_page.dart';
import 'vtc_quote_page.dart' show vtcEnergyLabel;

/// Liste des devis enregistrés du client connecté (`GET /mobile/quotes`).
class MyQuotesPage extends StatelessWidget {
  const MyQuotesPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AzColors.background,
      appBar: const AzTopBar(title: 'Mes devis'),
      body: SafeArea(
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
          builder: (context, quotes, reload) => RefreshIndicator(
            onRefresh: reload,
            child: ListView.separated(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
              itemCount: quotes.length,
              separatorBuilder: (_, __) => const SizedBox(height: 12),
              itemBuilder: (context, i) => _QuoteTile(
                quote: quotes[i],
                // Au retour, la liste est rechargée (le devis a pu être accepté).
                onTap: () async {
                  await context.push(AppRoutes.motoQuoteDetail, quotes[i]);
                  await reload();
                },
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
    return AzCard(
      onTap: onTap,
      semanticLabel: 'Devis ${quote.reference}',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text(quote.reference, style: AzText.section)),
              _badge(quote.status),
            ],
          ),
          if (vehicle.isNotEmpty || id != null) ...[
            const SizedBox(height: 4),
            Text([if (vehicle.isNotEmpty) vehicle, if (id != null) id].join(' · '), style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
          ],
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(child: Text(formatFcfa(quote.simulation.premium.total), style: AzText.headline.copyWith(fontSize: 20))),
              if (quote.validUntil != null && quote.status == MotoQuoteStatus.calcule)
                Text('Valable jusqu’au ${formatDate(quote.validUntil)}', style: AzText.caption),
            ],
          ),
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
      appBar: AzTopBar(title: 'Devis ${_quote.reference}'),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
          children: [
            AzSummaryCard(rows: [
              if (_quote.simulation.formulaName.isNotEmpty) ('Formule', _quote.simulation.formulaName),
              if (vehicle.isNotEmpty) ('Véhicule', vehicle),
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
            ),
          ],
        ),
      ),
    );
  }
}
