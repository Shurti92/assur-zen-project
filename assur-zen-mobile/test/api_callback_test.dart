import 'dart:convert';

import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/network/endpoints.dart';
import 'package:assurzen_mobile/data/api/api_repositories.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

http.Response _json(int status, Object? body) => http.Response.bytes(
      utf8.encode(jsonEncode(body)),
      status,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );

void main() {
  late List<http.Request> calls;

  ApiQuoteRepository build(MockClientHandler handler) {
    calls = [];
    return ApiQuoteRepository(ApiClient(
      baseUrl: 'http://gw.test',
      tokenProvider: () async => null,
      httpClient: MockClient((req) async {
        calls.add(req);
        return handler(req);
      }),
    ));
  }

  test('rappel : numéro, créneau et produit envoyés, référence reçue, aucun délai annoncé', () async {
    final repo = build((req) async => _json(201, {'reference': 'CB-2026-000001', 'expectedWithinMinutes': null}));

    final r = await repo.requestCallback(phone: '0700000000', slot: 'MORNING', productId: 'moto');

    expect(calls.single.url.path, Endpoints.callbacks);
    expect(calls.single.url.path, endsWith('/public/callbacks'));
    expect(jsonDecode(calls.single.body), {'phone': '0700000000', 'slot': 'MORNING', 'productId': 'moto'});
    expect(r.reference, 'CB-2026-000001');
    expect(r.expectedWithinMinutes, isNull, reason: 'aucun engagement « sous 30 minutes » sans donnée serveur');
  });

  test('rappel : délai annoncé seulement quand le serveur le fournit', () async {
    final repo = build((req) async => _json(201, {'reference': 'CB-2026-000002', 'expectedWithinMinutes': 45}));
    final r = await repo.requestCallback(phone: '0700000000', slot: 'ASAP');
    expect(r.expectedWithinMinutes, 45);
    expect(jsonDecode(calls.single.body).containsKey('productId'), isFalse);
  });

  test('rappel : trop de demandes, 429 avec délai', () async {
    final repo = build((req) async => _json(429, {'code': 'RATE_LIMITED', 'message': 'Trop de demandes.', 'retryAfterSeconds': 1200}));
    await expectLater(
      repo.requestCallback(phone: '0700000000', slot: 'ASAP'),
      throwsA(isA<ApiException>().having((e) => e.retryAfterSeconds, 'retryAfter', 1200)),
    );
  });

  test('rappel : numéro invalide, erreur de validation sur le champ phone', () async {
    final repo = build((req) async => _json(400, {'code': 'VALIDATION_FAILED', 'message': 'phone: invalide', 'field': 'phone'}));
    await expectLater(
      repo.requestCallback(phone: '123', slot: 'ASAP'),
      throwsA(isA<ApiException>().having((e) => e.field, 'field', 'phone')),
    );
  });
}
