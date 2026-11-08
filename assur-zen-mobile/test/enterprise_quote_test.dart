import 'package:assurzen_mobile/app/app_scope.dart';
import 'package:assurzen_mobile/app/session_controller.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/storage/local_store.dart';
import 'package:assurzen_mobile/core/ui.dart';
import 'package:assurzen_mobile/data/api/api_catalog_repository.dart';
import 'package:assurzen_mobile/data/api/api_enterprise_quote_repository.dart';
import 'package:assurzen_mobile/data/mock/mock_data.dart';
import 'package:assurzen_mobile/data/mock/mock_repositories.dart';
import 'package:assurzen_mobile/data/models/enterprise_quote.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:assurzen_mobile/data/repositories/repositories.dart';
import 'package:assurzen_mobile/features/quote/enterprise_quote_page.dart';
import 'package:assurzen_mobile/features/quote/enterprise_request_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'helpers/fonts.dart';

const _packs = [
  EnterprisePack(id: 'autopro', name: 'AutoPro', description: 'Flotte de véhicules.', options: [
    EnterpriseOption(id: 'OPTION_1', label: 'Option 1', total: 140878),
    EnterpriseOption(id: 'OPTION_2', label: 'Option 2', total: 214467),
  ]),
  // Une seule option, et pas la première : l'identifiant n'est pas consécutif.
  EnterprisePack(id: 'mpi-commerces', name: 'MPI Commerces', options: [
    EnterpriseOption(id: 'OPTION_2', label: 'Option 2', total: 179875),
  ]),
];

class _FakeEnterprise implements EnterpriseQuoteRepository {
  _FakeEnterprise({this.packs = _packs, this.failOnSimulate = false, this.domains = const [], this.submitError});

  final List<EnterprisePack> packs;
  final bool failOnSimulate;
  final List<EnterpriseActivityDomain> domains;
  final ApiException? submitError;
  final List<(String, String)> calls = [];
  final List<EnterpriseRequestInput> submitted = [];

  @override
  Future<List<EnterpriseActivityDomain>> getActivityDomains() async => domains;

  @override
  Future<EnterpriseRequestReceipt> submitRequest(EnterpriseRequestInput input) async {
    if (submitError != null) throw submitError!;
    submitted.add(input);
    return const EnterpriseRequestReceipt(
      reference: 'ENT-2026-000001',
      status: 'RECUE',
      formulaName: 'AutoPro',
      optionLabel: 'Option 2',
      total: 214467,
      message: 'Demande de souscription prise en compte. Un conseiller vous contactera.',
    );
  }

  @override
  Future<EnterpriseForm> getForm() async => EnterpriseForm(packs: packs, durationMonths: 12);

  @override
  Future<EnterpriseSimulation> simulate({required String formulaId, required String option}) async {
    calls.add((formulaId, option));
    if (failOnSimulate) throw ApiException.server();
    final pack = packs.firstWhere((p) => p.id == formulaId);
    final opt = pack.options.firstWhere((o) => o.id == option);
    return EnterpriseSimulation(
      formulaId: formulaId,
      formulaName: pack.name,
      option: option,
      optionLabel: opt.label,
      durationMonths: 12,
      total: opt.total,
      guarantees: formulaId == 'mpi-commerces'
          ? const [
              EnterpriseGuarantee(name: 'Responsabilité civile', indemnityAmount: 10000000, deductible: 'Néant', basis: 'Forfait'),
              EnterpriseGuarantee(name: 'dommages corporels', level: 1, indemnityAmount: 5000000),
              EnterpriseGuarantee(name: 'Vol par effraction', indemnityLabel: 'Non garanti'),
            ]
          : const [],
      limits: formulaId == 'mpi-commerces' ? const [EnterpriseLimit(label: 'Limite par sinistre (Dommages)', amount: 25000000)] : const [],
    );
  }
}

Repositories _repos(EnterpriseQuoteRepository enterprise) {
  final base = MockRepositories.create();
  return Repositories(
    auth: base.auth,
    catalog: base.catalog,
    quotes: base.quotes,
    motoQuotes: base.motoQuotes,
    payments: base.payments,
    contracts: base.contracts,
    claims: base.claims,
    notifications: base.notifications,
    support: base.support,
    engagement: base.engagement,
    enterpriseQuotes: enterprise,
  );
}

const _product = Product(
  id: 'entreprise',
  title: 'Assurance Entreprise',
  shortName: 'Entreprise',
  description: '',
  family: ProductFamily.professionnel,
  onlineSubscription: false,
  benefits: [],
  howItWorks: '',
  quoteType: 'ENTREPRISE',
);

Future<void> _pump(WidgetTester tester, Repositories repos) async {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = const Size(360, 1400);
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({'lock_choice_done': true});
  final store = await LocalStore.open();
  final session = SessionController(store: store, repos: repos)..updateUser(MockData.demoUser);
  await tester.pumpWidget(
    AppScope(repos: repos, session: session, store: store, child: const MaterialApp(home: EnterpriseQuotePage(product: _product))),
  );
  await tester.pump(const Duration(milliseconds: 600));
}

/// Le champ de saisie d'un formulaire, retrouvé par son libellé affiché au-dessus.
Finder field(String label) {
  final text = find.widgetWithText(AzTextField, label);
  final host = text.evaluate().isNotEmpty ? text : find.widgetWithText(AzAutocompleteField, label);
  return find.descendant(of: host, matching: find.byType(EditableText)).first;
}

void main() {
  setUpAll(loadAppFonts);

  test('le formulaire est lu tel que le serveur le donne, une option incomplète est ignorée', () {
    final f = ApiEnterpriseQuoteRepository.formFromJson({
      'productKey': 'entreprise',
      'durationMonths': 12,
      'packs': [
        {
          'id': 'securipro',
          'name': 'SecuriPro',
          'description': 'Locaux.',
          'options': [
            {'id': 'OPTION_1', 'label': 'Option 1', 'total': 156984, 'guaranteesAvailable': false},
            {'id': 'OPTION_2', 'label': 'Option 2'},
          ],
        },
        {'id': 'vide', 'name': 'Sans option', 'options': []},
        {'name': 'Sans identifiant', 'options': []},
      ],
    });
    expect(f.durationMonths, 12);
    expect(f.packs.length, 1);
    expect(f.packs.single.options.length, 1);
    expect(f.packs.single.options.single.total, 156984);
    expect(ApiEnterpriseQuoteRepository.formFromJson('x').packs, isEmpty);
  });

  test('la simulation donne un seul montant, les garanties et leurs lignes « dont »', () {
    final s = ApiEnterpriseQuoteRepository.simulationFromJson({
      'formulaId': 'mpi-commerces',
      'formulaName': 'MPI Commerces',
      'option': 'OPTION_2',
      'optionLabel': 'Option 2',
      'durationMonths': 12,
      'total': 179875,
      'currency': 'XOF',
      'guarantees': [
        {'order': 1, 'level': 0, 'name': 'Responsabilité civile', 'indemnityAmount': 10000000, 'deductible': 'Néant', 'basis': 'Forfait'},
        {'order': 2, 'level': 1, 'name': 'dommages corporels', 'indemnityAmount': 5000000},
        {'order': 3, 'level': 1, 'name': 'dommages matériels', 'indemnityAmount': 3000000},
        {'order': 4, 'level': 0, 'name': 'Vol par effraction', 'indemnityAmount': 0, 'indemnityLabel': 'Non garanti'},
      ],
      'limits': [
        {'label': 'Limite par sinistre (Dommages)', 'amount': 25000000},
        {'label': 'Sans montant', 'amount': 0},
      ],
      'note': null,
    })!;
    expect(s.total, 179875);
    final groups = s.guaranteeGroups;
    expect(groups.length, 2);
    expect(groups[0].children.length, 2);
    expect(groups[0].expandable, isTrue);
    // Un montant à 0 n'est jamais affiché comme un montant : le libellé du serveur le remplace.
    expect(groups[1].main.indemnityAmount, isNull);
    expect(groups[1].main.indemnityLabel, 'Non garanti');
    expect(groups[1].expandable, isFalse);
    expect(s.limits.length, 1);
    expect(s.note, isNull);

    // Corps incomplet : pas de simulation.
    expect(ApiEnterpriseQuoteRepository.simulationFromJson({'formulaId': 'x'}), isNull);
    expect(ApiEnterpriseQuoteRepository.simulationFromJson('x'), isNull);
    // Listes vides : rien à regrouper.
    expect(ApiEnterpriseQuoteRepository.simulationFromJson({'formulaId': 'a', 'option': 'OPTION_1', 'total': 1})!.guaranteeGroups, isEmpty);
  });

  test('le type de cotation vient du serveur, jamais de l’identifiant du produit', () {
    Product p(Map<String, dynamic> extra) => ApiCatalogRepository.productFromJson({'id': 'entreprise', 'title': 'Assurance Entreprise', 'family': 'PROFESSIONAL', ...extra});
    expect(p({'quote': {'available': true, 'type': 'ENTREPRISE'}}).hasEnterpriseQuote, isTrue);
    expect(p({'quote': {'available': false, 'type': null}}).hasEnterpriseQuote, isFalse);
    expect(p({}).hasEnterpriseQuote, isFalse, reason: 'sans drapeau, le client demande à être rappelé');
    expect(p({'quote': {'available': true, 'type': 'MOTO'}}).hasEnterpriseQuote, isFalse);
  });

  testWidgets('formule à deux options : formule, option avec prix, résultat', (tester) async {
    final fake = _FakeEnterprise();
    await _pump(tester, _repos(fake));
    expect(find.text('AutoPro'), findsOneWidget);
    expect(find.text('MPI Commerces'), findsOneWidget);

    await tester.tap(find.text('AutoPro'));
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('Option 1'), findsOneWidget);
    expect(find.text('Option 2'), findsOneWidget);
    expect(find.text(formatFcfa(140878)), findsOneWidget);

    await tester.tap(find.text('Option 2'));
    await tester.pump(const Duration(milliseconds: 300));
    await tester.tap(find.text('Voir ma cotation'));
    await tester.pump(const Duration(milliseconds: 600));
    expect(fake.calls, [('autopro', 'OPTION_2')]);
    expect(find.text(formatFcfa(214467)), findsWidgets);
    expect(find.textContaining('Prime annuelle TTC'), findsOneWidget);
    // AutoPro : aucune garantie fournie, la rubrique est masquée.
    expect(find.text('Garanties et montants d’indemnité'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('formule à une seule option : l’écran d’option est sauté et l’identifiant est celui du serveur', (tester) async {
    final fake = _FakeEnterprise();
    await _pump(tester, _repos(fake));
    await tester.tap(find.text('MPI Commerces'));
    await tester.pump(const Duration(milliseconds: 600));
    expect(fake.calls, [('mpi-commerces', 'OPTION_2')], reason: 'pas de OPTION_1 supposée');
    expect(find.text(formatFcfa(179875)), findsWidgets);
    // Garanties repliées : le détail n'est visible qu'après avoir touché la garantie.
    expect(find.text('Garanties et montants d’indemnité'), findsOneWidget);
    expect(find.text('Responsabilité civile'), findsOneWidget);
    expect(find.text(formatFcfa(10000000)), findsOneWidget);
    expect(find.text('Non garanti'), findsOneWidget);
    expect(find.textContaining('Franchise'), findsNothing);
    await tester.ensureVisible(find.text('Responsabilité civile'));
    await tester.tap(find.text('Responsabilité civile'));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.textContaining('Franchise'), findsOneWidget);
    expect(find.textContaining('Forfait'), findsOneWidget);
    expect(find.text('dont dommages corporels'), findsOneWidget);
    expect(find.text('Limites'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  test('domaines et accusé de réception : lus tels que le serveur les donne', () {
    final d = ApiEnterpriseQuoteRepository.domainsFromJson({
      'items': [
        {'code': 'AUTO_ECOLE', 'label': 'Auto-école'},
        {'code': 'X'},
        {'label': 'Autre activité'},
      ],
    });
    expect(d.map((e) => e.label), ['Auto-école', 'Autre activité']);
    expect(ApiEnterpriseQuoteRepository.domainsFromJson('x'), isEmpty);

    final r = ApiEnterpriseQuoteRepository.receiptFromJson({
      'reference': 'ENT-2026-000001',
      'status': 'RECUE',
      'formulaName': 'AutoPro',
      'optionLabel': 'Option 2',
      'total': 214467,
      'message': 'Demande de souscription prise en compte.',
    })!;
    expect(r.reference, 'ENT-2026-000001');
    expect(r.message, 'Demande de souscription prise en compte.');
    // Sans référence, la demande n'est pas déclarée prise en compte.
    expect(ApiEnterpriseQuoteRepository.receiptFromJson({'status': 'RECUE'}), isNull);
    expect(ApiEnterpriseQuoteRepository.receiptFromJson('x'), isNull);
  });

  test('la demande envoyée ne contient aucun prix', () {
    const input = EnterpriseRequestInput(
      formulaId: 'autopro',
      option: 'OPTION_2',
      activityDomain: 'Auto-école',
      companyName: 'Atlantic Transit',
      rccmNumber: 'ci-abj-2020-b-12345',
      location: 'Abidjan',
      representativeName: 'Awa Koné',
      representativeRole: 'Directrice',
      representativePhone: '0701010101',
      representativeEmail: 'a@b.ci',
    );
    final json = input.toJson();
    expect(json.keys, containsAll(['formulaId', 'option', 'activityDomain', 'companyName', 'rccmNumber', 'location', 'representativeName', 'representativeRole', 'representativePhone', 'representativeEmail']));
    expect(json.keys.any((k) => k.toLowerCase().contains('total') || k.toLowerCase().contains('price') || k.toLowerCase().contains('amount')), isFalse);
  });

  Future<void> fillCompany(WidgetTester tester) async {
    Future<void> type(String label, String text) async {
      final f = field(label);
      await tester.ensureVisible(f);
      await tester.enterText(f, text);
      await tester.pump(const Duration(milliseconds: 100));
    }

    await type('Domaine d’activité', 'Auto-école');
    await type('Nom de l’entreprise', 'Atlantic Transit SARL');
    await type('N° RCCM', 'ci-abj-2020-b-12345');
    await type('Localisation', 'Abidjan, Marcory');
  }

  Future<void> fillRepresentative(WidgetTester tester) async {
    Future<void> type(String label, String text) async {
      final f = field(label);
      await tester.ensureVisible(f);
      await tester.enterText(f, text);
      await tester.pump(const Duration(milliseconds: 100));
    }

    await type('Nom du représentant', 'Awa Koné');
    await type('Fonction', 'Directrice générale');
    await type('Téléphone', '07 01 01 01 01');
    await type('E-mail', 'awa.kone@atlantic.ci');
  }

  Future<void> pumpRequest(WidgetTester tester, _FakeEnterprise fake) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(360, 1400);
    addTearDown(tester.view.reset);
    SharedPreferences.setMockInitialValues({'lock_choice_done': true});
    final store = await LocalStore.open();
    final repos = _repos(fake);
    final session = SessionController(store: store, repos: repos)..updateUser(MockData.demoUser);
    final sim = await fake.simulate(formulaId: 'autopro', option: 'OPTION_2');
    await tester.pumpWidget(
      AppScope(
        repos: repos,
        session: session,
        store: store,
        child: MaterialApp(home: EnterpriseRequestPage(args: EnterpriseRequestArgs(product: _product, simulation: sim))),
      ),
    );
    await tester.pump(const Duration(milliseconds: 600));
  }

  testWidgets('demande de souscription : saisie contrôlée, envoi sans prix, puis « Demande envoyée »', (tester) async {
    final fake = _FakeEnterprise(domains: const [EnterpriseActivityDomain(code: 'AUTO_ECOLE', label: 'Auto-école')]);
    await pumpRequest(tester, fake);
    expect(find.text('Étape 1 sur 3'), findsOneWidget);

    // Étape 1 vide : on ne passe pas, les champs obligatoires sont signalés.
    await tester.ensureVisible(find.text('Continuer'));
    await tester.tap(find.text('Continuer'));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Étape 1 sur 3'), findsOneWidget);
    expect(find.text('Indiquez le nom de l’entreprise.'), findsOneWidget);

    await fillCompany(tester);
    await tester.ensureVisible(find.text('Continuer'));
    await tester.tap(find.text('Continuer'));
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('Étape 2 sur 3'), findsOneWidget);

    // Téléphone invalide : bloqué.
    await tester.enterText(field('Téléphone'), '0912345678');
    await fillRepresentative(tester);
    await tester.enterText(field('Téléphone'), '0912345678');
    await tester.ensureVisible(find.text('Continuer'));
    await tester.tap(find.text('Continuer'));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Indiquez un numéro à 10 chiffres (01, 05 ou 07).'), findsOneWidget);

    await tester.enterText(field('Téléphone'), '07 01 01 01 01');
    await tester.ensureVisible(find.text('Continuer'));
    await tester.tap(find.text('Continuer'));
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('Étape 3 sur 3'), findsOneWidget);
    expect(find.text('Vérifiez votre demande'), findsOneWidget);
    expect(find.text('CI-ABJ-2020-B-12345'), findsOneWidget);

    await tester.ensureVisible(find.text('Envoyer ma demande'));
    await tester.tap(find.text('Envoyer ma demande'));
    await tester.pump(const Duration(milliseconds: 600));

    expect(fake.submitted.length, 1);
    final sent = fake.submitted.single;
    expect(sent.formulaId, 'autopro');
    expect(sent.option, 'OPTION_2');
    expect(sent.representativePhone, '0701010101');
    expect(sent.toJson().containsKey('total'), isFalse);

    // Écran « Demande envoyée » : texte du serveur tel quel, référence, rappel de la demande.
    expect(find.text('Demande de souscription prise en compte. Un conseiller vous contactera.'), findsOneWidget);
    expect(find.text('Référence ENT-2026-000001'), findsOneWidget);
    expect(find.text('Retour à l’accueil'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('erreur du serveur sur le RCCM : retour à l’étape concernée, saisie conservée', (tester) async {
    final fake = _FakeEnterprise(
      submitError: const ApiException(kind: ApiErrorKind.validation, message: 'Ce numéro RCCM est invalide.', field: 'rccmNumber', statusCode: 400),
    );
    await pumpRequest(tester, fake);
    await fillCompany(tester);
    await tester.ensureVisible(find.text('Continuer'));
    await tester.tap(find.text('Continuer'));
    await tester.pump(const Duration(milliseconds: 400));
    await fillRepresentative(tester);
    await tester.ensureVisible(find.text('Continuer'));
    await tester.tap(find.text('Continuer'));
    await tester.pump(const Duration(milliseconds: 400));
    await tester.ensureVisible(find.text('Envoyer ma demande'));
    await tester.tap(find.text('Envoyer ma demande'));
    await tester.pump(const Duration(milliseconds: 600));

    expect(find.text('Étape 1 sur 3'), findsOneWidget);
    expect(find.text('Ce numéro RCCM est invalide.'), findsOneWidget);
    expect(find.text('Demande envoyée'), findsNothing, reason: 'jamais déclarée prise en compte sans accusé du serveur');
    expect(find.text('Atlantic Transit SARL'), findsOneWidget, reason: 'la saisie est conservée');
  });

  testWidgets('demande déjà enregistrée (réponse perdue puis renvoi) : message rassurant, pas d’erreur ni de référence inventée', (tester) async {
    final fake = _FakeEnterprise(
      submitError: const ApiException(
        kind: ApiErrorKind.business,
        message: 'Une demande pour cette entreprise et cette formule est deja en cours de traitement.',
        code: 'REQUEST_ALREADY_PENDING',
        statusCode: 409,
      ),
    );
    await pumpRequest(tester, fake);
    await fillCompany(tester);
    await tester.ensureVisible(find.text('Continuer'));
    await tester.tap(find.text('Continuer'));
    await tester.pump(const Duration(milliseconds: 400));
    await fillRepresentative(tester);
    await tester.ensureVisible(find.text('Continuer'));
    await tester.tap(find.text('Continuer'));
    await tester.pump(const Duration(milliseconds: 400));
    await tester.ensureVisible(find.text('Envoyer ma demande'));
    await tester.tap(find.text('Envoyer ma demande'));
    await tester.pump(const Duration(milliseconds: 600));

    expect(find.textContaining('Votre demande est déjà enregistrée.'), findsOneWidget);
    expect(find.textContaining('inutile de la renvoyer'), findsOneWidget);
    expect(find.textContaining('deja en cours'), findsNothing, reason: 'le message d’erreur brut du serveur n’est plus montré');
    expect(find.textContaining('Référence'), findsNothing, reason: 'aucune référence inventée');
    expect(find.text('Étape 3 sur 3'), findsOneWidget);
  });

  testWidgets('serveur sans formule : aucun prix, un rappel est proposé', (tester) async {
    await _pump(tester, _repos(_FakeEnterprise(packs: const [])));
    expect(find.text('Cotation indisponible'), findsOneWidget);
    expect(find.text('Être rappelé'), findsOneWidget);
  });

  testWidgets('erreur du serveur à la simulation : message clair, retour à la liste', (tester) async {
    await _pump(tester, _repos(_FakeEnterprise(failOnSimulate: true)));
    await tester.tap(find.text('MPI Commerces'));
    await tester.pump(const Duration(milliseconds: 600));
    expect(find.text('MPI Commerces'), findsOneWidget);
    expect(find.text('AutoPro'), findsOneWidget);
    expect(find.textContaining('FCFA'), findsNothing, reason: 'aucun prix inventé');
  });
}
