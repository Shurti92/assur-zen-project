import 'dart:convert';

import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/data/api/api_moto_quote_repository.dart';
import 'package:assurzen_mobile/data/models/moto_quote.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

http.Response _json(int status, Object? body) => http.Response.bytes(
      utf8.encode(jsonEncode(body)),
      status,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );

const _premium = {'netPremium': 6447, 'taxes': 925, 'accessories': 500, 'fga': 128, 'total': 8000, 'currency': 'XOF'};
const _guarantees = [
  {
    'label': 'Responsabilité civile',
    'details': [
      {'label': 'Décès', 'amount': '500 000 F CFA'}, // texte formaté par le serveur (réponse réelle)
      {'label': 'Assistance', 'amount': null},
    ],
  },
  {'label': 'Défense et Recours', 'details': []},
];
final _request = MotoQuoteRequest(
  vehicleType: 'MOTO',
  cylinderCc: 125,
  brand: 'Yamaha',
  model: 'YBR',
  durationMonths: 12,
  effectiveDate: DateTime(2026, 11, 5),
  registrationNumber: ' 1234 AB 01 ',
  chassisNumber: '  ',
);

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

  test('le formulaire vient du serveur', () async {
    final repo = build((req) async => _json(200, {
          'productKey': 'moto',
          'vehicleTypes': ['MOTO', 'TRICYCLE'],
          'durations': [
            {'months': 12, 'total': 8000},
          ],
          'formulaId': 'tiers-simple-moto',
          'formulaName': 'Tiers Simple Moto',
          'minEffectiveDate': '2026-11-03',
          'brands': ['Yamaha', ' Honda ', ''], // vide ignorée, espaces retirés
        }));

    final form = await repo.getForm();
    expect(form.brands, ['Yamaha', 'Honda']);

    expect(calls.single.url.path, endsWith('/public/quotes/moto/form'));
    expect(form.vehicleTypes, ['MOTO', 'TRICYCLE']);
    expect(form.durations.single.total, 8000);
    expect(form.formulaName, 'Tiers Simple Moto');
    expect(form.minEffectiveDate, DateTime(2026, 11, 3));
  });

  test('la simulation envoie le bon corps et lit la prime du serveur', () async {
    final repo = build((req) async => _json(200, {
          'productKey': 'moto',
          'formulaId': 'tiers-simple-moto',
          'formulaName': 'Tiers Simple Moto',
          'vehicleType': 'MOTO',
          'durationMonths': 12,
          'effectiveDate': '2026-11-05',
          'expiryDate': '2027-11-05',
          'premium': _premium,
          'guarantees': _guarantees,
        }));

    final sim = await repo.simulate(_request);

    expect(calls.single.method, 'POST');
    expect(calls.single.url.path, endsWith('/public/quotes/moto/simulate'));
    final body = jsonDecode(calls.single.body) as Map<String, dynamic>;
    expect(body['effectiveDate'], '2026-11-05');
    expect(body['registrationNumber'], '1234 AB 01'); // espaces retirés
    expect(body.containsKey('chassisNumber'), isFalse); // champ vide non envoyé
    expect(body.containsKey('formulaId'), isFalse);
    expect(sim.premium.total, 8000);
    expect(sim.premium.fga, 128);
    expect(sim.guarantees.first.details.first.amount, '500 000 F CFA');
    expect(sim.guarantees.first.details.last.amount, isNull); // montant absent = non communiqué
    expect(sim.guarantees.last.details, isEmpty); // garantie sans détail : valide
    expect(sim.expiryDate, DateTime(2027, 11, 5));
  });

  test('un devis enregistré, sa liste et son acceptation', () async {
    final quote = {
      'reference': 'DEV-2026-000001',
      'status': 'CALCULE',
      'canAccept': true,
      'formulaId': 'tiers-simple-moto',
      'formulaName': 'Tiers Simple Moto',
      'vehicleType': 'MOTO',
      'cylinderCc': 125,
      'brand': 'Yamaha',
      'model': 'YBR',
      'registrationNumber': '1234 AB 01',
      'chassisNumber': null,
      'durationMonths': 12,
      'effectiveDate': '2026-11-05',
      'expiryDate': '2027-11-05',
      'validUntil': '2026-11-05',
      'premium': _premium,
      'guarantees': _guarantees,
      'createdAt': '2026-11-03T15:50:00',
      'acceptedAt': null,
    };
    final repo = build((req) async {
      if (req.url.path.endsWith('/accept')) return _json(200, {...quote, 'status': 'ACCEPTE', 'canAccept': false});
      if (req.method == 'POST') return _json(201, quote);
      return _json(200, {
        'items': [quote],
      });
    });

    final created = await repo.create(_request);
    expect(calls.last.url.path, endsWith('/mobile/quotes/moto'));
    expect(calls.last.headers['Authorization'], 'Bearer jeton');
    expect(created.reference, 'DEV-2026-000001');
    expect(created.status, MotoQuoteStatus.calcule);
    expect(created.canAccept, isTrue);
    expect(created.chassisNumber, isNull);
    expect(created.simulation.premium.total, 8000);

    final list = await repo.list();
    expect(calls.last.url.path, endsWith('/mobile/quotes'));
    expect(list.single.reference, 'DEV-2026-000001');

    final accepted = await repo.accept('DEV-2026-000001');
    expect(calls.last.url.path, endsWith('/mobile/quotes/DEV-2026-000001/accept'));
    expect(accepted.status, MotoQuoteStatus.accepte);
    expect(accepted.canAccept, isFalse);
  });

  test('erreurs : validation avec champ, devis périmé', () async {
    final repo = build((req) async => req.url.path.endsWith('/accept')
        ? _json(409, {'code': 'QUOTE_EXPIRED', 'message': 'Devis périmé'})
        : _json(400, {'code': 'VALIDATION_FAILED', 'message': 'Date invalide', 'field': 'effectiveDate'}));

    await expectLater(repo.simulate(_request), throwsA(isA<ApiException>()));
    await expectLater(repo.accept('DEV-2026-000001'), throwsA(isA<ApiException>()));
  });
}
