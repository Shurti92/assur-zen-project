import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/network/api_exception.dart';
import '../../core/ui.dart';
import '../../data/models/enterprise_quote.dart';
import '../../data/models/models.dart';
import '../auth/register_page.dart';
import '../shell/main_shell.dart';

class EnterpriseRequestArgs {
  const EnterpriseRequestArgs({required this.product, required this.simulation});

  final Product product;
  final EnterpriseSimulation simulation;
}

/// Demande de souscription Assurance Entreprise, en trois étapes : l'entreprise, le représentant, la
/// vérification. La formule et l'option viennent de la cotation ; le prix n'est jamais envoyé, le serveur
/// le retrouve. Le compte n'est demandé qu'à l'envoi. « Demande envoyée » n'apparaît qu'avec la
/// référence renvoyée par le serveur ; sinon la saisie est conservée et l'erreur est expliquée.
class EnterpriseRequestPage extends StatefulWidget {
  const EnterpriseRequestPage({super.key, required this.args});

  final EnterpriseRequestArgs args;

  @override
  State<EnterpriseRequestPage> createState() => _EnterpriseRequestPageState();
}

/// Étape (0 entreprise, 1 représentant) de chaque champ renvoyé en erreur par le serveur.
const _fieldStep = {
  'activityDomain': 0,
  'companyName': 0,
  'rccmNumber': 0,
  'location': 0,
  'representativeName': 1,
  'representativeRole': 1,
  'representativePhone': 1,
  'representativeEmail': 1,
};

final _rccmPattern = RegExp(r'^[A-Za-z0-9 ./_-]{4,40}$');
final _phonePattern = RegExp(r'^(01|05|07)\d{8}$');
final _emailPattern = RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$');

class _EnterpriseRequestPageState extends State<EnterpriseRequestPage> {
  final _domain = TextEditingController();
  final _company = TextEditingController();
  final _rccm = TextEditingController();
  final _location = TextEditingController();
  final _name = TextEditingController();
  final _role = TextEditingController();
  final _phone = TextEditingController();
  final _email = TextEditingController();

  late final Future<List<EnterpriseActivityDomain>> _domains = context.repos.enterpriseQuotes.getActivityDomains();
  final Map<String, String> _errors = {};
  int _step = 0;
  bool _busy = false;
  String? _globalError;

  /// Le serveur a déjà la demande (réponse perdue au premier envoi, ou envoi répété) : le client est
  /// rassuré au lieu de voir une erreur. Aucune référence n'est inventée.
  bool _alreadyPending = false;
  EnterpriseRequestReceipt? _receipt;
  bool _prefilled = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_prefilled) return;
    _prefilled = true;
    // Le téléphone et l'e-mail du compte facilitent la saisie ; le client peut les changer.
    final user = context.session.user;
    if (user != null) {
      _phone.text = user.phone;
      final mail = user.email;
      if (mail != null && _emailPattern.hasMatch(mail)) _email.text = mail;
    }
  }

  @override
  void dispose() {
    for (final c in [_domain, _company, _rccm, _location, _name, _role, _phone, _email]) {
      c.dispose();
    }
    super.dispose();
  }

  void _changed() {
    if (_errors.isEmpty && _globalError == null && !_alreadyPending) return;
    setState(() {
      _errors.clear();
      _globalError = null;
      _alreadyPending = false;
    });
  }

  bool _validate(int step) {
    final e = <String, String>{};
    String t(TextEditingController c) => c.text.trim();
    if (step == 0) {
      if (t(_domain).isEmpty) e['activityDomain'] = 'Indiquez le domaine d’activité.';
      if (t(_company).isEmpty) e['companyName'] = 'Indiquez le nom de l’entreprise.';
      if (!_rccmPattern.hasMatch(t(_rccm))) e['rccmNumber'] = 'Indiquez le numéro RCCM (4 à 40 caractères).';
      if (t(_location).isEmpty) e['location'] = 'Indiquez la localisation.';
    } else if (step == 1) {
      if (t(_name).isEmpty) e['representativeName'] = 'Indiquez le nom du représentant.';
      if (t(_role).isEmpty) e['representativeRole'] = 'Indiquez sa fonction.';
      if (!_phonePattern.hasMatch(phoneDigits(_phone.text))) e['representativePhone'] = 'Indiquez un numéro à 10 chiffres (01, 05 ou 07).';
      if (!_emailPattern.hasMatch(t(_email))) e['representativeEmail'] = 'Indiquez une adresse e-mail valide.';
    }
    setState(() {
      _errors
        ..clear()
        ..addAll(e);
      _globalError = null;
    });
    return e.isEmpty;
  }

  void _next() {
    if (_validate(_step)) setState(() => _step++);
  }

  void _back() {
    setState(() {
      _errors.clear();
      _globalError = null;
      _alreadyPending = false;
      _step--;
    });
  }

  EnterpriseRequestInput _input() {
    final s = widget.args.simulation;
    return EnterpriseRequestInput(
      formulaId: s.formulaId,
      option: s.option,
      activityDomain: _domain.text.trim(),
      companyName: _company.text.trim(),
      rccmNumber: _rccm.text.trim(),
      location: _location.text.trim(),
      representativeName: _name.text.trim(),
      representativeRole: _role.text.trim(),
      representativePhone: phoneDigits(_phone.text),
      representativeEmail: _email.text.trim(),
    );
  }

  Future<void> _send() async {
    if (!_validate(0)) {
      setState(() => _step = 0);
      return;
    }
    if (!_validate(1)) {
      setState(() => _step = 1);
      return;
    }
    if (!context.session.isLoggedIn) {
      // Le compte n'est demandé qu'à l'envoi : la cotation reste publique.
      final ok = await context.push<bool>(AppRoutes.register, const RegisterArgs(returnToCaller: true));
      if (ok != true || !mounted) return;
    }
    setState(() {
      _busy = true;
      _globalError = null;
      _alreadyPending = false;
    });
    try {
      final receipt = await context.repos.enterpriseQuotes.submitRequest(_input());
      if (mounted) setState(() => _receipt = receipt);
    } catch (e) {
      if (!mounted) return;
      if (e is ApiException && (e.code == 'REQUEST_ALREADY_PENDING' || e.statusCode == 409)) {
        setState(() => _alreadyPending = true);
      } else if (e is ApiException && e.field != null && _fieldStep.containsKey(e.field)) {
        setState(() {
          _errors[e.field!] = e.message;
          _step = _fieldStep[e.field!]!;
        });
      } else {
        setState(() => _globalError = errorMessage(e));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final sent = _receipt != null;
    return PopScope(
      canPop: !sent && _step == 0,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        if (sent) {
          context.resetTo(AppRoutes.shell, ShellTab.home);
        } else {
          _back();
        }
      },
      child: Scaffold(
        backgroundColor: AzColors.background,
        appBar: sent
            ? AzTopBar(title: 'Demande envoyée', showBack: false, actions: [
                IconButton(tooltip: 'Fermer', icon: const Icon(Icons.close_rounded), onPressed: () => context.resetTo(AppRoutes.shell, ShellTab.home)),
              ])
            : AzTopBar(title: 'Étape ${_step + 1} sur 3', onBack: _step == 0 ? null : _back),
        body: SafeArea(
          child: AzContentWidth(child: sent ? _sentView(_receipt!) : _form()),
        ),
      ),
    );
  }

  Widget _stepBar() => Row(
        children: [
          for (var i = 0; i < 3; i++) ...[
            if (i > 0) const SizedBox(width: 4),
            Expanded(child: Container(height: 5, decoration: BoxDecoration(color: i <= _step ? AzColors.blue700 : AzColors.line, borderRadius: BorderRadius.circular(4)))),
          ],
        ],
      );

  Widget _form() {
    return ListView(
      padding: EdgeInsets.fromLTRB(AzSpace.screen, 4, AzSpace.screen, 24 + MediaQuery.viewPaddingOf(context).bottom),
      children: [
        _stepBar(),
        const SizedBox(height: 14),
        if (_alreadyPending) ...[
          const AzBanner(
            kind: AzBannerKind.success,
            title: 'Votre demande est déjà enregistrée.',
            text: 'Elle est en cours de traitement : il est inutile de la renvoyer.',
          ),
          const SizedBox(height: 12),
        ],
        if (_globalError != null) ...[
          AzBanner(kind: AzBannerKind.error, text: _globalError!),
          const SizedBox(height: 12),
        ],
        if (_step == 0) ..._stepCompany() else if (_step == 1) ..._stepRepresentative() else ..._stepReview(),
      ],
    );
  }

  List<Widget> _stepCompany() => [
        const Text('Votre entreprise', style: AzText.title),
        const SizedBox(height: 14),
        AzGap(gap: 14, children: [
          FutureBuilder<List<EnterpriseActivityDomain>>(
            future: _domains,
            builder: (context, snap) {
              final labels = [for (final d in snap.data ?? const <EnterpriseActivityDomain>[]) d.label];
              // Liste du serveur quand il en donne une ; sinon (ou en attendant) le client saisit librement.
              return AzAutocompleteField(
                label: 'Domaine d’activité',
                hint: labels.isEmpty ? 'Ex. : transport, commerce, artisanat' : 'Tapez pour chercher un domaine',
                controller: _domain,
                options: labels,
                error: _errors['activityDomain'],
                onChanged: (_) => _changed(),
              );
            },
          ),
          AzTextField(
            label: 'Nom de l’entreprise',
            controller: _company,
            error: _errors['companyName'],
            maxLength: 150,
            capitalization: TextCapitalization.words,
            onChanged: (_) => _changed(),
          ),
          AzTextField(
            label: 'N° RCCM',
            hint: 'Ex. : CI-ABJ-2020-B-12345',
            controller: _rccm,
            error: _errors['rccmNumber'],
            maxLength: 40,
            capitalization: TextCapitalization.characters,
            onChanged: (_) => _changed(),
          ),
          AzTextField(
            label: 'Localisation',
            hint: 'Commune, quartier',
            controller: _location,
            error: _errors['location'],
            maxLength: 200,
            capitalization: TextCapitalization.words,
            onChanged: (_) => _changed(),
          ),
        ]),
        const SizedBox(height: 20),
        AzButton('Continuer', onPressed: _next),
      ];

  List<Widget> _stepRepresentative() => [
        const Text('Votre représentant', style: AzText.title),
        const SizedBox(height: 14),
        AzGap(gap: 14, children: [
          AzTextField(
            label: 'Nom du représentant',
            controller: _name,
            error: _errors['representativeName'],
            maxLength: 120,
            capitalization: TextCapitalization.words,
            onChanged: (_) => _changed(),
          ),
          AzTextField(
            label: 'Fonction',
            hint: 'Ex. : Directrice générale',
            controller: _role,
            error: _errors['representativeRole'],
            maxLength: 100,
            capitalization: TextCapitalization.sentences,
            onChanged: (_) => _changed(),
          ),
          AzTextField(
            label: 'Téléphone',
            controller: _phone,
            error: _errors['representativePhone'],
            keyboardType: TextInputType.phone,
            inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9 ]')), LengthLimitingTextInputFormatter(14)],
            onChanged: (_) => _changed(),
          ),
          AzTextField(
            label: 'E-mail',
            hint: 'nom@entreprise.ci',
            controller: _email,
            error: _errors['representativeEmail'],
            keyboardType: TextInputType.emailAddress,
            maxLength: 150,
            onChanged: (_) => _changed(),
          ),
        ]),
        const SizedBox(height: 20),
        AzButton('Continuer', onPressed: _next),
      ];

  List<Widget> _stepReview() {
    final s = widget.args.simulation;
    String t(TextEditingController c) => c.text.trim();
    return [
      const Text('Vérifiez votre demande', style: AzText.title),
      const SizedBox(height: 14),
      AzSummaryCard(radius: 26, rows: [
        ('Domaine', t(_domain)),
        ('Entreprise', t(_company)),
        ('RCCM', t(_rccm).toUpperCase()),
        ('Localisation', t(_location)),
        ('Représentant', '${t(_name)}, ${t(_role)}'),
        ('Téléphone', formatPhone(_phone.text)),
        ('E-mail', t(_email)),
        ('Produit', widget.args.product.title),
        ('Formule', s.formulaName),
        ('Option', s.optionLabel),
      ]),
      const SizedBox(height: 20),
      AzButton('Envoyer ma demande', variant: AzButtonVariant.accent, loading: _busy, onPressed: _send),
      const SizedBox(height: 10),
      AzButton.secondary('Modifier', onPressed: _busy ? null : _back),
    ];
  }

  Widget _sentView(EnterpriseRequestReceipt r) {
    final s = widget.args.simulation;
    final total = r.total ?? s.total;
    return ListView(
      padding: EdgeInsets.fromLTRB(AzSpace.screen, 24, AzSpace.screen, 24 + MediaQuery.viewPaddingOf(context).bottom),
      children: [
        Column(
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: const BoxDecoration(color: AzColors.successBg, shape: BoxShape.circle),
              child: const Icon(Icons.check_rounded, size: 40, color: AzColors.success),
            ),
            const SizedBox(height: 14),
            // Texte de confirmation réglé au back-office, affiché tel quel.
            if (r.message != null) Text(r.message!, textAlign: TextAlign.center, style: AzText.title),
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
              decoration: BoxDecoration(color: AzColors.blue50, borderRadius: BorderRadius.circular(AzRadius.pill)),
              child: Text('Référence ${r.reference}', style: AzText.label.copyWith(color: AzColors.blue700, fontWeight: FontWeight.w800)),
            ),
          ],
        ),
        const SizedBox(height: 20),
        AzSummaryCard(radius: 26, rows: [
          ('Entreprise', _company.text.trim()),
          ('RCCM', _rccm.text.trim().toUpperCase()),
          ('Produit', widget.args.product.title),
          ('Formule', r.formulaName ?? s.formulaName),
          ('Option', r.optionLabel ?? s.optionLabel),
          ('Prime annuelle TTC', formatFcfa(total)),
          ('Contact', formatPhone(_phone.text)),
        ]),
        const SizedBox(height: 24),
        AzButton('Retour à l’accueil', onPressed: () => context.resetTo(AppRoutes.shell, ShellTab.home)),
      ],
    );
  }
}
