import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';

import '../../app/app_scope.dart';
import '../../app/env.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';
import '../auth/register_page.dart';
import '../catalog/advisor_sheet.dart';
import '../payment/payment_page.dart';

class QuoteArgs {
  const QuoteArgs({required this.productId, this.draft});

  final String productId;

  /// Brouillon à reprendre (décision 2.5).
  final QuoteDraft? draft;
}

/// Parcours de devis et de souscription.
///
/// 1 Véhicule · 2 Couverture · 3 Coordonnées · 4 Récapitulatif
/// → 5 Devis calculé par le serveur · 6 Pièces justificatives → Paiement.
/// Les informations sont conservées au retour et sauvegardées 30 jours.
class QuoteFlowPage extends StatefulWidget {
  const QuoteFlowPage({super.key, required this.args});

  final QuoteArgs args;

  @override
  State<QuoteFlowPage> createState() => _QuoteFlowPageState();
}

class _QuoteFlowPageState extends State<QuoteFlowPage> {
  late final QuoteDraft _d = widget.args.draft ?? QuoteDraft(productId: widget.args.productId);
  late final Future<(Product, QuoteReferentials)> _setup = _loadSetup();

  late final _plate = TextEditingController(text: _d.plate);
  late final _model = TextEditingController(text: _d.model);
  late final _seats = TextEditingController(text: '${_d.seats}');
  late final _name = TextEditingController(text: _d.holderName);
  late final _phone = TextEditingController(text: formatPhone(_d.holderPhone));
  late final _email = TextEditingController(text: _d.holderEmail);
  late final _promo = TextEditingController(text: _d.promoCode);

  Quote? _quote;
  Object? _quoteError;
  bool _computing = false;
  bool _uploading = false;
  final Map<String, String?> _errors = {};
  final _picker = ImagePicker();

  static const _titles = {
    1: 'Votre véhicule',
    2: 'Votre couverture',
    3: 'Vos coordonnées',
    4: 'Récapitulatif',
    5: 'Votre devis',
    6: 'Pièces justificatives',
  };

  @override
  void initState() {
    super.initState();
    final user = context.session.user;
    if (_d.holderName.isEmpty && user != null) _name.text = user.fullName;
    if (_d.holderPhone.isEmpty && user != null) _phone.text = formatPhone(user.phone);
    if (_d.holderEmail.isEmpty && user?.email != null) _email.text = user!.email!;
    if (_d.step > 4) _d.step = 4; // un devis repris est recalculé
  }

  Future<(Product, QuoteReferentials)> _loadSetup() async {
    final repos = context.repos;
    final product = await repos.catalog.getProduct(_d.productId);
    final ref = await repos.catalog.getReferentials(_d.productId);
    _d.power ??= ref.powers.length > 1 ? ref.powers[1] : ref.powers.first;
    _d.usage ??= ref.usages.first;
    if (ref.platforms.isNotEmpty) _d.platform ??= ref.platforms.first;
    _d.formulaId ??= ref.formulas.first.id;
    _d.startDate ??= DateTime.now().add(const Duration(days: 1));
    return (product, ref);
  }

  @override
  void dispose() {
    for (final c in [_plate, _model, _seats, _name, _phone, _email, _promo]) {
      c.dispose();
    }
    super.dispose();
  }

  void _syncText() {
    _d
      ..plate = _plate.text.trim().toUpperCase()
      ..model = _model.text.trim()
      ..seats = int.tryParse(_seats.text) ?? 5
      ..holderName = _name.text.trim()
      ..holderPhone = phoneDigits(_phone.text)
      ..holderEmail = _email.text.trim()
      ..promoCode = _promo.text.trim().toUpperCase();
  }

  bool _validate(int step) {
    _errors.clear();
    switch (step) {
      case 1:
        _errors['plate'] = Validators.plate(_plate.text);
        _errors['model'] = Validators.minLength(_model.text, 2, 'Indiquez la marque et le modèle.');
        if (_d.firstCirculation == null) _errors['circ'] = 'Indiquez la date de 1re mise en circulation.';
        final seats = int.tryParse(_seats.text);
        if (seats == null || seats < 1 || seats > 60) _errors['seats'] = 'Nombre de places invalide.';
      case 3:
        _errors['name'] = Validators.fullName(_name.text);
        _errors['phone'] = Validators.phone(_phone.text);
        _errors['email'] = Validators.optionalEmail(_email.text);
      case 4:
        if (!_d.certified) _errors['certify'] = 'Cochez la case pour certifier vos informations.';
      case 6:
        final missing = RequiredDocument.values.where((r) => !_d.documents.containsKey(r));
        if (missing.isNotEmpty) _errors['docs'] = 'Ajoutez : ${missing.map((m) => m.label.toLowerCase()).join(', ')}.';
    }
    setState(() {});
    return _errors.values.every((e) => e == null);
  }

  Future<void> _next(Product product) async {
    _syncText();
    final step = _d.step;
    if (!_validate(step)) return;
    if (step <= 3) {
      setState(() => _d.step = step + 1);
      context.store.saveDraft(_d);
      return;
    }
    if (step == 4) {
      setState(() => _d.step = 5);
      context.store.saveDraft(_d);
      await _compute();
      return;
    }
    if (step == 5) {
      final session = context.session;
      if (!session.isLoggedIn) {
        // Décision 4.6 : le compte n’est demandé qu’au moment de souscrire.
        final ok = await context.push<bool>(AppRoutes.register, const RegisterArgs(returnToCaller: true));
        if (ok != true || !mounted) return;
      }
      setState(() => _d.step = 6);
      return;
    }
    if (step == 6) await _uploadAndPay(product);
  }

  Future<void> _compute() async {
    setState(() {
      _computing = true;
      _quoteError = null;
    });
    try {
      final q = await context.repos.quotes.computeQuote(_d);
      if (mounted) setState(() => _quote = q);
    } catch (e) {
      if (mounted) setState(() => _quoteError = e);
    } finally {
      if (mounted) setState(() => _computing = false);
    }
  }

  Future<void> _uploadAndPay(Product product) async {
    final q = _quote!;
    setState(() => _uploading = true);
    try {
      for (final e in _d.documents.entries) {
        await context.repos.quotes.uploadDocument(quoteNumber: q.number, type: e.key, filePath: e.value);
        if (!mounted) return;
      }
    } catch (e) {
      if (mounted) showAzSnack(context, errorMessage(e));
      return;
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
    if (!mounted) return;
    // Paiement en plusieurs fois : retiré, le serveur n'expose aucune échéance payable.
    await context.push(
      AppRoutes.payment,
      PaymentArgs(
        payableReference: q.number,
        amount: q.total,
        caption: 'Prime totale · ${_durationLabel(_d.durationMonths)}',
        objectLabel: product.title,
        clearDraftOnSuccess: true,
      ),
    );
  }

  void _back() {
    if (_computing || _uploading) return;
    if (_d.step > 1) {
      _syncText();
      setState(() {
        _d.step -= 1;
        _errors.clear();
      });
    } else {
      _exit();
    }
  }

  void _exit() {
    _syncText();
    final hasData = _d.plate.isNotEmpty || _d.model.isNotEmpty || _d.step > 1;
    if (hasData) {
      context.store.saveDraft(_d);
      showAzSnack(context, 'Devis enregistré 30 jours : reprenez-le depuis l’accueil.');
    }
    Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _back();
      },
      child: Scaffold(
        backgroundColor: Colors.white,
        body: FutureBuilder<(Product, QuoteReferentials)>(
          future: _setup,
          builder: (context, snap) {
            if (snap.hasError) {
              return SafeArea(
                child: AzErrorState(message: errorMessage(snap.error!), onRetry: () => Navigator.of(context).pop()),
              );
            }
            if (!snap.hasData) return const AzLoader();
            final (product, ref) = snap.data!;
            return _body(product, ref);
          },
        ),
      ),
    );
  }

  Widget _body(Product product, QuoteReferentials ref) {
    final step = _d.step;
    final showNext = !(step == 5 && (_computing || _quoteError != null));
    return Column(
      children: [
        AzTopBar(
          overline: product.title,
          title: _titles[step],
          onBack: _back,
          actions: [
            IconButton(
              tooltip: 'Fermer',
              onPressed: _computing || _uploading ? null : _exit,
              icon: const Icon(Icons.close_rounded, color: AzColors.muted),
            ),
          ],
        ),
        if (step <= 4)
          Padding(
            padding: const EdgeInsets.fromLTRB(AzSpace.screen, 0, AzSpace.screen, 12),
            child: AzProgressHeader(step: step, total: 4, label: _titles[step]),
          ),
        Expanded(
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 200),
            child: ListView(
              key: ValueKey(step),
              padding: const EdgeInsets.fromLTRB(AzSpace.screen, 8, AzSpace.screen, 24),
              children: [
                switch (step) {
                  1 => _vehicleStep(ref),
                  2 => _coverStep(ref),
                  3 => _holderStep(),
                  4 => _summaryStep(product, ref),
                  5 => _quoteStep(product),
                  _ => _documentsStep(),
                },
              ],
            ),
          ),
        ),
        AzBottomBar(children: [
          if (showNext)
            AzButton(
              switch (step) {
                4 => 'Calculer mon devis',
                5 => context.session.isLoggedIn ? 'Souscrire' : 'Créer mon compte et souscrire',
                6 => 'Continuer vers le paiement',
                _ => 'Continuer',
              },
              loading: _uploading,
              onPressed: () => _next(product),
            ),
          if (step <= 5)
            AzButton.ghost(
              'Je préfère être accompagné par un conseiller',
              height: 40,
              onPressed: () => showAdvisorSheet(context, product: product),
            ),
        ]),
      ],
    );
  }

  // --- Étape 1 -------------------------------------------------------------
  Widget _vehicleStep(QuoteReferentials ref) {
    return AzGap(
      gap: 18,
      children: [
        _Labeled(
          ref.powerLabel,
          AzChipFill<String>(options: ref.powers, selected: _d.power, minChipWidth: 110, onSelected: (v) => setState(() => _d.power = v)),
        ),
        _Labeled(
          'Usage du véhicule',
          AzChipFill<String>(options: ref.usages, selected: _d.usage, minChipWidth: 120, onSelected: (v) => setState(() => _d.usage = v)),
        ),
        if (ref.platforms.isNotEmpty)
          _Labeled(
            'Plateforme',
            AzChipFill<String>(options: ref.platforms, selected: _d.platform, minChipWidth: 100, onSelected: (v) => setState(() => _d.platform = v)),
          ),
        AzTextField(
          label: 'Immatriculation',
          controller: _plate,
          hint: 'Ex. 1234 AB 01',
          error: _errors['plate'],
          capitalization: TextCapitalization.characters,
        ),
        AzTextField(label: 'Marque et modèle', controller: _model, hint: 'Ex. Toyota Corolla', error: _errors['model'], capitalization: TextCapitalization.words),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              flex: 3,
              child: AzDateField(
                label: '1re mise en circulation',
                value: _d.firstCirculation,
                error: _errors['circ'],
                firstDate: DateTime(1980),
                lastDate: DateTime.now(),
                onChanged: (v) => setState(() {
                  _d.firstCirculation = v;
                  _errors['circ'] = null;
                }),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              flex: 2,
              child: AzTextField(
                label: 'Nombre de places',
                controller: _seats,
                hint: 'Ex. : 5',
                error: _errors['seats'],
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                maxLength: 2,
              ),
            ),
          ],
        ),
        Text('Bientôt : photographiez votre carte grise pour remplir ces champs automatiquement.',
            style: AzText.caption.copyWith(fontWeight: FontWeight.w500)),
      ],
    );
  }

  // --- Étape 2 -------------------------------------------------------------
  Widget _coverStep(QuoteReferentials ref) {
    return AzGap(
      gap: 18,
      children: [
        _Labeled(
          'Formule',
          AzGap(
            gap: 10,
            children: [
              for (final f in ref.formulas)
                AzOptionTile(
                  title: f.name,
                  subtitle: f.description,
                  selected: _d.formulaId == f.id,
                  onTap: () => setState(() => _d.formulaId = f.id),
                ),
              Text('Contenu des formules : exemples, à confirmer par AssurZen.', style: AzText.caption.copyWith(color: AzColors.warning)),
            ],
          ),
        ),
        _Labeled(
          'Durée de couverture',
          AzChipFill<int>(
            options: ref.durations,
            minChipWidth: 76,
            maxColumns: 4,
            selected: _d.durationMonths,
            labelOf: _durationLabel,
            onSelected: (v) => setState(() => _d.durationMonths = v),
          ),
        ),
        AzDateField(
          label: 'Date de prise d’effet souhaitée',
          value: _d.startDate,
          firstDate: DateTime.now(),
          lastDate: DateTime.now().add(const Duration(days: 90)),
          onChanged: (v) => setState(() => _d.startDate = v),
        ),
      ],
    );
  }

  // --- Étape 3 -------------------------------------------------------------
  Widget _holderStep() {
    return AzGap(
      gap: 18,
      children: [
        AzTextField(label: 'Nom et prénoms du souscripteur', controller: _name, hint: 'Ex. Koné Awa', error: _errors['name'], capitalization: TextCapitalization.words),
        AzPhoneField(controller: _phone, label: 'Téléphone', error: _errors['phone']),
        AzTextField(
          label: 'E-mail (facultatif)',
          controller: _email,
          hint: 'nom@exemple.ci',
          error: _errors['email'],
          keyboardType: TextInputType.emailAddress,
        ),
        const AzBanner(kind: AzBannerKind.info, text: 'Ces informations servent uniquement à établir votre devis et votre contrat.'),
      ],
    );
  }

  // --- Étape 4 -------------------------------------------------------------
  Widget _summaryStep(Product product, QuoteReferentials ref) {
    final formula = ref.formulas.firstWhere((f) => f.id == _d.formulaId, orElse: () => ref.formulas.first);
    return AzGap(
      gap: 16,
      children: [
        AzSummaryCard(rows: [
          ('Produit', product.title),
          (ref.powerLabel, _d.power ?? '—'),
          ('Usage', _d.usage ?? '—'),
          if (_d.platform != null && ref.platforms.isNotEmpty) ('Plateforme', _d.platform!),
          ('Véhicule', '${_d.model.isEmpty ? 'Véhicule' : _d.model} · ${_d.plate}'),
          ('Mise en circulation', formatDate(_d.firstCirculation)),
          ('Formule', formula.name),
          ('Durée', _durationLabel(_d.durationMonths)),
          ('Prise d’effet', formatDate(_d.startDate)),
          ('Souscripteur', _d.holderName),
        ]),
        Wrap(
          spacing: 12,
          children: [
            AzTextLink('Modifier le véhicule', onPressed: () => setState(() => _d.step = 1)),
            AzTextLink('Modifier la formule', onPressed: () => setState(() => _d.step = 2)),
          ],
        ),
        AzTextField(
          label: 'Code promo ou parrainage (facultatif)',
          controller: _promo,
          hint: 'Ex. CHAUFFEUR25',
          capitalization: TextCapitalization.characters,
        ),
        AzCheckRow(
          value: _d.certified,
          onChanged: (v) => setState(() {
            _d.certified = v;
            _errors['certify'] = null;
          }),
          child: const Text('Je certifie l’exactitude des informations déclarées.'),
        ),
        if (_errors['certify'] != null) AzBanner(kind: AzBannerKind.error, text: _errors['certify']!),
      ],
    );
  }

  // --- Étape 5 -------------------------------------------------------------
  Widget _quoteStep(Product product) {
    if (_computing) {
      return const AzGap(
        gap: 14,
        children: [
          SizedBox(height: 32),
          AzLoader(message: 'Calcul de votre devis…'),
          Text('Le tarif est calculé par les serveurs AssurZen.', textAlign: TextAlign.center, style: AzText.bodyMedium),
          SizedBox(height: 8),
          AzSkeleton(height: 110, radius: 18),
          AzSkeleton(width: 220),
          AzSkeleton(width: 160),
        ],
      );
    }
    if (_quoteError != null || _quote == null) {
      return AzGap(
        children: [
          const SizedBox(height: 24),
          AzBanner(
            kind: AzBannerKind.error,
            text: _quoteError == null ? 'Le tarif n’a pas pu être calculé.' : errorMessage(_quoteError!),
          ),
          AzButton('Réessayer', icon: Icons.refresh_rounded, onPressed: _compute),
          AzButton.secondary('Être rappelé par un conseiller', onPressed: () => showAdvisorSheet(context, product: product)),
        ],
      );
    }
    final q = _quote!;
    return AzGap(
      gap: 16,
      children: [
        Container(
          padding: const EdgeInsets.all(22),
          decoration: BoxDecoration(color: AzColors.blue700, borderRadius: BorderRadius.circular(AzRadius.hero)),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (q.isDemo) ...[
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(color: AzColors.orange50, borderRadius: BorderRadius.circular(AzRadius.pill)),
                  child: Text('Montant de démonstration', style: AzText.caption.copyWith(color: AzColors.warningInk, fontWeight: FontWeight.w700)),
                ),
                const SizedBox(height: 10),
              ],
              Text('Total à payer · ${_durationLabel(_d.durationMonths)}', style: AzText.label.copyWith(color: AzColors.blue100)),
              const SizedBox(height: 4),
              Text(formatFcfa(q.total), style: AzText.amount.copyWith(color: Colors.white, fontSize: 34)),
              const SizedBox(height: 4),
              Text('Devis n° ${q.number} · valable jusqu’au ${formatDate(q.validUntil)}',
                  style: AzText.caption.copyWith(color: AzColors.blue50, fontWeight: FontWeight.w500, fontSize: 13)),
            ],
          ),
        ),
        AzSummaryCard(rows: [
          ('Prime nette', formatFcfa(q.netPremium)),
          ('Accessoires', formatFcfa(q.fees)),
          ('Taxes', formatFcfa(q.taxes)),
        ]),
        _Labeled(
          'Garanties incluses',
          AzGap(
            gap: 8,
            children: [
              for (final g in q.guarantees)
                Row(children: [
                  const Icon(Icons.check_rounded, size: 20, color: AzColors.success),
                  const SizedBox(width: 10),
                  Expanded(child: Text(g, style: AzText.bodyMedium.copyWith(color: AzColors.ink))),
                ]),
              if (q.isDemo) Text('Liste d’exemple : les garanties réelles sont renvoyées par l’API de devis.', style: AzText.caption.copyWith(color: AzColors.warning)),
            ],
          ),
        ),
        Align(
          alignment: Alignment.centerLeft,
          child: AzTextLink(
            'Envoyer le devis par WhatsApp',
            onPressed: () => Launch.shareWhatsapp(
              context,
              'Mon devis AssurZen ${q.number} : ${formatFcfa(q.total)} (${_durationLabel(_d.durationMonths)}), valable jusqu’au ${formatDate(q.validUntil)}.',
            ),
          ),
        ),
      ],
    );
  }

  // --- Étape 6 -------------------------------------------------------------
  Widget _documentsStep() {
    return AzGap(
      gap: 12,
      children: [
        Text('Ajoutez une photo lisible de chaque pièce. Elles sont envoyées de façon sécurisée et ne restent pas sur votre téléphone.',
            style: AzText.body.copyWith(fontSize: 15, color: AzColors.ink2)),
        for (final r in RequiredDocument.values)
          _DocumentTile(
            label: r.label,
            added: _d.documents.containsKey(r),
            onAdd: () => _pickDocument(r),
            onRemove: () => setState(() => _d.documents.remove(r)),
          ),
        if (_errors['docs'] != null) AzBanner(kind: AzBannerKind.error, text: _errors['docs']!),
        Text('Délai de vérification annoncé : 2 h (à confirmer par AssurZen).', style: AzText.caption.copyWith(fontWeight: FontWeight.w500)),
      ],
    );
  }

  Future<void> _pickDocument(RequiredDocument type) async {
    final source = await showAzSheet<ImageSource>(
      context,
      title: type.label,
      builder: (context) => AzGap(
        gap: 10,
        children: [
          AzButton('Prendre une photo', icon: Icons.photo_camera_outlined, onPressed: () => Navigator.of(context).pop(ImageSource.camera)),
          AzButton.secondary('Choisir dans la galerie', icon: Icons.photo_library_outlined, onPressed: () => Navigator.of(context).pop(ImageSource.gallery)),
        ],
      ),
    );
    if (source == null) return;
    try {
      // Photos compressées (≈ 1 Mo) pour économiser les données mobiles.
      final file = await _picker.pickImage(source: source, imageQuality: 70, maxWidth: 1600);
      if (file == null || !mounted) return;
      setState(() {
        _d.documents[type] = file.path;
        _errors['docs'] = null;
      });
    } catch (_) {
      if (mounted) showAzSnack(context, 'Impossible d’accéder à l’appareil photo. Vérifiez les autorisations.');
    }
  }
}

String _durationLabel(int months) => months == 1 ? '1 mois' : '$months mois';

class _Labeled extends StatelessWidget {
  const _Labeled(this.label, this.child);

  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(label, style: AzText.label),
        const SizedBox(height: 8),
        child,
      ],
    );
  }
}

class _DocumentTile extends StatelessWidget {
  const _DocumentTile({required this.label, required this.added, required this.onAdd, required this.onRemove});

  final String label;
  final bool added;
  final VoidCallback onAdd;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    return AzCard(
      padding: const EdgeInsets.all(14),
      child: Row(
        children: [
          AzIconTile(
            icon: added ? Icons.check_rounded : Icons.badge_outlined,
            background: added ? AzColors.successBg : AzColors.background,
            color: added ? AzColors.success : AzColors.ink2,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: AzText.titleSmall),
                Text(added ? 'Photo ajoutée' : 'Obligatoire',
                    style: AzText.caption.copyWith(color: added ? AzColors.success : AzColors.muted, fontSize: 13)),
              ],
            ),
          ),
          AzButton.secondary(added ? 'Retirer' : 'Ajouter', expand: false, height: 40, onPressed: added ? onRemove : onAdd),
        ],
      ),
    );
  }
}

/// Utilisé par l’accueil pour savoir si un brouillon existe encore.
bool draftIsFresh(QuoteDraft d) => DateTime.now().difference(d.savedAt) < Env.draftLifetime;
