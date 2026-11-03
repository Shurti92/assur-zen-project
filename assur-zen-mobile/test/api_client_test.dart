import 'dart:convert';

import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/network/endpoints.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

/// Réponse JSON UTF-8 (le client lit le corps en octets).
http.Response _json(int status, Object body) =>
    http.Response.bytes(utf8.encode(jsonEncode(body)), status, headers: {'content-type': 'application/json; charset=utf-8'});

void main() {
  group('ApiClient : renouvellement du jeton', () {
    late List<http.Request> calls;
    late String access;
    late String refresh;
    late List<(String, String)> saved;
    var unauthorized = 0;

    ApiClient build(MockClientHandler handler, {bool withRefresh = true}) {
      return ApiClient(
        baseUrl: 'http://gw.test',
        tokenProvider: () async => access,
        refreshTokenProvider: withRefresh ? () async => refresh : null,
        onTokensRefreshed: withRefresh
            ? (a, r) async {
                access = a;
                refresh = r;
                saved.add((a, r));
              }
            : null,
        onUnauthorized: () => unauthorized++,
        httpClient: MockClient((req) async {
          calls.add(req);
          return handler(req);
        }),
      );
    }

    setUp(() {
      calls = [];
      saved = [];
      unauthorized = 0;
      access = 'old-access';
      refresh = 'old-refresh';
    });

    test('un 401 renouvelle le jeton une fois puis rejoue l’appel', () async {
      final client = build((req) async {
        if (req.url.path == Endpoints.refresh) {
          return _json(200, {'accessToken': 'new-access', 'refreshToken': 'new-refresh', 'tokenType': 'Bearer', 'expiresIn': 3600});
        }
        return req.headers['Authorization'] == 'Bearer new-access' ? _json(200, {'ok': true}) : _json(401, {'code': 'UNAUTHORIZED'});
      });

      final data = await client.get('/api-assur-zen/anything');

      expect(data, {'ok': true});
      expect(saved, [('new-access', 'new-refresh')]);
      expect(calls.where((c) => c.url.path == Endpoints.refresh).length, 1);
      expect(unauthorized, 0);
    });

    test('le corps du refresh est le refresh token courant', () async {
      final client = build((req) async {
        if (req.url.path == Endpoints.refresh) {
          expect(jsonDecode(req.body), {'refreshToken': 'old-refresh'});
          return _json(200, {'accessToken': 'a2', 'refreshToken': 'r2'});
        }
        return req.headers['Authorization'] == 'Bearer a2' ? _json(200, {}) : _json(401, {});
      });
      await client.get('/api-assur-zen/anything');
    });

    test('deux appels simultanés en 401 ne consomment qu’un seul refresh', () async {
      final client = build((req) async {
        if (req.url.path == Endpoints.refresh) {
          return _json(200, {'accessToken': 'a2', 'refreshToken': 'r2'});
        }
        return req.headers['Authorization'] == 'Bearer a2' ? _json(200, {'ok': 1}) : _json(401, {});
      });

      final results = await Future.wait([client.get('/api-assur-zen/a'), client.get('/api-assur-zen/b')]);

      expect(results, everyElement({'ok': 1}));
      expect(calls.where((c) => c.url.path == Endpoints.refresh).length, 1);
    });

    test('refresh refusé : écran de PIN (onUnauthorized) et erreur « session expirée »', () async {
      final client = build((req) async => _json(401, {'code': 'INVALID_REFRESH_TOKEN', 'message': 'Jeton invalide'}));

      await expectLater(
        client.get('/api-assur-zen/anything'),
        throwsA(isA<ApiException>().having((e) => e.kind, 'kind', ApiErrorKind.unauthorized)),
      );
      expect(unauthorized, 1);
      expect(saved, isEmpty);
    });

    test('sans refresh configuré : 401 → écran de PIN directement', () async {
      final client = build((req) async => _json(401, {}), withRefresh: false);
      await expectLater(client.get('/api-assur-zen/anything'), throwsA(isA<ApiException>()));
      expect(unauthorized, 1);
      expect(calls.length, 1);
    });

    test('un 401 sur le login n’essaie pas de renouveler et garde le message du serveur', () async {
      final client = build((req) async => _json(401, {
            'error': 'INVALID_CREDENTIALS',
            'message': 'Identifiants invalides',
            'remainingAttempts': 4,
          }));

      await expectLater(
        client.post(Endpoints.login, body: {'username': '0500000009', 'password': '000000'}),
        throwsA(isA<ApiException>()
            .having((e) => e.code, 'code', 'INVALID_CREDENTIALS')
            .having((e) => e.message, 'message', 'Identifiants invalides')
            .having((e) => e.statusCode, 'statusCode', 401)),
      );
      expect(calls.length, 1);
      expect(unauthorized, 0);
    });
  });

  group('ApiClient : en-têtes et erreurs', () {
    test('aucun jeton envoyé sur les routes publiques d’authentification', () async {
      late http.Request seen;
      final client = ApiClient(
        baseUrl: 'http://gw.test',
        tokenProvider: () async => 'stale-token',
        httpClient: MockClient((req) async {
          seen = req;
          return _json(200, {'otpRequired': true});
        }),
      );

      await client.post(Endpoints.login, body: {'username': 'x', 'password': 'y'});
      expect(seen.headers.containsKey('Authorization'), isFalse);

      await client.get(Endpoints.me);
      expect(seen.headers['Authorization'], 'Bearer stale-token');
    });

    test('VALIDATION_FAILED sans field : le champ est déduit de « champ: raison »', () async {
      final client = ApiClient(
        baseUrl: 'http://gw.test',
        tokenProvider: () async => null,
        httpClient: MockClient((req) async => _json(400, {'code': 'VALIDATION_FAILED', 'message': 'challengeToken: ne doit pas être vide'})),
      );

      await expectLater(
        client.post(Endpoints.loginOtpVerify, body: {}),
        throwsA(isA<ApiException>()
            .having((e) => e.kind, 'kind', ApiErrorKind.validation)
            .having((e) => e.field, 'field', 'challengeToken')
            .having((e) => e.message, 'message', 'Ne doit pas être vide')),
      );
    });

    test('429 : l’attente est lue dans remainingSeconds', () async {
      final client = ApiClient(
        baseUrl: 'http://gw.test',
        tokenProvider: () async => null,
        httpClient: MockClient((req) async => _json(429, {
              'code': 'OTP_RESEND_TOO_SOON',
              'message': 'Veuillez patienter 28 seconde(s) avant de redemander un code',
              'remainingSeconds': 28,
            })),
      );

      await expectLater(
        client.post(Endpoints.login, body: {}),
        throwsA(isA<ApiException>()
            .having((e) => e.code, 'code', 'OTP_RESEND_TOO_SOON')
            .having((e) => e.retryAfterSeconds, 'retryAfterSeconds', 28)),
      );
    });
  });
}
