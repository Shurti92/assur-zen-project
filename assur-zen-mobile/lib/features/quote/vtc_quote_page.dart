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
import 'moto_quote_page.dart';

/// Libellé d'une énergie renvoyée par le serveur (code inconnu : affiché tel quel).
String vtcEnergyLabel(String code) => switch (code) {
      'ESSENCE' => 'Essence',
      'DIESEL' => 'Diesel',
      'HYBRIDE' => 'Hybride',
      'ELECTRIQUE' => 'Électrique',
      _ => code,
    };

/// Devis en ligne VTC (assurance automobile). Tout vient du serveur : énergies, durées, marques,
/// plateformes, nombre de places, prix, formule et garanties. Rien n'est calculé ici.
///
/// 1. Saisie → « Calculer » : simulation publique (rien n'est enregistré).
/// 2. « Enregistrer mon devis » (compte requis) : prix figé, référence DEV-….
/// 3. « Accepter ce devis ».
class VtcQuotePage extends StatelessWidget {
  const VtcQuotePage({super.key, this.title});

  /// Nom du produit tel que le catalogue du serveur le donne.
  final String? title;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AzColors.background,
      appBar: const AzTopBar(title: 'Devis VTC'),
      body: SafeArea(
        child: AzContentWidth(
          child: AsyncView<VtcQuoteForm>(
            load: () => context.repos.motoQuotes.getVtcForm(),
            builder: (context, form, reload) => _VtcQuoteBody(form: form, title: title),
          ),
        ),
      ),
    );
  }
}

class _VtcQuoteBody extends StatefulWidget {
  const _VtcQuoteBody({required this.form, this.title});

  final VtcQuoteForm form;
  final String? title;

  @override
  State<_VtcQuoteBody> createState() => _VtcQuoteBodyState();
}

class _VtcQuoteBodyState extends State<_VtcQuoteBody> {
  final _brand = TextEditingController();
  final _model = TextEditingController();
  final _power = TextEditingController();
  final _year = TextEditingController();
  final _platform = TextEditingController();
  final _plate = TextEditingController();
  final _chassis = TextEditingController();

  String _energy = '';
  int? _seats;

  /// « oui » / « non » ; vide tant que le client n'a pas répondu (le serveur l'exige).
  String _declared = '';
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
    for (final c in [_brand, _model, _power, _year, _platform, _plate, _chassis]) {
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

  VtcQuoteRequest? _request() {
    final form = widget.form;
    final errors = <String, String>{};
    final power = int.tryParse(_power.text.trim());
    final year = int.tryParse(_year.text.trim());
    final maxYear = DateTime.now().year + 1;
    if (_brand.text.trim().isEmpty) errors['brand'] = 'Indiquez la marque.';
    if (_model.text.trim().isEmpty) errors['model'] = 'Indiquez le modèle.';
    if (_energy.isEmpty) errors['energy'] = 'Choisissez l’énergie.';
    if (power == null || power < form.minFiscalPower || power <= 0) {
      errors['fiscalPower'] = form.minFiscalPower > 0
          ? 'Indiquez la puissance fiscale (${form.minFiscalPower} CV minimum).'
          : 'Indiquez la puissance fiscale en CV.';
    }
    if (_seats == null) errors['seats'] = 'Choisissez le nombre de places.';
    if (year == null || year < 1900 || year > maxYear) {
      errors['firstRegistrationYear'] = 'Indiquez une année valide (par exemple ${DateTime.now().year}).';
    }
    if (_declared.isEmpty) errors['vtcDeclared'] = 'Indiquez si le véhicule est déclaré VTC.';
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
    return VtcQuoteRequest(
      fiscalPower: power!,
      energy: _energy,
      brand: _brand.text.trim(),
      model: _model.text.trim(),
      firstRegistrationYear: year!,
      seats: _seats!,
      vtcDeclared: _declared == 'oui',
      durationMonths: _months,
      effectiveDate: _effective,
      platform: _platform.text,
      registrationNumber: _plate.text,
      chassisNumber: _chassis.text,
      formulaId: form.formulaId,
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
      final sim = await context.repos.motoQuotes.simulateVtc(request);
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
      final quote = await context.repos.motoQuotes.createVtc(request);
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
    final seatOptions = form.seatOptions;
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
      children: [
        if (Env.useMock) const Padding(padding: EdgeInsets.only(bottom: 12), child: AzDemoNote()),
        if (widget.title != null && widget.title!.isNotEmpty) Text(widget.title!, style: AzText.headline),
        const SizedBox(height: 16),
        AzGap(
          gap: 16,
          children: [
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
            if (form.energies.isNotEmpty)
              QuoteFieldSection(
                label: 'Énergie',
                error: _errors['energy'],
                child: AzChipFill<String>(
                  options: form.energies,
                  minChipWidth: 110,
                  selected: _energy,
                  labelOf: vtcEnergyLabel,
                  onSelected: (v) {
                    _energy = v;
                    _changed();
                  },
                ),
              ),
            AzTextField(
              label: 'Puissance fiscale (CV)',
              hint: 'Ex. : 5',
              controller: _power,
              error: _errors['fiscalPower'],
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              maxLength: 2,
              onChanged: (_) => _changed(),
            ),
            if (seatOptions.isNotEmpty)
              QuoteFieldSection(
                label: 'Nombre de places',
                error: _errors['seats'],
                child: AzChipFill<int>(
                  options: seatOptions,
                  minChipWidth: 52,
                  selected: _seats,
                  onSelected: (v) {
                    _seats = v;
                    _changed();
                  },
                ),
              ),
            AzTextField(
              label: 'Année de première mise en circulation',
              hint: 'AAAA',
              controller: _year,
              error: _errors['firstRegistrationYear'],
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              maxLength: 4,
              onChanged: (_) => _changed(),
            ),
            QuoteFieldSection(
              label: 'Le véhicule est-il déclaré VTC ?',
              error: _errors['vtcDeclared'],
              child: AzChipFill<String>(
                options: const ['oui', 'non'],
                minChipWidth: 100,
                selected: _declared,
                labelOf: (v) => v == 'oui' ? 'Oui' : 'Non',
                onSelected: (v) {
                  _declared = v;
                  _changed();
                },
              ),
            ),
            // Plateformes proposées par le serveur ; facultatif, saisie libre possible.
            AzAutocompleteField(
              label: 'Plateforme (facultatif)',
              hint: form.platforms.isEmpty ? 'Saisissez la plateforme' : 'Tapez pour chercher une plateforme',
              controller: _platform,
              options: form.platforms,
              error: _errors['platform'],
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
                  selected: _months,
                  minChipWidth: 120,
                  // Les durées viennent du serveur ; le prix n'apparaît qu'au calcul.
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
