import 'dart:convert';

import 'package:assurzen_mobile/app/app_scope.dart';
import 'package:assurzen_mobile/app/session_controller.dart';
import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/network/endpoints.dart';
import 'package:assurzen_mobile/core/storage/local_store.dart';
import 'package:assurzen_mobile/core/utils/formatters.dart';
import 'package:assurzen_mobile/core/widgets/az_inputs.dart';
import 'package:assurzen_mobile/data/api/api_moto_quote_repository.dart';
import 'package:assurzen_mobile/data/mock/mock_data.dart';
import 'package:assurzen_mobile/data/mock/mock_repositories.dart';
import 'package:assurzen_mobile/data/models/moto_quote.dart';
import 'package:assurzen_mobile/data/repositories/repositories.dart';
import 'package:assurzen_mobile/features/quote/moto_quote_page.dart' show MotoQuoteResult;
import 'package:assurzen_mobile/features/quote/voyage_quote_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'helpers/fonts.dart';

http.Response _json(int status, Object? body) => http.Response.bytes(
      utf8.encode(jsonEncode(body)),
      status,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );

Map<String, dynamic> _formJson() => {
      'spaces': [
        {'code': 'AFRIQUE_OUEST', 'label': 'Afrique de l\'Ouest'},
      ],
      'countries': [
        {'id': 3, 'name': 'FRANCE', 'space': 'ESPACE_SCHENGEN', 'spaceLabel': 'Schengen et Afrique du Nord'},
        {'id': 8, 'name': 'MALI', 'space': 'AFRIQUE_OUEST', 'spaceLabel': 'Afrique de l\'Ouest'},
        {'id': 1, 'name': 'ALLEMAGNE', 'space': 'ESPACE_SCHENGEN', 'spaceLabel': 'Schengen et Afrique du Nord'},
        {'id': 9, 'name': '', 'space': 'X', 'spaceLabel': 'Sans nom'},
      ],
      'minAge': 0,
      'maxAge': 80,
      'maxDurationDays': 365,
      'minDepartureDate': '2026-11-05',
    };

Map<String, dynamic> _simJson() => {
      'country': {'id': 1, 'name': 'ALLEMAGNE', 'space': 'ESPACE_SCHENGEN', 'spaceLabel': 'Schengen et Afrique du Nord'},
      'age': 30,
      'ageBand': '12-80',
      'durationDays': 7,
      'durationBand': 'jusqu\'à 7 jours',
      'netPremium': 9107,
      'accessories': 500,
      'taxes': 1393,
      'totalPremium': 11000,
    };

Map<String, dynamic> _quoteJson({Object? voyage}) => {
      'reference': 'DEV-2026-000050',
      'status': 'CALCULE',
      'canAccept': true,
      'productKey': 'voyage',
      'formulaName': 'Assurance Voyage - Schengen et Afrique du Nord',
      'vehicleType': 'VOYAGE',
      'durationMonths': 1,
      'effectiveDate': '2026-12-01',
      'expiryDate': '2026-12-07',
      'validUntil': '2026-12-01',
      'premium': {'netPremium': 9107, 'taxes': 1393, 'accessories': 500, 'fga': 0, 'total': 11000},
      'guarantees': <Object>[],
      'voyage': voyage ??
          {
            'countryName': 'ALLEMAGNE',
            'space': 'ESPACE_SCHENGEN',
            'spaceLabel': 'Schengen et Afrique du Nord',
            'travelerBirthDate': '1996-05-20',
            'travelerAge': 30,
            'passportNumber': '****4567',
            'departureDate': '2026-12-01',
            'returnDate': '2026-12-07',
            'durationDays': 7,
          },
    };

List<Map<String, dynamic>> _guaranteesJson() => [
      {
        'label': 'Frais médicaux',
        'details': [
          {'label': 'Plafond / prise en charge', 'amount': 'Jusqu\'à 30 000 euros'},
        ],
      },
      {
        'label': 'Indemnités pour perte de bagages',
        'details': [
          {'label': 'Plafond / prise en charge', 'amount': 'Jusqu\'à 400 euros'},
        ],
      },
    ];

void main() {
  setUpAll(loadAppFonts);

  group('Calculs de dates (simulation publique)', () {
    test('âge : années entières, anniversaire inclus', () {
      final birth = DateTime(1996, 5, 20);

      expect(ageAt(birth, DateTime(2026, 5, 19)), 29, reason: 'la veille de l\'anniversaire');
      expect(ageAt(birth, DateTime(2026, 5, 20)), 30, reason: 'le jour de l\'anniversaire');
      expect(ageAt(birth, DateTime(2026, 12, 31)), 30);
      expect(ageAt(DateTime(2026, 11, 5), DateTime(2026, 11, 5)), 0);
      expect(ageAt(DateTime(2027, 1, 1), DateTime(2026, 11, 5)), 0, reason: 'naissance après le départ : jamais négatif');
    });

    test('âge : né un 29 février', () {
      final birth = DateTime(2000, 2, 29);

      expect(ageAt(birth, DateTime(2027, 2, 28)), 26, reason: 'année non bissextile : pas encore l\'anniversaire le 28');
      expect(ageAt(birth, DateTime(2027, 3, 1)), 27);
      expect(ageAt(birth, DateTime(2028, 2, 29)), 28);
    });

    test('durée : les deux jours inclus, quel que soit le fuseau', () {
      expect(inclusiveDays(DateTime(2026, 12, 1), DateTime(2026, 12, 1)), 1);
      expect(inclusiveDays(DateTime(2026, 12, 1), DateTime(2026, 12, 7)), 7, reason: 'du 1er au 7 = 7 jours');
      expect(inclusiveDays(DateTime(2026, 12, 28), DateTime(2027, 1, 3)), 7, reason: 'à cheval sur deux années');
      expect(inclusiveDays(DateTime(2026, 11, 5), DateTime(2027, 11, 4)), 365);
      expect(inclusiveDays(DateTime(2026, 3, 28), DateTime(2026, 4, 2)), 6, reason: 'changement d\'heure : on compte des jours, pas des heures');
    });
  });

  group('Montants qui ne se coupent pas', () {
    test('espace entre groupes de chiffres et avant « F CFA » : insécable', () {
      expect(keepNumbersTogether('30 000 euros (soit 19 678 710 F CFA)'), '30\u00A0000 euros (soit 19\u00A0678\u00A0710\u00A0F\u00A0CFA)');
      expect(keepNumbersTogether('Jusqu\'à 400 euros (260 000 F CFA)'), 'Jusqu\'à 400 euros (260\u00A0000\u00A0F\u00A0CFA)');
      expect(keepNumbersTogether('11 000 FCFA'), '11\u00A0000\u00A0FCFA');
    });

    test('les autres espaces restent normales : le texte peut toujours passer à la ligne', () {
      expect(keepNumbersTogether('Frais de transport réels'), 'Frais de transport réels');
      expect(keepNumbersTogether('100 euros par nuit - maximum 5 nuits'), '100 euros par nuit - maximum 5 nuits');
      expect(keepNumbersTogether('maximum 5 nuits'), 'maximum 5 nuits', reason: 'un chiffre seul n\'est pas lié au mot suivant');
      expect(keepNumbersTogether('1234 5678'), '1234 5678', reason: 'pas un groupe de trois chiffres');
      expect(keepNumbersTogether(''), '');
    });

    test('déjà insécable ou idempotent : un second passage ne change rien', () {
      final once = keepNumbersTogether('19 678 710 F CFA');
      expect(keepNumbersTogether(once), once);
    });
  });

  group('Garanties par rubrique', () {
    MotoGuarantee g(String label, [String? group]) => MotoGuarantee(label: label, group: group, details: const []);

    test('rubriques dans l\'ordre de première apparition, sans rubrique à la fin', () {
      final r = groupGuarantees([g('a', 'Santé'), g('b'), g('c', 'Bagages'), g('d', 'Santé')]);
      expect(r.map((e) => e.$1), ['Santé', 'Bagages', null]);
      expect(r[0].$2.map((x) => x.label), ['a', 'd']);
    });

    test('aucune rubrique : pas de regroupement (liste à plat)', () {
      expect(groupGuarantees([g('a'), g('b')]), isEmpty);
    });

    test('rubrique vide ou d\'espaces : lue comme absente', () {
      final q = MotoQuote.fromJson({
        ..._quoteJson(),
        'guarantees': [
          {'label': 'x', 'group': '  ', 'details': []},
        ],
      });
      expect(q.simulation.guarantees.single.group, isNull);
    });

    testWidgets('rubriques repliables : la première est dépliée, les autres repliées', (tester) async {
      final quote = MotoQuote.fromJson({
        ..._quoteJson(),
        'guarantees': [
          {'label': 'Frais médicaux', 'group': 'Santé', 'details': [{'label': 'Plafond', 'amount': '30 000 euros'}]},
          {'label': 'Perte de bagages', 'group': 'Bagages et retards', 'details': [{'label': 'Plafond', 'amount': '800 euros'}]},
        ],
      });
      await tester.pumpWidget(MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: MotoQuoteResult(simulation: quote.simulation, quote: null, busy: false, onSave: () {}, onAccept: () {}),
          ),
        ),
      ));
      await tester.pumpAndSettle();
      expect(find.text('Santé'), findsOneWidget);
      expect(find.text('Bagages et retards'), findsOneWidget);
      expect(find.text('Frais médicaux').hitTestable(), findsOneWidget);
      expect(find.text('Perte de bagages').hitTestable(), findsNothing);
      await tester.tap(find.text('Bagages et retards'));
      await tester.pumpAndSettle();
      expect(find.text('Perte de bagages').hitTestable(), findsOneWidget);
    });
  });

  group('Saisie de la date de naissance', () {
    TextEditingValue type(String before, String typed) =>
        const DateMaskFormatter().formatEditUpdate(TextEditingValue(text: before), TextEditingValue(text: typed));

    test('les barres obliques s\'ajoutent toutes seules, chiffre par chiffre', () {
      var t = '';
      for (final d in '20051996'.split('')) {
        t = type(t, t + d).text;
      }

      expect(t, '20/05/1996');
      expect(type('2', '20').text, '20');
      expect(type('20', '200').text, '20/0');
      expect(type('20/05', '20/051').text, '20/05/1');
    });

    test('suppression : on peut effacer en arrière, y compris sur une barre', () {
      expect(type('20/05/1996', '20/05/199').text, '20/05/199');
      expect(type('20/', '20').text, '20');
      expect(type('20/0', '20/').text, '20');
    });

    test('seuls les chiffres sont gardés, 8 au plus', () {
      expect(type('', 'ab12cd').text, '12');
      expect(type('', '1234567890').text, '12/34/5678');
    });

    test('lecture : date réelle seulement', () {
      expect(parseDateFr('20/05/1996'), DateTime(1996, 5, 20));
      expect(parseDateFr('29/02/2000'), DateTime(2000, 2, 29));
      expect(parseDateFr('29/02/2001'), isNull);
      expect(parseDateFr('31/04/2000'), isNull);
      expect(parseDateFr('00/05/2000'), isNull);
      expect(parseDateFr('15/13/2000'), isNull);
      expect(parseDateFr('20/05/96'), isNull);
      expect(parseDateFr('20/05/0996'), isNull);
      expect(parseDateFr('20051996'), isNull);
      expect(parseDateFr(''), isNull);
    });
  });

  group('Lecture des réponses', () {
    test('formulaire : pays groupés par espace, ordre alphabétique, pays sans nom ignoré', () {
      final f = VoyageForm.fromJson(_formJson());

      expect(f.countries.map((c) => c.name), ['FRANCE', 'MALI', 'ALLEMAGNE'], reason: 'le pays sans nom est écarté');
      expect(f.bySpace.keys.toList(), ['Afrique de l\'Ouest', 'Schengen et Afrique du Nord']);
      expect(f.bySpace['Schengen et Afrique du Nord']!.map((c) => c.name), ['ALLEMAGNE', 'FRANCE']);
      expect(f.countries.first.id, '3', reason: 'identifiant numérique du serveur converti en texte');
      expect(f.maxAge, 80);
      expect(f.maxDurationDays, 365);
      expect(f.minDepartureDate, DateTime(2026, 11, 5), reason: 'date du jour du serveur');
    });

    test('formulaire vide ou incomplet : aucune exception', () {
      final f = VoyageForm.fromJson(const {});

      expect(f.countries, isEmpty);
      expect(f.bySpace, isEmpty);
      expect(f.minDepartureDate, isNull);
    });

    test('devis voyage : détail du voyage, durée en jours, passeport masqué tel que reçu', () {
      final q = MotoQuote.fromJson(_quoteJson());

      expect(q.isVoyage, isTrue);
      expect(q.voyage?.countryName, 'ALLEMAGNE');
      expect(q.voyage?.spaceLabel, 'Schengen et Afrique du Nord');
      expect(q.voyage?.travelerAge, 30);
      expect(q.voyage?.passportNumber, '****4567');
      expect(q.voyage?.departureDate, DateTime(2026, 12, 1));
      expect(q.voyage?.returnDate, DateTime(2026, 12, 7));
      expect(q.voyage?.durationDays, 7);
      expect(q.voyage?.durationLabel, '7 jours');
      expect(q.simulation.premium.total, 11000);
      expect(q.simulation.guarantees, isEmpty, reason: 'aucune garantie inventée');
      expect(q.brand, isEmpty);
    });

    test('durée : « 1 jour » au singulier, absente = aucune durée', () {
      expect(VoyageInfo.fromJson({'countryName': 'MALI', 'durationDays': 1})?.durationLabel, '1 jour');
      expect(VoyageInfo.fromJson({'countryName': 'MALI'})?.durationLabel, isNull);
    });

    test('moto et VTC : pas de voyage ; objet voyage sans destination ignoré', () {
      final moto = MotoQuote.fromJson({..._quoteJson(), 'productKey': 'moto', 'voyage': null});

      expect(moto.voyage, isNull);
      expect(moto.isVoyage, isFalse);
      expect(VoyageInfo.fromJson({'durationDays': 7}), isNull);
      expect(VoyageInfo.fromJson('oups'), isNull);
    });

    test('accepter un devis voyage conserve son voyage et ne ment pas sur le statut', () {
      final q = MotoQuote.fromJson(_quoteJson()).copyAccepted(DateTime(2026, 11, 5));

      expect(q.status, MotoQuoteStatus.accepte);
      expect(q.voyage?.countryName, 'ALLEMAGNE');
    });

    test('simulation avec garanties du serveur : conservées telles quelles', () {
      final s = VoyageSimulation.fromJson({..._simJson(), 'guarantees': _guaranteesJson()});
      final q = s.toQuoteSimulation();

      expect(q.guarantees.map((g) => g.label), ['Frais médicaux', 'Indemnités pour perte de bagages']);
      expect(q.guarantees.first.details.single.amount, 'Jusqu\'à 30 000 euros');
      expect(MotoQuote.fromJson({..._quoteJson(), 'guarantees': _guaranteesJson()}).simulation.guarantees, hasLength(2));
    });

    test('note de bas de page des garanties : simulation, devis enregistré, vide ou absente', () {
      const note = '* Ou le montant équivalent dans la devise du pays du sinistre.';

      final sim = VoyageSimulation.fromJson({..._simJson(), 'guarantees': _guaranteesJson(), 'guaranteesNote': note});
      expect(sim.toQuoteSimulation().guaranteesNote, note);

      final voyage = {..._quoteJson()['voyage'] as Map<String, dynamic>, 'guaranteesNote': note};
      final quote = MotoQuote.fromJson({..._quoteJson(voyage: voyage), 'guarantees': _guaranteesJson()});
      expect(quote.simulation.guaranteesNote, note, reason: 'dans le devis, la note est dans l\'objet voyage');

      for (final empty in [null, '', '   ']) {
        expect(VoyageSimulation.fromJson({..._simJson(), 'guaranteesNote': empty}).toQuoteSimulation().guaranteesNote, isNull);
      }
      expect(MotoQuote.fromJson(_quoteJson()).simulation.guaranteesNote, isNull, reason: 'ancien serveur : aucune note');
      expect(MotoQuote.fromJson({..._quoteJson(), 'productKey': 'moto', 'voyage': null}).simulation.guaranteesNote, isNull);
    });

    test('simulation : prime décomposée, mêmes lignes que les autres produits, aucune garantie', () {
      final s = VoyageSimulation.fromJson(_simJson());
      final q = s.toQuoteSimulation(departure: DateTime(2026, 12, 1), back: DateTime(2026, 12, 7));

      expect(s.countryName, 'ALLEMAGNE');
      expect(s.age, 30);
      expect(s.durationDays, 7);
      expect(s.durationBand, 'jusqu\'à 7 jours');
      expect(q.premium.netPremium, 9107);
      expect(q.premium.accessories, 500);
      expect(q.premium.taxes, 1393);
      expect(q.premium.total, 11000);
      expect(q.premium.fga, 0);
      expect(q.guarantees, isEmpty);
      expect(q.effectiveDate, DateTime(2026, 12, 1));
      expect(q.expiryDate, DateTime(2026, 12, 7));
    });

    test('récapitulatif : jamais de durée en mois ni de type de véhicule', () {
      final rows = voyageRows(MotoQuote.fromJson(_quoteJson()).voyage, null);
      final text = rows.map((r) => '${r.$1} ${r.$2}').join('|');

      expect(text, contains('ALLEMAGNE'));
      expect(text, contains('7 jours'));
      expect(text, contains('****4567'));
      expect(text, isNot(contains('mois')));
      expect(text, isNot(contains('VOYAGE')));
    });
  });

  group('Appels au serveur', () {
    late List<http.Request> calls;

    ApiMotoQuoteRepository build(MockClientHandler h) {
      calls = [];
      return ApiMotoQuoteRepository(ApiClient(
        baseUrl: 'http://gw.test',
        tokenProvider: () async => 'jeton',
        httpClient: MockClient((req) async {
          calls.add(req);
          return h(req);
        }),
      ));
    }

    final request = VoyageRequest(
      countryId: '1',
      birthDate: DateTime(1996, 5, 20),
      departureDate: DateTime(2026, 12, 1),
      returnDate: DateTime(2026, 12, 7),
    );

    test('formulaire : GET public', () async {
      final repo = build((_) async => _json(200, _formJson()));

      final f = await repo.getVoyageForm();

      expect(calls.single.method, 'GET');
      expect(calls.single.url.path, endsWith('/public/quotes/voyage/form'));
      expect(f.countries, hasLength(3));
    });

    test('simulation : le serveur reçoit un âge et une durée, pas des dates', () async {
      final repo = build((_) async => _json(200, _simJson()));

      final s = await repo.simulateVoyage(request);

      expect(calls.single.method, 'POST');
      expect(calls.single.url.path, endsWith('/public/quotes/voyage/simulate'));
      expect(jsonDecode(calls.single.body), {'countryId': 1, 'age': 30, 'durationDays': 7});
      expect(s.premium.total, 11000);
    });

    test('devis : dates au format AAAA-MM-JJ, champs facultatifs envoyés seulement s\'ils sont remplis', () async {
      final repo = build((_) async => _json(201, _quoteJson()));

      final q = await repo.createVoyage(request);

      expect(calls.single.url.path, endsWith('/mobile/quotes/voyage'));
      expect(calls.single.headers['Authorization'], 'Bearer jeton');
      expect(jsonDecode(calls.single.body), {
        'countryId': 1,
        'birthDate': '1996-05-20',
        'departureDate': '2026-12-01',
        'returnDate': '2026-12-07',
      });
      expect(q.reference, 'DEV-2026-000050');
    });

    test('voyageur différent et passeport : envoyés, espaces retirés', () async {
      final repo = build((_) async => _json(201, _quoteJson()));

      await repo.createVoyage(VoyageRequest(
        countryId: '8',
        birthDate: DateTime(2015, 1, 2),
        departureDate: DateTime(2026, 12, 1),
        returnDate: DateTime(2026, 12, 7),
        travelerName: '  Awa Koné ',
        passportNumber: ' 12AB34567 ',
      ));

      final body = jsonDecode(calls.single.body) as Map;
      expect(body['travelerName'], 'Awa Koné');
      expect(body['passportNumber'], '12AB34567');
      expect(body['countryId'], 8);
    });

    test('erreurs de validation : le champ nommé par le serveur est conservé', () async {
      final repo = build((_) async => _json(400, {'code': 'VALIDATION_FAILED', 'message': 'Le départ est passé.', 'field': 'departureDate'}));

      await expectLater(
        repo.createVoyage(request),
        throwsA(isA<ApiException>().having((e) => e.field, 'field', 'departureDate').having((e) => e.message, 'message', 'Le départ est passé.')),
      );
    });

    test('grille incomplète (409), trop de devis (429), sans compte (403)', () async {
      final tariff = build((_) async => _json(409, {'code': 'TARIFF_NOT_AVAILABLE', 'message': 'Tarif indisponible.'}));
      await expectLater(tariff.simulateVoyage(request), throwsA(isA<ApiException>().having((e) => e.code, 'code', 'TARIFF_NOT_AVAILABLE')));

      final limit = build((_) async => _json(429, {'code': 'RATE_LIMITED', 'message': 'Trop de devis.', 'retryAfterSeconds': 3600}));
      await expectLater(limit.createVoyage(request), throwsA(isA<ApiException>().having((e) => e.retryAfterSeconds, 'retryAfter', 3600)));

      final noToken = build((_) async => _json(403, {'code': 'FORBIDDEN', 'message': 'Interdit.'}));
      await expectLater(noToken.createVoyage(request), throwsA(isA<ApiException>()));
    });

    test('adresses', () {
      expect(Endpoints.voyageQuoteForm, endsWith('/public/quotes/voyage/form'));
      expect(Endpoints.voyageQuoteSimulate, endsWith('/public/quotes/voyage/simulate'));
      expect(Endpoints.voyageQuoteCreate, endsWith('/mobile/quotes/voyage'));
    });
  });

  group('Écran du devis voyage', () {
    testWidgets('champs vides : chaque oubli est signalé, aucun appel au serveur', (tester) async {
      final fake = _Fake();
      await _show(tester, fake);

      await tester.tap(find.text('Calculer mon devis'));
      await tester.pumpAndSettle();

      expect(find.text('Choisissez un pays de destination.'), findsOneWidget);
      expect(find.text('Indiquez la date de naissance.'), findsOneWidget);
      expect(find.text('Indiquez la date de départ.'), findsOneWidget);
      expect(find.text('Indiquez la date de retour.'), findsOneWidget);
      expect(fake.simulations, isEmpty);
    });

    testWidgets('sélecteur de pays : regroupés par espace, choix du serveur, le pays choisi s\'affiche', (tester) async {
      await _show(tester, _Fake());

      await tester.tap(find.text('Choisir un pays'));
      await tester.pumpAndSettle();

      expect(find.text('AFRIQUE DE L\'OUEST'), findsOneWidget);
      expect(find.text('SCHENGEN ET AFRIQUE DU NORD'), findsOneWidget);
      expect(find.text('ALLEMAGNE'), findsOneWidget);
      expect(find.text('SANS NOM'), findsNothing, reason: 'un pays sans nom n\'est jamais proposé');

      await tester.tap(find.text('MALI'));
      await tester.pumpAndSettle();

      expect(find.text('MALI'), findsOneWidget);
      expect(find.text('Choisir un pays'), findsNothing);
    });

    testWidgets('parcours : pays, dates, calcul, enregistrement', (tester) async {
      final fake = _Fake();
      await _show(tester, fake);

      await tester.tap(find.text('Choisir un pays'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('ALLEMAGNE'));
      await tester.pumpAndSettle();

      // Naissance : saisie au clavier, les barres sont ajoutées toutes seules.
      await tester.enterText(find.widgetWithText(TextField, 'JJ/MM/AAAA'), '01011990');
      await tester.pump();
      expect(find.text('01/01/1990'), findsOneWidget);

      // Départ et retour : chaque sélecteur s'ouvre sur « aujourd'hui » (dans les bornes du serveur) : on valide.
      for (final label in ['Date de départ', 'Date de retour']) {
        await tester.tap(find.text('Choisir une date').first);
        await tester.pumpAndSettle();
        await tester.tap(find.text('Valider'));
        await tester.pumpAndSettle();
        expect(find.text(label), findsOneWidget);
      }

      expect(find.textContaining('Durée : 1 jour'), findsOneWidget);

      await tester.tap(find.text('Calculer mon devis'));
      await tester.pumpAndSettle();

      expect(fake.simulations, hasLength(1));
      expect(fake.simulations.single.countryId, '1');
      expect(find.text('Destination'), findsOneWidget);
      expect(find.text('Enregistrer mon devis'), findsOneWidget);
      expect(find.textContaining('mois'), findsNothing, reason: 'jamais de durée en mois');

      await tester.tap(find.text('Enregistrer mon devis'));
      await tester.pumpAndSettle();

      expect(fake.created, hasLength(1));
      expect(find.text('Accepter ce devis'), findsOneWidget);
    });

    testWidgets('naissance : date inexistante ou incomplète refusée, date future refusée', (tester) async {
      final fake = _Fake();
      await _show(tester, fake);
      await tester.tap(find.text('Choisir un pays'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('FRANCE'));
      await tester.pumpAndSettle();
      for (var i = 0; i < 2; i++) {
        await tester.tap(find.text('Choisir une date').first);
        await tester.pumpAndSettle();
        await tester.tap(find.text('Valider'));
        await tester.pumpAndSettle();
      }

      await tester.enterText(find.widgetWithText(TextField, 'JJ/MM/AAAA'), '31022000');
      await tester.pump();
      await tester.tap(find.text('Calculer mon devis'));
      await tester.pumpAndSettle();
      expect(find.text('Saisissez une date valide (JJ/MM/AAAA).'), findsOneWidget, reason: 'le 31 février n\'existe pas');

      await tester.enterText(find.widgetWithText(TextField, 'JJ/MM/AAAA'), '0101');
      await tester.pump();
      await tester.tap(find.text('Calculer mon devis'));
      await tester.pumpAndSettle();
      expect(find.text('Saisissez une date valide (JJ/MM/AAAA).'), findsOneWidget, reason: 'date incomplète');

      await tester.enterText(find.widgetWithText(TextField, 'JJ/MM/AAAA'), '01012999');
      await tester.pump();
      await tester.tap(find.text('Calculer mon devis'));
      await tester.pumpAndSettle();
      expect(find.text('La naissance ne peut pas être dans le futur.'), findsOneWidget);
      expect(fake.simulations, isEmpty);
    });

    testWidgets('garanties du serveur : affichées telles quelles après le calcul', (tester) async {
      final fake = _Fake()..withGuarantees = true;
      await _show(tester, fake);
      await tester.tap(find.text('Choisir un pays'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('FRANCE'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'JJ/MM/AAAA'), '01011990');
      await tester.pump();
      for (var i = 0; i < 2; i++) {
        await tester.tap(find.text('Choisir une date').first);
        await tester.pumpAndSettle();
        await tester.tap(find.text('Valider'));
        await tester.pumpAndSettle();
      }
      await tester.tap(find.text('Calculer mon devis'));
      await tester.pumpAndSettle();

      await tester.scrollUntilVisible(find.text('Frais médicaux'), 200, scrollable: find.byType(Scrollable).first);
      expect(find.text('Frais médicaux'), findsOneWidget);
      // Affiché avec une espace insécable : le nombre ne se coupe pas en fin de ligne.
      expect(find.text('Jusqu\'à 30\u00A0000 euros'), findsOneWidget);
      expect(find.text('Jusqu\'à 30 000 euros'), findsNothing, reason: 'espace ordinaire remplacée à l\'affichage');
    });

    const guaranteesNote = '* Ou le montant équivalent dans la devise du pays du sinistre.';

    Future<void> calculate(WidgetTester tester, _Fake fake) async {
      await _show(tester, fake);
      await tester.tap(find.text('Choisir un pays'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('FRANCE'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'JJ/MM/AAAA'), '01011990');
      await tester.pump();
      for (var i = 0; i < 2; i++) {
        await tester.tap(find.text('Choisir une date').first);
        await tester.pumpAndSettle();
        await tester.tap(find.text('Valider'));
        await tester.pumpAndSettle();
      }
      await tester.tap(find.text('Calculer mon devis'));
      await tester.pumpAndSettle();
    }

    testWidgets('note des garanties : affichée sous la rubrique, texte du serveur', (tester) async {
      await calculate(tester, _Fake()..withGuarantees = true..guaranteesNote = guaranteesNote);

      await tester.scrollUntilVisible(find.text(guaranteesNote), 200, scrollable: find.byType(Scrollable).first);
      expect(find.text(guaranteesNote), findsOneWidget);
      expect(tester.getTopLeft(find.text(guaranteesNote)).dy, greaterThan(tester.getTopLeft(find.text('Frais médicaux')).dy), reason: 'sous la rubrique');
    });

    testWidgets('note des garanties : serveur sans note, rien n\'est ajouté', (tester) async {
      await calculate(tester, _Fake()..withGuarantees = true);

      expect(find.textContaining('devise du pays', findRichText: true), findsNothing);
    });

    testWidgets('note des garanties : sans garanties, une note n\'a pas d\'objet', (tester) async {
      await calculate(tester, _Fake()..guaranteesNote = guaranteesNote);

      expect(find.text(guaranteesNote), findsNothing);
    });

    for (final scale in [1.0, 1.3]) {
      for (final size in [const Size(320, 568), const Size(411, 891)]) {
        testWidgets('garanties aux montants longs : ni débordement ni libellé écrasé (${size.width.round()} dp, texte ${(scale * 100).round()} %)', (tester) async {
          tester.view.devicePixelRatio = 1;
          tester.view.physicalSize = size;
          addTearDown(tester.view.reset);
          final quote = MotoQuote.fromJson({
            ..._quoteJson(),
            'guarantees': [
              {
                'label': 'Soins dentaires d\'urgence',
                'details': [
                  {'label': 'Plafond / prise en charge', 'amount': '300 euros (196 787 F CFA), franchise de 100 euros (65 596 F CFA)'},
                ],
              },
              {
                'label': 'Frais médicaux',
                'details': [
                  {'label': 'Plafond', 'amount': 'Jusqu\'à 30 000 euros'},
                  {'label': 'Sans montant', 'amount': null},
                ],
              },
            ],
          });
          await tester.pumpWidget(MaterialApp(
            builder: (context, child) => MediaQuery(data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)), child: child!),
            home: Scaffold(
              body: SingleChildScrollView(
                padding: const EdgeInsets.all(20),
                child: MotoQuoteResult(simulation: quote.simulation, quote: null, busy: false, onSave: () {}, onAccept: () {}),
              ),
            ),
          ));
          await tester.pump();

          expect(tester.takeException(), isNull, reason: 'aucun débordement');
          // Le libellé garde de la largeur : il n'est pas réduit à une colonne de lettres.
          expect(tester.getSize(find.text('Plafond / prise en charge')).width, greaterThan(60));
        });
      }
    }

    testWidgets('erreur du serveur sur un champ : affichée sous ce champ', (tester) async {
      final fake = _Fake()
        ..simulateError = const ApiException(
          kind: ApiErrorKind.validation,
          statusCode: 400,
          code: 'VALIDATION_FAILED',
          field: 'durationDays',
          message: 'La durée ne peut pas dépasser 365 jours (1 an).',
        );
      await _show(tester, fake);

      await tester.tap(find.text('Choisir un pays'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('FRANCE'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'JJ/MM/AAAA'), '01011990');
      await tester.pump();
      for (var i = 0; i < 2; i++) {
        await tester.tap(find.text('Choisir une date').first);
        await tester.pumpAndSettle();
        await tester.tap(find.text('Valider'));
        await tester.pumpAndSettle();
      }
      await tester.tap(find.text('Calculer mon devis'));
      await tester.pumpAndSettle();

      expect(find.text('La durée ne peut pas dépasser 365 jours (1 an).'), findsOneWidget, reason: 'rattachée au champ « retour »');
      expect(find.text('Enregistrer mon devis'), findsNothing);
    });
  });
}

Future<void> _show(WidgetTester tester, _Fake fake) async {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = const Size(411, 1400);
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({'lock_choice_done': true});
  final store = await LocalStore.open();
  final base = MockRepositories.create();
  final repos = Repositories(
    auth: base.auth,
    catalog: base.catalog,
    quotes: base.quotes,
    motoQuotes: fake,
    payments: base.payments,
    contracts: base.contracts,
    claims: base.claims,
    notifications: base.notifications,
    support: base.support,
    engagement: base.engagement,
  );
  final session = SessionController(store: store, repos: repos)..updateUser(MockData.demoUser);
  await tester.pumpWidget(AppScope(
    repos: repos,
    session: session,
    store: store,
    child: MaterialApp(
      // Comme l'application : les sélecteurs de date ont besoin des traductions françaises.
      localizationsDelegates: const [GlobalMaterialLocalizations.delegate, GlobalWidgetsLocalizations.delegate, GlobalCupertinoLocalizations.delegate],
      supportedLocales: const [Locale('fr')],
      locale: const Locale('fr'),
      onGenerateRoute: (s) => MaterialPageRoute<void>(settings: s, builder: (_) => Scaffold(body: Text('page ${s.name}'))),
      home: const VoyageQuotePage(),
    ),
  ));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 100));
}

/// Dépôt de devis de test : il n'implémente que le Voyage et note les appels.
class _Fake implements MotoQuoteRepository {
  final List<VoyageRequest> simulations = [];
  final List<VoyageRequest> created = [];
  ApiException? simulateError;
  bool withGuarantees = false;
  String? guaranteesNote;

  @override
  Future<VoyageForm> getVoyageForm() async {
    final today = DateTime.now();
    return VoyageForm.fromJson({..._formJson(), 'minDepartureDate': '${today.year.toString().padLeft(4, '0')}-${today.month.toString().padLeft(2, '0')}-${today.day.toString().padLeft(2, '0')}'});
  }

  @override
  Future<VoyageSimulation> simulateVoyage(VoyageRequest request) async {
    final err = simulateError;
    if (err != null) throw err;
    simulations.add(request);
    return VoyageSimulation.fromJson({..._simJson(), if (withGuarantees) 'guarantees': _guaranteesJson(), if (guaranteesNote != null) 'guaranteesNote': guaranteesNote});
  }

  @override
  Future<MotoQuote> createVoyage(VoyageRequest request) async {
    created.add(request);
    return MotoQuote.fromJson(_quoteJson());
  }

  @override
  Future<MotoQuote> accept(String reference) async => MotoQuote.fromJson({..._quoteJson(), 'status': 'ACCEPTE', 'canAccept': false});

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError('${invocation.memberName}');
}
