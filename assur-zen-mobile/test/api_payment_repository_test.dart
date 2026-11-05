import 'dart:convert';

import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/network/endpoints.dart';
import 'package:assurzen_mobile/data/api/api_payment_repository.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

http.Response _json(int status, Object? body) => http.Response.bytes(
      utf8.encode(jsonEncode(body)),
      status,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );

Map<String, dynamic> _attempt(String status, {String? failure, bool simulated = true}) => {
      'reference': 'PAY-2026-000007',
      'status': status,
      'failureReason': failure,
      'receiptUrl': null,
      'payableReference': 'RN-655-20261114',
      'amount': 25000,
      'methodId': 'wave',
      'simulated': simulated,
      'redirectUrl': null,
    };

void main() {
  late List<http.Request> calls;

  ApiPaymentRepository build(MockClientHandler handler) {
    calls = [];
    return ApiPaymentRepository(
      ApiClient(
        baseUrl: 'http://gw.test',
        tokenProvider: () async => 'tok',
        httpClient: MockClient((req) async {
          calls.add(req);
          return handler(req);
        }),
      ),
      newKey: () => 'cle-fixe',
    );
  }

  test('moyens : seuls les moyens actifs du serveur, signalés simulés', () async {
    final repo = build((req) async => _json(200, {
          'items': [
            {'id': 'wave', 'name': 'Wave', 'subtitle': 'Validation dans Wave', 'needsPhoneNumber': true, 'enabled': true, 'simulated': true},
            {'id': 'carte', 'name': 'Carte', 'subtitle': 'Bientot disponible', 'needsPhoneNumber': false, 'enabled': false, 'simulated': false},
          ],
        }));

    final methods = await repo.getMethods();

    expect(calls.single.url.path, Endpoints.paymentMethods);
    expect(methods.map((m) => m.id), ['wave']);
    expect(methods.single.simulated, isTrue);
    expect(methods.single.needsPhoneNumber, isTrue);
  });

  test('aucun moyen actif : liste vide', () async {
    final repo = build((req) async => _json(200, {
          'items': [
            {'id': 'wave', 'name': 'Wave', 'subtitle': 'Bientot disponible', 'enabled': false},
          ],
        }));
    expect(await repo.getMethods(), isEmpty);
  });

  test('initier : référence payable, moyen, montant de contrôle, clé d’idempotence', () async {
    final repo = build((req) async => _json(201, _attempt('PENDING')));

    final a = await repo.initiate(payableReference: 'RN-655-20261114', amount: 25000, methodId: 'wave', phone: '0701020304');

    expect(calls.single.url.path, Endpoints.payments);
    expect(calls.single.headers['Authorization'], 'Bearer tok');
    expect(jsonDecode(calls.single.body), {
      'payableReference': 'RN-655-20261114',
      'methodId': 'wave',
      'amount': 25000,
      'phone': '0701020304',
      'idempotencyKey': 'cle-fixe',
    });
    expect(a.reference, 'PAY-2026-000007');
    expect(a.status, PaymentStatus.pending);
    expect(a.simulated, isTrue);
    expect(a.receiptUrl, isNull);
  });

  test('statut : CONFIRMED, FAILED avec motif', () async {
    var status = 'CONFIRMED';
    final repo = build((req) async => _json(200, _attempt(status, failure: status == 'FAILED' ? 'Solde insuffisant (echec simule)' : null)));

    expect((await repo.status('PAY-2026-000007')).status, PaymentStatus.confirmed);
    expect(calls.single.url.path, Endpoints.payment('PAY-2026-000007'));

    status = 'FAILED';
    final failed = await repo.status('PAY-2026-000007');
    expect(failed.status, PaymentStatus.failed);
    expect(failed.failureReason, 'Solde insuffisant (echec simule)');
  });

  test('statut inconnu : traité comme en attente, jamais comme payé', () async {
    final repo = build((req) async => _json(200, _attempt('QUELQUE_CHOSE')));
    expect((await repo.status('PAY-2026-000007')).status, PaymentStatus.pending);
  });

  test('montant différent : 409 AMOUNT_MISMATCH', () async {
    final repo = build((req) async => _json(409, {'code': 'AMOUNT_MISMATCH', 'message': 'Montant différent.'}));
    await expectLater(
      repo.initiate(payableReference: 'RN-655-20261114', amount: 1, methodId: 'wave', phone: '0701020304'),
      throwsA(isA<ApiException>().having((e) => e.code, 'code', 'AMOUNT_MISMATCH')),
    );
  });

  group("paiement d'un devis accepté", () {
    Map<String, dynamic> quotePayment({String status = 'PENDING'}) => {
          ..._attempt(status, simulated: false),
          'payableReference': 'DV-DEV-2026-000019',
          'amount': 8000,
          'redirectUrl': 'https://pay.wave.com/c/abc',
          'expiresAt': '2026-11-05T05:30:00Z',
        };

    test('POST /mobile/quotes/{référence}/convert : moyen, téléphone, montant de contrôle, clé', () async {
      final repo = build((req) async => _json(201, quotePayment()));

      final a = await repo.convertQuote(quoteReference: 'DEV-2026-000019', amount: 8000, methodId: 'wave', phone: '0701020304');

      expect(calls.single.method, 'POST');
      expect(calls.single.url.path, endsWith('/mobile/quotes/DEV-2026-000019/convert'));
      expect(calls.single.headers['Authorization'], 'Bearer tok');
      expect(jsonDecode(calls.single.body), {
        'methodId': 'wave',
        'amount': 8000,
        'phone': '0701020304',
        'idempotencyKey': 'cle-fixe',
      });
      expect(a.reference, 'PAY-2026-000007');
      expect(a.status, PaymentStatus.pending);
      expect(a.redirectUrl, 'https://pay.wave.com/c/abc');
      expect(a.expiresAt, DateTime.utc(2026, 11, 5, 5, 30));
    });

    test('devis payé ou non accepté : le message du serveur est conservé', () async {
      for (final code in ['QUOTE_NOT_ACCEPTED', 'QUOTE_EXPIRED', 'QUOTE_ALREADY_PAID', 'METHOD_DISABLED', 'AMOUNT_MISMATCH']) {
        final repo = build((req) async => _json(409, {'code': code, 'message': 'Message $code'}));
        await expectLater(
          repo.convertQuote(quoteReference: 'DEV-2026-000019', amount: 8000, methodId: 'wave', phone: '0701020304'),
          throwsA(isA<ApiException>().having((e) => e.code, 'code', code).having((e) => e.message, 'message', 'Message $code')),
          reason: code,
        );
      }
    });

    test('paiement déjà en cours : la référence PAY-… à suivre est dans le message', () async {
      final repo = build((req) async => _json(409, {
            'code': 'PAYMENT_IN_PROGRESS',
            'message': 'Un paiement est déjà en cours pour ce devis : PAY-2026-000031.',
          }));

      try {
        await repo.convertQuote(quoteReference: 'DEV-2026-000019', amount: 8000, methodId: 'wave', phone: '0701020304');
        fail('une ApiException était attendue');
      } on ApiException catch (e) {
        expect(e.code, 'PAYMENT_IN_PROGRESS');
        expect(paymentReferenceIn(e.message), 'PAY-2026-000031');
      }
    });

    test('Jeko injoignable : 502, message générique invitant à réessayer, aucun paiement supposé', () async {
      final repo = build((req) async => _json(502, {'code': 'PROVIDER_UNAVAILABLE', 'message': 'Réessayez.'}));
      await expectLater(
        repo.convertQuote(quoteReference: 'DEV-2026-000019', amount: 8000, methodId: 'wave', phone: '0701020304'),
        throwsA(isA<ApiException>()
            .having((e) => e.statusCode, 'statusCode', 502)
            .having((e) => e.kind, 'kind', ApiErrorKind.server)
            .having((e) => e.message, 'message', contains('Réessayez'))),
      );
    });

    test('référence de paiement : extraite seulement si elle a le bon format', () {
      expect(paymentReferenceIn('voir PAY-2026-000031 svp'), 'PAY-2026-000031');
      expect(paymentReferenceIn('PAY-26-1'), isNull);
      expect(paymentReferenceIn('rien'), isNull);
      expect(paymentReferenceIn(null), isNull);
    });
  });

  test('trop de tentatives : 429 avec délai', () async {
    final repo = build((req) async => _json(429, {'code': 'RATE_LIMITED', 'message': 'Trop de tentatives.', 'retryAfterSeconds': 1800}));
    await expectLater(
      repo.initiate(payableReference: 'RN-655-20261114', amount: 25000, methodId: 'wave', phone: '0701020304'),
      throwsA(isA<ApiException>().having((e) => e.retryAfterSeconds, 'retryAfter', 1800)),
    );
  });
}
