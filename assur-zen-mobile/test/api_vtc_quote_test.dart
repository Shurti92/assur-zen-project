import 'dart:convert';

import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/data/api/api_moto_quote_repository.dart';
import 'package:assurzen_mobile/data/models/moto_quote.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

// Contrat de la Gateway : docs/api-contracts/devis-en-ligne.md, « Assurance automobile VTC ».

http.Response _json(int status, Object? body) => http.Response.bytes(
      utf8.encode(jsonEncode(body)),
      status,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );

// Exemple 12 mois du contrat : 181363 + 2024 + 1000 + 1000 + 280 = 185667 (taxes 0, remise 0).
const _vtcPremium = {
  'netPremium': 181363,
  'taxes': 0,
  'accessories': 2024,
  'fga': 280,
  'cedeao': 1000,
  'attestation': 1000,
  'discount': 0,
  'total': 185667,
  'currency': 'XOF',
};

const _vtcGuarantees = [
  {'label': 'Responsabilité civile', 'details': []},
  {'label': 'Défense et Recours', 'details': []},
];

final _request = VtcQuoteRequest(
  fiscalPower: 5,
  energy: 'ESSENCE',
  brand: 'Toyota',
  model: 'Corolla',
  firstRegistrationYear: 2019,
  seats: 4,
  vtcDeclared: false,
  durationMonths: 12,
  effectiveDate: DateTime(2026, 11, 5),
  platform: '  ',
  registrationNumber: ' 1234 AB 01 ',
  chassisNumber: '',
);

Map<String, dynamic> _vtcQuote() => {
      'reference': 'DEV-2026-000004',
      'status': 'CALCULE',
      'canAccept': true,
      'productKey': 'automobile-vtc',
      'formulaId': 'tiers-simple-vtc',
      'formulaName': 'Tiers Simple VTC',
      'cylinderCc': null,
      'brand': 'Toyota',
      'model': 'Corolla',
      'registrationNumber': '1234 AB 01',
      'chassisNumber': null,
      'fiscalPower': 5,
      'energy': 'ESSENCE',
      'seats': 4,
      'firstRegistrationYear': 2019,
      'vtcDeclared': false,
      'platform': 'Yango',
      'discountPercent': null,
      'durationMonths': 12,
      'effectiveDate': '2026-11-05',
      'expiryDate': '2027-11-04',
      'validUntil': '2026-11-05',
      'premium': _vtcPremium,
      'guarantees': _vtcGuarantees,
      'createdAt': '2026-11-03T18:00:00',
      'acceptedAt': null,
    };

void main() {
  late List<http.Request> calls;

  ApiMotoQuoteRepository build(MockClientHandler handler) {
    calls = [];
    return ApiMotoQuoteRepository(ApiClient(
      baseUrl: 'http://gw.test',
      tokenProvider: () async => 'jeton',
      httpClient: MockClient((req) async {
        calls.add(req);
        return handler(req);
      }),
    ));
  }

  test('le formulaire VTC vient du serveur : énergies, durées, marques, plateformes, places', () async {
    final repo = build((req) async => _json(200, {
          'productKey': 'automobile-vtc',
          'energies': ['ESSENCE', 'DIESEL', 'HYBRIDE', 'ELECTRIQUE'],
          'durations': [
            {'months': 1, 'total': 23800},
            {'months': 3, 'total': 54160},
            {'months': 6, 'total': 98732},
            {'months': 12, 'total': 185667},
          ],
          'formulaId': 'tiers-simple-vtc',
          'formulaName': 'Tiers Simple VTC',
          'minEffectiveDate': '2026-11-03',
          'brands': ['Toyota', ' Hyundai ', ''],
          'platforms': ['Yango', 'Uber', 'Autre'],
          'minFiscalPower': 3,
          'minSeats': 4,
          'maxSeats': 8,
        }));

    final form = await repo.getVtcForm();

    expect(calls.single.url.path, endsWith('/public/quotes/vtc/form'));
    expect(form.energies, ['ESSENCE', 'DIESEL', 'HYBRIDE', 'ELECTRIQUE']);
    expect(form.durations.map((d) => d.months), [1, 3, 6, 12]);
    expect(form.brands, ['Toyota', 'Hyundai']); // vide ignorée, espaces retirés
    expect(form.platforms, ['Yango', 'Uber', 'Autre']);
    expect(form.minFiscalPower, 3);
    expect(form.seatOptions, [4, 5, 6, 7, 8]);
    expect(form.minEffectiveDate, DateTime(2026, 11, 3));
  });

  test('brands et platforms vides ou absents : saisie libre, aucun plantage', () async {
    final repo = build((req) async => _json(200, {
          'energies': ['ESSENCE'],
          'durations': [
            {'months': 12, 'total': 185667},
          ],
          'formulaId': 'tiers-simple-vtc',
          'formulaName': 'Tiers Simple VTC',
        }));

    final form = await repo.getVtcForm();

    expect(form.brands, isEmpty);
    expect(form.platforms, isEmpty);
    expect(form.seatOptions, isEmpty); // bornes absentes : pas de puces de places
  });

  test('la simulation envoie le bon corps (vtcDeclared booléen, aucune remise) et lit toutes les lignes du prix', () async {
    final repo = build((req) async => _json(200, {
          'productKey': 'automobile-vtc',
          'formulaId': 'tiers-simple-vtc',
          'formulaName': 'Tiers Simple VTC',
          'durationMonths': 12,
          'effectiveDate': '2026-11-05',
          'expiryDate': '2027-11-04',
          'premium': _vtcPremium,
          'guarantees': _vtcGuarantees,
        }));

    final sim = await repo.simulateVtc(_request);

    expect(calls.single.method, 'POST');
    expect(calls.single.url.path, endsWith('/public/quotes/vtc/simulate'));
    final body = jsonDecode(calls.single.body) as Map<String, dynamic>;
    expect(body['vtcDeclared'], isFalse); // « non » est bien envoyé (champ obligatoire)
    expect(body['fiscalPower'], 5);
    expect(body['seats'], 4);
    expect(body['firstRegistrationYear'], 2019);
    expect(body['energy'], 'ESSENCE');
    expect(body['effectiveDate'], '2026-11-05');
    expect(body['registrationNumber'], '1234 AB 01');
    expect(body.containsKey('platform'), isFalse); // plateforme vide : non envoyée
    expect(body.containsKey('chassisNumber'), isFalse);
    expect(body.containsKey('discountPercent'), isFalse); // aucune remise côté client
    final p = sim.premium;
    expect(p.total, 185667);
    expect(p.cedeao, 1000);
    expect(p.attestation, 1000);
    expect(p.discount, 0);
    // total = prime nette - remise + taxes + accessoires + FGA + CEDEAO + attestation
    expect(p.netPremium - p.discount + p.taxes + p.accessories + p.fga + p.cedeao + p.attestation, p.total);
    expect(sim.guarantees.map((g) => g.label), ['Responsabilité civile', 'Défense et Recours']);
  });

  test('un devis VTC enregistré : champs propres au VTC, puis acceptation sans les perdre', () async {
    final repo = build((req) async {
      if (req.url.path.endsWith('/accept')) return _json(200, {..._vtcQuote(), 'status': 'ACCEPTE', 'canAccept': false});
      return _json(201, _vtcQuote());
    });

    final created = await repo.createVtc(_request);

    expect(calls.single.url.path, endsWith('/mobile/quotes/vtc'));
    expect(calls.single.headers['Authorization'], 'Bearer jeton');
    expect(created.isVtc, isTrue);
    expect(created.fiscalPower, 5);
    expect(created.energy, 'ESSENCE');
    expect(created.seats, 4);
    expect(created.firstRegistrationYear, 2019);
    expect(created.vtcDeclared, isFalse);
    expect(created.platform, 'Yango');
    expect(created.discountPercent, isNull);
    expect(created.cylinderCc, 0); // null côté serveur : 0, non affiché

    final accepted = await repo.accept(created.reference);
    expect(calls.last.url.path, endsWith('/mobile/quotes/DEV-2026-000004/accept'));
    expect(accepted.status, MotoQuoteStatus.accepte);
    expect(accepted.isVtc, isTrue);
  });

  test('copyAccepted garde tous les champs, y compris ceux du VTC', () {
    final q = MotoQuote.fromJson(_vtcQuote());

    final a = q.copyAccepted(DateTime(2026, 11, 4));

    expect(a.status, MotoQuoteStatus.accepte);
    expect(a.canAccept, isFalse);
    expect(a.acceptedAt, DateTime(2026, 11, 4));
    expect(a.reference, q.reference);
    expect(a.fiscalPower, 5);
    expect(a.energy, 'ESSENCE');
    expect(a.seats, 4);
    expect(a.platform, 'Yango');
    expect(a.simulation.premium.cedeao, 1000);
  });

  test('la liste mélange moto et VTC : un devis moto n\'est pas VTC et ses lignes VTC valent 0', () async {
    final moto = {
      'reference': 'DEV-2026-000002',
      'status': 'ACCEPTE',
      'canAccept': false,
      'productKey': 'moto',
      'formulaId': 'tiers-simple-moto',
      'formulaName': 'Tiers Simple Moto',
      'vehicleType': 'MOTO',
      'cylinderCc': 125,
      'brand': 'Yamaha',
      'model': 'YBR',
      'registrationNumber': '1234 AB 01',
      'fiscalPower': null,
      'energy': null,
      'seats': null,
      'vtcDeclared': null,
      'platform': null,
      'discountPercent': null,
      'durationMonths': 12,
      'effectiveDate': '2026-11-05',
      'expiryDate': '2027-11-04',
      'premium': {'netPremium': 6447, 'taxes': 925, 'accessories': 500, 'fga': 128, 'total': 8000, 'currency': 'XOF'},
      'guarantees': [],
    };
    final repo = build((req) async => _json(200, {
          'items': [_vtcQuote(), moto],
        }));

    final list = await repo.list();

    expect(list.map((q) => q.isVtc), [true, false]);
    final m = list.last;
    expect(m.cylinderCc, 125);
    expect(m.fiscalPower, isNull);
    expect(m.simulation.premium.cedeao, 0); // absent côté serveur : 0, ligne masquée dans l'écran
    expect(m.simulation.premium.discount, 0);
    expect(m.simulation.premium.total, 8000);
  });

  test('erreur de validation : le champ fautif remonte (seats : aucun tarif pour cette puissance)', () async {
    final repo = build((req) async =>
        _json(400, {'code': 'VALIDATION_FAILED', 'message': 'Aucun tarif pour ce nombre de places', 'field': 'seats'}));

    await expectLater(
      repo.simulateVtc(_request),
      throwsA(isA<ApiException>().having((e) => e.field, 'field', 'seats')),
    );
  });
}
