import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/env.dart';
import '../../app/routes.dart';
import '../../core/network/api_exception.dart';
import '../../core/ui.dart';
import '../../data/models/moto_quote.dart';
import '../auth/register_page.dart';
import '../catalog/advisor_sheet.dart';
import 'moto_quote_page.dart';

/// Devis Assurance Voyage. Tout vient du serveur : pays et espaces, bornes d'âge et de durée, date du jour,
/// prix. Rien n'est calculé localement, sauf l'âge et le nombre de jours que la simulation publique demande.
///
/// 1. Saisie → « Calculer » : simulation publique (rien n'est enregistré).
/// 2. « Enregistrer mon devis » (compte requis) : prix figé, référence DEV-….
/// 3. « Accepter ce devis », puis le paiement, comme pour la moto.
class VoyageQuotePage extends StatelessWidget {
  const VoyageQuotePage({super.key, this.title});

  final String? title;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AzColors.background,
      appBar: const AzTopBar(title: 'Devis voyage'),
      body: SafeArea(
        child: AzContentWidth(
          child: AsyncView<VoyageForm>(
            load: () => context.repos.motoQuotes.getVoyageForm(),
            builder: (context, form, reload) => VoyageQuoteBody(form: form, title: title),
          ),
        ),
      ),
    );
  }
}

class VoyageQuoteBody extends StatefulWidget {
  const VoyageQuoteBody({super.key, required this.form, this.title});

  final VoyageForm form;
  final String? title;

  @override
  State<VoyageQuoteBody> createState() => _VoyageQuoteBodyState();
}

class _VoyageQuoteBodyState extends State<VoyageQuoteBody> {
  final _traveler = TextEditingController();
  final _passport = TextEditingController();
  final _birthText = TextEditingController();

  VoyageCountry? _country;
  DateTime? _birth;
  DateTime? _departure;
  DateTime? _return;

  final Map<String, String> _errors = {};
  VoyageSimulation? _simulation;
  MotoQuote? _saved;
  bool _busy = false;
  String? _globalError;

  /// Premier départ : date du jour du serveur ; à défaut (serveur ancien), celle du téléphone.
  DateTime get _firstDeparture {
    final d = widget.form.minDepartureDate ?? DateTime.now();
    return DateTime(d.year, d.month, d.day);
  }

  @override
  void dispose() {
    _traveler.dispose();
    _passport.dispose();
    _birthText.dispose();
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

  VoyageRequest? _request() {
    final f = widget.form;
    final errors = <String, String>{};
    final birth = _birth;
    final out = _departure;
    final back = _return;
    if (_country == null) errors['countryId'] = 'Choisissez un pays de destination.';
    if (birth == null) {
      errors['birthDate'] = _birthText.text.trim().isEmpty ? 'Indiquez la date de naissance.' : 'Saisissez une date valide (JJ/MM/AAAA).';
    } else if (birth.isAfter(_firstDeparture)) {
      errors['birthDate'] = 'La naissance ne peut pas être dans le futur.';
    }
    if (out == null) errors['departureDate'] = 'Indiquez la date de départ.';
    if (back == null) errors['returnDate'] = 'Indiquez la date de retour.';
    if (out != null && back != null) {
      if (back.isBefore(out)) {
        errors['returnDate'] = 'Le retour ne peut pas être avant le départ.';
      } else if (f.maxDurationDays > 0 && inclusiveDays(out, back) > f.maxDurationDays) {
        errors['returnDate'] = 'La durée ne peut pas dépasser ${f.maxDurationDays} jours.';
      }
    }
    if (birth != null && out != null) {
      // putIfAbsent : un message déjà posé pour ce champ (naissance dans le futur) n'est pas écrasé.
      if (birth.isAfter(out)) {
        errors.putIfAbsent('birthDate', () => 'La naissance doit être avant le départ.');
      } else if (f.maxAge > 0 && ageAt(birth, out) > f.maxAge) {
        errors.putIfAbsent('birthDate', () => 'L’âge maximum est de ${f.maxAge} ans.');
      }
    }
    if (errors.isNotEmpty) {
      setState(() {
        _errors
          ..clear()
          ..addAll(errors);
      });
      return null;
    }
    return VoyageRequest(
      countryId: _country!.id,
      birthDate: birth!,
      departureDate: out!,
      returnDate: back!,
      travelerName: _traveler.text,
      passportNumber: _passport.text,
    );
  }

  void _showError(Object e) {
    if (e is ApiException && e.field != null) {
      // Le serveur nomme parfois l'âge ou la durée : on les rattache au champ que le client saisit.
      final field = switch (e.field) {
        'age' => 'birthDate',
        'durationDays' => 'returnDate',
        final f => f!,
      };
      setState(() {
        _errors[field] = e.message;
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
      final sim = await context.repos.motoQuotes.simulateVoyage(request);
      if (mounted) setState(() => _simulation = sim);
    });
  }

  Future<void> _save() async {
    final request = _request();
    if (request == null) return;
    if (!context.session.isLoggedIn) {
      // Le compte n'est demandé qu'au moment d'enregistrer (le calcul reste public).
      final ok = await context.push<bool>(AppRoutes.register, const RegisterArgs(returnToCaller: true));
      if (ok != true || !mounted) return;
    }
    await _run(() async {
      final quote = await context.repos.motoQuotes.createVoyage(request);
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

  Future<void> _pickCountry() async {
    FocusManager.instance.primaryFocus?.unfocus(); // pas de clavier qui revient après le choix
    final picked = await showAzSheet<VoyageCountry>(
      context,
      title: 'Pays de destination',
      builder: (_) => VoyageCountryPicker(form: widget.form, selected: _country),
    );
    if (picked != null) {
      _country = picked;
      _changed();
    }
  }

  @override
  Widget build(BuildContext context) {
    final f = widget.form;
    final saved = _saved;
    final sim = _simulation;
    final out = _departure;
    final back = _return;
    final birth = _birth;
    final days = out != null && back != null && !back.isBefore(out) ? inclusiveDays(out, back) : null;
    final age = birth != null && out != null && !birth.isAfter(out) ? ageAt(birth, out) : null;

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
      children: [
        if (Env.useMock) const Padding(padding: EdgeInsets.only(bottom: 12), child: AzDemoNote()),
        if (widget.title != null && widget.title!.isNotEmpty) Text(widget.title!, style: AzText.headline),
        const SizedBox(height: 16),
        AzGap(
          gap: 16,
          children: [
            AzSelectField(
              label: 'Pays de destination',
              text: _country?.name ?? 'Choisir un pays',
              placeholder: _country == null,
              error: _errors['countryId'],
              onTap: _pickCountry,
            ),
            // Saisie au clavier : un calendrier obligerait à remonter année par année.
            AzTextField(
              label: 'Date de naissance du voyageur',
              hint: 'JJ/MM/AAAA',
              controller: _birthText,
              error: _errors['birthDate'],
              keyboardType: TextInputType.number,
              inputFormatters: const [DateMaskFormatter()],
              maxLength: 10,
              onChanged: (t) {
                _birth = parseDateFr(t);
                _changed();
              },
            ),
            AzDateField(
              label: 'Date de départ',
              value: _departure,
              error: _errors['departureDate'],
              firstDate: _firstDeparture,
              lastDate: _firstDeparture.add(const Duration(days: 365)),
              onChanged: (d) {
                _departure = d;
                // Un retour avant le nouveau départ n'a plus de sens : on le redemande.
                if (_return != null && _return!.isBefore(d)) _return = null;
                _changed();
              },
            ),
            AzDateField(
              label: 'Date de retour',
              value: _return,
              error: _errors['returnDate'],
              firstDate: _departure ?? _firstDeparture,
              lastDate: (_departure ?? _firstDeparture).add(Duration(days: f.maxDurationDays > 0 ? f.maxDurationDays - 1 : 365)),
              onChanged: (d) {
                _return = d;
                _changed();
              },
            ),
            if (days != null || age != null)
              Text(
                [if (age != null) 'Âge au départ : $age ${age > 1 ? 'ans' : 'an'}', if (days != null) 'Durée : $days ${days > 1 ? 'jours' : 'jour'}'].join(' · '),
                style: AzText.bodyMedium.copyWith(color: AzColors.muted),
              ),
            AzTextField(
              label: 'Nom du voyageur (si ce n’est pas vous)',
              hint: 'Laissez vide si vous voyagez vous-même',
              controller: _traveler,
              capitalization: TextCapitalization.words,
              onChanged: (_) => _changed(),
            ),
            AzTextField(
              label: 'Numéro de passeport (facultatif)',
              hint: 'Saisissez le numéro de passeport',
              controller: _passport,
              error: _errors['passportNumber'],
              capitalization: TextCapitalization.characters,
              onChanged: (_) => _changed(),
            ),
          ],
        ),
        const SizedBox(height: 20),
        if (_globalError != null) ...[
          AzBanner(kind: AzBannerKind.error, text: _globalError!),
          const SizedBox(height: 12),
        ],
        if (saved == null && sim == null)
          AzButton('Calculer mon devis', loading: _busy, onPressed: _simulate)
        else ...[
          VoyageSummary(simulation: sim, quote: saved),
          const SizedBox(height: 16),
          MotoQuoteResult(
            simulation: saved?.simulation ?? sim!.toQuoteSimulation(departure: out, back: back),
            quote: saved,
            busy: _busy,
            onSave: _save,
            onAccept: _accept,
            onPay: saved == null ? null : () => payQuote(context, saved),
          ),
        ],
        const SizedBox(height: 12),
        AzButton.secondary('Être rappelé par un conseiller', onPressed: () => showAdvisorSheet(context)),
      ],
    );
  }
}

/// Récapitulatif du voyage : celui de la simulation, puis celui du devis enregistré (valeurs du serveur).
class VoyageSummary extends StatelessWidget {
  const VoyageSummary({super.key, this.simulation, this.quote});

  final VoyageSimulation? simulation;
  final MotoQuote? quote;

  @override
  Widget build(BuildContext context) {
    final v = quote?.voyage;
    final s = simulation;
    return AzSummaryCard(rows: voyageRows(v, s));
  }
}

/// Lignes du récapitulatif. Une valeur absente n'est pas affichée ; la durée en mois n'apparaît jamais.
List<(String, String)> voyageRows(VoyageInfo? v, VoyageSimulation? s) => [
      if (v != null) ...[
        ('Destination', v.countryName),
        if (v.spaceLabel != null) ('Zone', v.spaceLabel!),
        if (v.travelerAge != null) ('Âge du voyageur', '${v.travelerAge} ${v.travelerAge! > 1 ? 'ans' : 'an'}'),
        if (v.departureDate != null) ('Départ', formatDate(v.departureDate)),
        if (v.returnDate != null) ('Retour', formatDate(v.returnDate)),
        if (v.durationLabel != null) ('Durée', v.durationLabel!),
        if (v.passportNumber != null) ('Passeport', v.passportNumber!),
      ] else if (s != null) ...[
        if (s.countryName.isNotEmpty) ('Destination', s.countryName),
        if (s.spaceLabel.isNotEmpty) ('Zone', s.spaceLabel),
        ('Âge du voyageur', '${s.age} ${s.age > 1 ? 'ans' : 'an'}'),
        ('Durée', '${s.durationDays} ${s.durationDays > 1 ? 'jours' : 'jour'}'),
        if (s.durationBand != null) ('Tarif', s.durationBand!),
      ],
    ];

/// Liste des pays regroupés par espace, avec recherche (sans tenir compte des accents ni des majuscules).
class VoyageCountryPicker extends StatefulWidget {
  const VoyageCountryPicker({super.key, required this.form, this.selected});

  final VoyageForm form;
  final VoyageCountry? selected;

  @override
  State<VoyageCountryPicker> createState() => _VoyageCountryPickerState();
}

class _VoyageCountryPickerState extends State<VoyageCountryPicker> {
  String _query = '';

  static String _fold(String s) {
    const from = 'àâäáãåçéèêëíìîïñóòôöõúùûüýÿœ';
    const to = 'aaaaaaceeeeiiiinooooouuuuyyo';
    final lower = s.toLowerCase();
    final b = StringBuffer();
    for (final r in lower.runes) {
      final ch = String.fromCharCode(r);
      final i = from.indexOf(ch);
      b.write(i >= 0 ? to[i] : ch);
    }
    return b.toString();
  }

  @override
  Widget build(BuildContext context) {
    final q = _fold(_query.trim());
    final groups = <String, List<VoyageCountry>>{};
    widget.form.bySpace.forEach((space, list) {
      final shown = q.isEmpty ? list : [for (final c in list) if (_fold(c.name).contains(q)) c];
      if (shown.isNotEmpty) groups[space] = shown;
    });
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (widget.form.countries.length > 8) ...[
          TextField(
            decoration: const InputDecoration(hintText: 'Rechercher un pays', prefixIcon: Icon(Icons.search_rounded)),
            onChanged: (v) => setState(() => _query = v),
          ),
          const SizedBox(height: 12),
        ],
        if (groups.isEmpty)
          Padding(padding: const EdgeInsets.symmetric(vertical: 16), child: Text('Aucun pays trouvé.', style: AzText.bodyMedium.copyWith(color: AzColors.muted)))
        else
          for (final entry in groups.entries) ...[
            Padding(padding: const EdgeInsets.only(top: 8, bottom: 6), child: Text(entry.key.toUpperCase(), style: AzText.overline)),
            for (final c in entry.value) ...[
              AzOptionTile(title: c.name, selected: widget.selected?.id == c.id, onTap: () => Navigator.of(context).pop(c)),
              const SizedBox(height: 8),
            ],
          ],
      ],
    );
  }
}
