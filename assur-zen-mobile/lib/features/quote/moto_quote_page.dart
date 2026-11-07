import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app/app_scope.dart';
import '../../app/env.dart';
import '../../app/routes.dart';
import '../../core/network/api_exception.dart';
import '../../core/ui.dart';
import '../../data/models/moto_quote.dart';
import '../auth/register_page.dart';
import '../catalog/advisor_sheet.dart';
import '../payment/payment_page.dart';

/// Devis en ligne moto. Tout vient du serveur : types de véhicule, durées, prix,
/// formule et garanties. Rien n'est calculé ni écrit en dur ici.
///
/// 1. Saisie → « Calculer » : simulation publique (rien n'est enregistré).
/// 2. « Enregistrer mon devis » (compte requis) : prix figé, référence DEV-….
/// 3. « Accepter ce devis ».
class MotoQuotePage extends StatelessWidget {
  const MotoQuotePage({super.key, this.title});

  /// Nom du produit tel que le catalogue du serveur le donne (ex. fiche produit).
  final String? title;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AzColors.background,
      appBar: const AzTopBar(title: 'Devis moto'),
      body: SafeArea(
        child: AzContentWidth(
          child: AsyncView<MotoQuoteForm>(
            load: () => context.repos.motoQuotes.getForm(),
            builder: (context, form, reload) => _MotoQuoteBody(form: form, title: title),
          ),
        ),
      ),
    );
  }
}

class _MotoQuoteBody extends StatefulWidget {
  const _MotoQuoteBody({required this.form, this.title});

  final MotoQuoteForm form;
  final String? title;

  @override
  State<_MotoQuoteBody> createState() => _MotoQuoteBodyState();
}

class _MotoQuoteBodyState extends State<_MotoQuoteBody> {
  final _cc = TextEditingController();
  final _brand = TextEditingController();
  final _model = TextEditingController();
  final _plate = TextEditingController();
  final _chassis = TextEditingController();

  late String _type = widget.form.vehicleTypes.isEmpty ? '' : widget.form.vehicleTypes.first;
  late int _months = widget.form.durations.isEmpty ? 0 : widget.form.durations.first.months;
  late DateTime _effective = _firstDay;

  /// Erreurs par champ (côté client, puis `field` renvoyé par le serveur).
  final Map<String, String> _errors = {};
  MotoQuoteSimulation? _simulation;
  MotoQuote? _saved;
  bool _busy = false;
  String? _globalError;

  DateTime get _firstDay {
    final min = widget.form.minEffectiveDate ?? DateTime.now();
    return DateTime(min.year, min.month, min.day);
  }

  @override
  void dispose() {
    for (final c in [_cc, _brand, _model, _plate, _chassis]) {
      c.dispose();
    }
    super.dispose();
  }

  /// Toute modification invalide le résultat affiché : le prix est celui de la saisie courante.
  void _changed() {
    setState(() {
      _simulation = null;
      _saved = null;
      _errors.clear();
      _globalError = null;
    });
  }

  String _typeLabel(String code) => switch (code) {
        'MOTO' => 'Moto',
        'TRICYCLE' => 'Tricycle',
        _ => code,
      };

  MotoQuoteRequest? _request() {
    final errors = <String, String>{};
    final cc = int.tryParse(_cc.text.trim());
    if (_type.isEmpty) errors['vehicleType'] = 'Choisissez un type de véhicule.';
    if (cc == null || cc <= 0) errors['cylinderCc'] = 'Indiquez la cylindrée en cm³.';
    if (_brand.text.trim().isEmpty) errors['brand'] = 'Indiquez la marque.';
    if (_model.text.trim().isEmpty) errors['model'] = 'Indiquez le modèle.';
    if (_plate.text.trim().isEmpty && _chassis.text.trim().isEmpty) {
      errors['registrationNumber'] = 'Indiquez l’immatriculation ou le numéro de châssis.';
    }
    if (_months <= 0) errors['durationMonths'] = 'Choisissez une durée.';
    if (errors.isNotEmpty) {
      setState(() {
        _errors
          ..clear()
          ..addAll(errors);
      });
      return null;
    }
    return MotoQuoteRequest(
      vehicleType: _type,
      cylinderCc: cc!,
      brand: _brand.text.trim(),
      model: _model.text.trim(),
      durationMonths: _months,
      effectiveDate: _effective,
      registrationNumber: _plate.text,
      chassisNumber: _chassis.text,
      formulaId: widget.form.formulaId,
    );
  }

  void _showError(Object e) {
    if (e is ApiException && e.field != null) {
      setState(() {
        _errors[e.field!] = e.message;
        _globalError = null;
      });
    } else {
      setState(() => _globalError = errorMessage(e));
    }
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() {
      _busy = true;
      _globalError = null;
    });
    try {
      await action();
    } catch (e) {
      if (mounted) _showError(e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _simulate() async {
    final request = _request();
    if (request == null) return;
    await _run(() async {
      final sim = await context.repos.motoQuotes.simulate(request);
      if (mounted) setState(() => _simulation = sim);
    });
  }

  Future<void> _save() async {
    final request = _request();
    if (request == null) return;
    final session = context.session;
    if (!session.isLoggedIn) {
      // Le compte n'est demandé qu'au moment d'enregistrer (le calcul reste public).
      final ok = await context.push<bool>(AppRoutes.register, const RegisterArgs(returnToCaller: true));
      if (ok != true || !mounted) return;
    }
    await _run(() async {
      final quote = await context.repos.motoQuotes.create(request);
      if (mounted) setState(() => _saved = quote);
    });
  }

  Future<void> _accept() async {
    final quote = _saved;
    if (quote == null) return;
    await _run(() async {
      final accepted = await context.repos.motoQuotes.accept(quote.reference);
      if (mounted) setState(() => _saved = accepted);
    });
  }

  @override
  Widget build(BuildContext context) {
    final form = widget.form;
    final sim = _saved?.simulation ?? _simulation;
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
      children: [
        if (Env.useMock) const Padding(padding: EdgeInsets.only(bottom: 12), child: AzDemoNote()),
        if (widget.title != null && widget.title!.isNotEmpty) Text(widget.title!, style: AzText.headline),
        const SizedBox(height: 16),
        AzGap(
          gap: 16,
          children: [
            if (form.vehicleTypes.length > 1)
              QuoteFieldSection(
                label: 'Type de Moto',
                error: _errors['vehicleType'],
                child: AzChipFill<String>(
                  options: form.vehicleTypes,
                  minChipWidth: 120,
                  selected: _type,
                  labelOf: _typeLabel,
                  onSelected: (v) {
                    _type = v;
                    _changed();
                  },
                ),
              ),
            AzTextField(
              label: 'Cylindrée (cm³)',
              hint: 'Ex. : 125',
              controller: _cc,
              error: _errors['cylinderCc'],
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              maxLength: 4,
              onChanged: (_) => _changed(),
            ),
            // Marques proposées par le serveur : auto-complétion (saisie libre possible).
            AzAutocompleteField(
              label: 'Marque',
              hint: form.brands.isEmpty ? 'Saisissez la marque' : 'Tapez pour chercher une marque',
              controller: _brand,
              options: form.brands,
              error: _errors['brand'],
              onChanged: (_) => _changed(),
            ),
            AzTextField(
              label: 'Modèle',
              hint: 'Saisissez le modèle',
              controller: _model,
              error: _errors['model'],
              capitalization: TextCapitalization.words,
              onChanged: (_) => _changed(),
            ),
            AzTextField(
              label: 'Immatriculation',
              hint: 'Ex. : 1234 AB 01',
              controller: _plate,
              error: _errors['registrationNumber'],
              capitalization: TextCapitalization.characters,
              onChanged: (_) => _changed(),
            ),
            AzTextField(
              label: 'Numéro de châssis (si pas d’immatriculation)',
              hint: 'Saisissez le numéro de châssis',
              controller: _chassis,
              capitalization: TextCapitalization.characters,
              onChanged: (_) => _changed(),
            ),
            if (form.durations.isNotEmpty)
              QuoteFieldSection(
                label: 'Durée de la garantie',
                error: _errors['durationMonths'],
                child: AzChipFill<int>(
                  options: [for (final d in form.durations) d.months],
                  minChipWidth: 120,
                  selected: _months,
                  // Les durées viennent du serveur ; le prix s'affiche dans le résultat.
                  labelOf: (m) => '$m mois',
                  onSelected: (v) {
                    _months = v;
                    _changed();
                  },
                ),
              ),
            AzDateField(
              label: 'Date d’effet',
              value: _effective,
              error: _errors['effectiveDate'],
              firstDate: _firstDay,
              lastDate: _firstDay.add(const Duration(days: 365)),
              onChanged: (d) {
                _effective = d;
                _changed();
              },
            ),
          ],
        ),
        const SizedBox(height: 20),
        if (_globalError != null) ...[
          AzBanner(kind: AzBannerKind.error, text: _globalError!),
          const SizedBox(height: 12),
        ],
        if (sim == null)
          AzButton('Calculer mon devis', loading: _busy, onPressed: _simulate)
        else
          MotoQuoteResult(
            simulation: sim,
            quote: _saved,
            busy: _busy,
            onSave: _save,
            onAccept: _accept,
            onPay: _saved == null ? null : () => payQuote(context, _saved!),
          ),
        const SizedBox(height: 12),
        AzButton.secondary('Être rappelé par un conseiller', onPressed: () => showAdvisorSheet(context)),
      ],
    );
  }
}

/// Ouvre le paiement d'un devis accepté. Le montant et les contrôles viennent du serveur ; la page de
/// paiement ne dit « reçu » qu'après la confirmation du serveur.
Future<void> payQuote(BuildContext context, MotoQuote quote) async {
  final v = quote.voyage;
  await context.push(
    AppRoutes.payment,
    PaymentArgs(
      payableReference: quote.reference,
      amount: quote.simulation.premium.total,
      // Voyage : la durée est en jours et l'objet est la destination (jamais de « mois » technique).
      caption: v != null ? 'Paiement du devis${v.durationLabel == null ? '' : ' · ${v.durationLabel}'}' : 'Paiement du devis · ${quote.simulation.durationMonths} mois',
      objectLabel: v != null ? '${v.countryName} · ${quote.reference}' : '${quote.brand} ${quote.model} · ${quote.reference}',
      isQuotePayment: true,
    ),
  );
}

/// État du paiement et du contrat d'un devis : tout vient du serveur, rien n'est supposé.
List<Widget> _conversion(BuildContext context, MotoQuote q, VoidCallback? onPay) {
  final ref = q.conversion.paymentReference;
  switch (q.status) {
    case MotoQuoteStatus.paye:
      return [
        const SizedBox(height: 12),
        AzBanner(
          kind: AzBannerKind.success,
          title: 'Paiement reçu.',
          text: 'Votre contrat est en cours d’émission.${q.conversion.paidAt == null ? '' : ' Payé le ${formatDate(q.conversion.paidAt)}.'}',
        ),
      ];
    case MotoQuoteStatus.converti:
      return [
        const SizedBox(height: 12),
        AzBanner(
          kind: AzBannerKind.success,
          title: 'Votre contrat est émis.',
          text: q.policyNumber == null ? 'Retrouvez-le dans « Mes assurances ».' : 'Contrat ${q.policyNumber}.',
        ),
        if (q.contractId != null) ...[
          const SizedBox(height: 12),
          AzButton('Voir mon contrat', height: 52, radius: 16, onPressed: () => context.push(AppRoutes.contract, q.contractId)),
        ],
      ];
    case MotoQuoteStatus.accepte:
      return [
        if (q.conversion.status == QuoteConversionStatus.pending) ...[
          const SizedBox(height: 12),
          AzBanner(kind: AzBannerKind.info, text: ref == null ? 'Un paiement est en cours.' : 'Un paiement est en cours ($ref).'),
        ] else if (q.conversion.status == QuoteConversionStatus.failed) ...[
          const SizedBox(height: 12),
          const AzBanner(kind: AzBannerKind.warning, text: 'Le dernier paiement n’a pas abouti. Vous pouvez réessayer.'),
        ],
        if (onPay != null) ...[
          const SizedBox(height: 12),
          AzButton('Payer et obtenir mon contrat', variant: AzButtonVariant.accent, height: 52, radius: 16, icon: Icons.arrow_forward_rounded, onPressed: onPay),
        ],
      ];
    case MotoQuoteStatus.calcule || MotoQuoteStatus.expire || MotoQuoteStatus.inconnu:
      return const [];
  }
}

class QuoteFieldSection extends StatelessWidget {
  const QuoteFieldSection({super.key, required this.label, required this.child, this.error});

  final String label;
  final Widget child;
  final String? error;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: AzText.label),
        const SizedBox(height: 8),
        child,
        if (error != null) ...[
          const SizedBox(height: 6),
          Text(error!, style: AzText.caption.copyWith(color: AzColors.error)),
        ],
      ],
    );
  }
}

class MotoQuoteResult extends StatelessWidget {
  const MotoQuoteResult({
    super.key,
    required this.simulation,
    required this.quote,
    required this.busy,
    required this.onSave,
    required this.onAccept,
    this.onPay,
  });

  final MotoQuoteSimulation simulation;
  final MotoQuote? quote;
  final bool busy;
  final VoidCallback onSave;
  final VoidCallback onAccept;

  /// Paiement d'un devis accepté. Absent : aucun bouton de paiement.
  final VoidCallback? onPay;

  @override
  Widget build(BuildContext context) {
    final p = simulation.premium;
    final q = quote;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Carte bleue unie : référence ou intitulé, prime en grand, période.
        AzCard(
          color: AzColors.blue700,
          borderColor: null,
          radius: 28,
          padding: const EdgeInsets.all(18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(child: Text(q == null ? 'Votre prime' : 'Devis ${q.reference}', style: AzText.bodyMedium.copyWith(color: AzColors.blue100))),
                  if (q != null) _statusPill(q.status),
                ],
              ),
              const SizedBox(height: 6),
              Text(formatFcfa(p.total), style: AzText.display.copyWith(color: Colors.white, fontSize: 28, height: 1.1)),
              if (simulation.effectiveDate != null && simulation.expiryDate != null) ...[
                const SizedBox(height: 6),
                Text(
                  'Du ${formatDate(simulation.effectiveDate)} au ${formatDate(simulation.expiryDate)}',
                  style: AzText.bodyMedium.copyWith(color: AzColors.blue100),
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 12),
        // Une ligne à 0 n'est pas affichée (ex. taxes non communiquées, CEDEAO propre au VTC).
        AzSummaryCard(radius: 26, rows: [
          ('Prime nette', formatFcfa(p.netPremium)),
          if (p.discount > 0) (_discountLabel(q), '- ${formatFcfa(p.discount)}'),
          if (p.taxes > 0) ('Taxes', formatFcfa(p.taxes)),
          if (p.accessories > 0) ('Accessoires', formatFcfa(p.accessories)),
          if (p.fga > 0) ('FGA', formatFcfa(p.fga)),
          if (p.cedeao > 0) ('CEDEAO', formatFcfa(p.cedeao)),
          if (p.attestation > 0) ('Attestation', formatFcfa(p.attestation)),
          ('Total', formatFcfa(p.total)),
        ]),
        if (simulation.guarantees.isNotEmpty) ...[
          const SizedBox(height: 16),
          const AzSectionHeader('Garanties'),
          const SizedBox(height: 8),
          Builder(builder: (context) {
            final groups = groupGuarantees(simulation.guarantees);
            // Aucune rubrique fournie par le serveur : liste à plat.
            if (groups.isEmpty) {
              return AzCard(
                radius: 26,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [for (final g in simulation.guarantees) ..._guaranteeWidgets(g)],
                ),
              );
            }
            // Rubriques repliables ; la première est dépliée.
            return AzCard(
              radius: 26,
              child: Theme(
                data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
                child: Column(
                  children: [
                    for (var i = 0; i < groups.length; i++)
                      ExpansionTile(
                        key: PageStorageKey('guarantee-group-$i'),
                        initiallyExpanded: i == 0,
                        tilePadding: EdgeInsets.zero,
                        childrenPadding: EdgeInsets.zero,
                        expandedCrossAxisAlignment: CrossAxisAlignment.start,
                        title: Text(
                          groups[i].$1 ?? 'Autres garanties',
                          style: AzText.bodyMedium.copyWith(fontWeight: FontWeight.w700),
                        ),
                        subtitle: Text('${groups[i].$2.length} garantie${groups[i].$2.length > 1 ? 's' : ''}', style: AzText.caption),
                        children: [for (final g in groups[i].$2) ..._guaranteeWidgets(g)],
                      ),
                  ],
                ),
              ),
            );
          }),
          // Renvoi de l'astérisque des montants : texte du serveur, absent = rien.
          if (simulation.guaranteesNote != null) ...[
            const SizedBox(height: 8),
            Text(simulation.guaranteesNote!, style: AzText.caption.copyWith(fontWeight: FontWeight.w500)),
          ],
        ],
        const SizedBox(height: 16),
        if (q == null)
          AzButton('Enregistrer mon devis', variant: AzButtonVariant.accent, height: 52, radius: 16, loading: busy, onPressed: onSave)
        else ...[
          if (q.validUntil != null && q.status == MotoQuoteStatus.calcule)
            AzBanner(kind: AzBannerKind.info, text: 'Prix garanti jusqu’au ${formatDate(q.validUntil)}.'),
          if (q.status == MotoQuoteStatus.accepte)
            const AzBanner(kind: AzBannerKind.success, text: 'Devis accepté.')
          else if (q.status == MotoQuoteStatus.expire)
            const AzBanner(kind: AzBannerKind.warning, text: 'Ce devis a expiré. Modifiez la saisie pour en calculer un nouveau.'),
          if (q.canAccept) ...[
            const SizedBox(height: 12),
            AzButton('Accepter ce devis', variant: AzButtonVariant.accent, height: 52, radius: 16, icon: Icons.arrow_forward_rounded, loading: busy, onPressed: onAccept),
          ],
          ..._conversion(context, q, onPay),
        ],
      ],
    );
  }
}

/// Une garantie : titre puis lignes libellé / montant.
List<Widget> _guaranteeWidgets(MotoGuarantee g) => [
      Text(g.label, style: AzText.bodyMedium.copyWith(fontWeight: FontWeight.w700)),
      for (final d in g.details)
        Padding(
          padding: const EdgeInsets.only(top: 4),
          // Libellé et montant se partagent la largeur (2/5 et 3/5) et passent à la ligne : un montant long
          // n'écrase plus le libellé ni ne déborde.
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Flexible(flex: 2, child: Text(d.label, style: AzText.body.copyWith(color: AzColors.muted))),
              // Montant déjà formaté par le serveur ; absent = rien d'affiché.
              if (d.amount != null) ...[
                const SizedBox(width: 12),
                Expanded(flex: 3, child: Text(keepNumbersTogether(d.amount!), textAlign: TextAlign.right, style: AzText.bodyMedium)),
              ],
            ],
          ),
        ),
      const SizedBox(height: 10),
    ];

/// Libellé de la remise : sur un devis fait dans l'application, toute remise est une remise Zen Points
/// (seul un conseiller peut en saisir une autre). Sans pourcentage ou devis de conseiller : « Remise ».
String _discountLabel(MotoQuote? q) {
  final percent = q?.discountPercent;
  if (q == null || q.isFromAdvisor || percent == null || percent <= 0) return 'Remise';
  return 'Remise Zen Points −${formatPercent(percent)} %';
}

/// Pastille blanche d'état du devis, sur la carte bleue.
Widget _statusPill(MotoQuoteStatus s) {
  final (String label, Color color) = switch (s) {
    MotoQuoteStatus.calcule => ('À accepter', AzColors.warning),
    MotoQuoteStatus.accepte => ('Accepté', AzColors.success),
    MotoQuoteStatus.paye => ('Payé', AzColors.success),
    MotoQuoteStatus.expire => ('Expiré', AzColors.ink2),
    MotoQuoteStatus.converti => ('Souscrit', AzColors.blue700),
    MotoQuoteStatus.inconnu => ('—', AzColors.ink2),
  };
  return Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
    decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(AzRadius.pill)),
    child: Text(label, style: AzText.caption.copyWith(color: color, fontWeight: FontWeight.w700)),
  );
}
