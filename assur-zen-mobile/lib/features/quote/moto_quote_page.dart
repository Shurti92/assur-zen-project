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
        child: AsyncView<MotoQuoteForm>(
          load: () => context.repos.motoQuotes.getForm(),
          builder: (context, form, reload) => _MotoQuoteBody(form: form, title: title),
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
                child: AzChipWrap<String>(
                  options: form.vehicleTypes,
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
              hint: form.brands.isEmpty ? null : 'Tapez pour chercher une marque',
              controller: _brand,
              options: form.brands,
              error: _errors['brand'],
              onChanged: (_) => _changed(),
            ),
            AzTextField(
              label: 'Modèle',
              controller: _model,
              error: _errors['model'],
              capitalization: TextCapitalization.words,
              onChanged: (_) => _changed(),
            ),
            AzTextField(
              label: 'Immatriculation',
              controller: _plate,
              error: _errors['registrationNumber'],
              capitalization: TextCapitalization.characters,
              onChanged: (_) => _changed(),
            ),
            AzTextField(
              label: 'Numéro de châssis (si pas d’immatriculation)',
              controller: _chassis,
              capitalization: TextCapitalization.characters,
              onChanged: (_) => _changed(),
            ),
            if (form.durations.isNotEmpty)
              QuoteFieldSection(
                label: 'Durée de la garantie',
                error: _errors['durationMonths'],
                child: AzChipWrap<int>(
                  options: [for (final d in form.durations) d.months],
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
          ),
        const SizedBox(height: 12),
        AzButton.secondary('Être rappelé par un conseiller', onPressed: () => showAdvisorSheet(context)),
      ],
    );
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
  });

  final MotoQuoteSimulation simulation;
  final MotoQuote? quote;
  final bool busy;
  final VoidCallback onSave;
  final VoidCallback onAccept;

  @override
  Widget build(BuildContext context) {
    final p = simulation.premium;
    final q = quote;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        AzHeroCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(q == null ? 'Votre prime' : 'Devis ${q.reference}', style: AzText.bodyMedium.copyWith(color: Colors.white70)),
              const SizedBox(height: 4),
              Text(formatFcfa(p.total), style: AzText.headline.copyWith(color: Colors.white)),
              if (simulation.effectiveDate != null && simulation.expiryDate != null) ...[
                const SizedBox(height: 4),
                Text(
                  'Du ${formatDate(simulation.effectiveDate)} au ${formatDate(simulation.expiryDate)}',
                  style: AzText.bodyMedium.copyWith(color: Colors.white70),
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 12),
        // Une ligne à 0 n'est pas affichée (ex. taxes non communiquées, CEDEAO propre au VTC).
        AzSummaryCard(rows: [
          ('Prime nette', formatFcfa(p.netPremium)),
          if (p.discount > 0) ('Remise', '- ${formatFcfa(p.discount)}'),
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
          AzCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                for (final g in simulation.guarantees) ...[
                  Text(g.label, style: AzText.bodyMedium.copyWith(fontWeight: FontWeight.w700)),
                  for (final d in g.details)
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Row(
                        children: [
                          Expanded(child: Text(d.label, style: AzText.body.copyWith(color: AzColors.muted))),
                          // Montant déjà formaté par le serveur ; absent = rien d'affiché.
                          if (d.amount != null) Text(d.amount!, style: AzText.bodyMedium),
                        ],
                      ),
                    ),
                  const SizedBox(height: 10),
                ],
              ],
            ),
          ),
        ],
        const SizedBox(height: 16),
        if (q == null)
          AzButton('Enregistrer mon devis', loading: busy, onPressed: onSave)
        else ...[
          if (q.validUntil != null && q.status == MotoQuoteStatus.calcule)
            AzBanner(kind: AzBannerKind.info, text: 'Prix garanti jusqu’au ${formatDate(q.validUntil)}.'),
          if (q.status == MotoQuoteStatus.accepte)
            const AzBanner(kind: AzBannerKind.success, text: 'Devis accepté.')
          else if (q.status == MotoQuoteStatus.expire)
            const AzBanner(kind: AzBannerKind.warning, text: 'Ce devis a expiré. Modifiez la saisie pour en calculer un nouveau.'),
          if (q.canAccept) ...[
            const SizedBox(height: 12),
            AzButton('Accepter ce devis', loading: busy, onPressed: onAccept),
          ],
        ],
      ],
    );
  }
}
