import 'dart:convert';

import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/network/endpoints.dart';
import 'package:assurzen_mobile/data/api/api_auth_repository.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:assurzen_mobile/data/repositories/repositories.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

http.Response _json(int status, Object? body) => http.Response.bytes(
      body == null ? const [] : utf8.encode(jsonEncode(body)),
      status,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );

const _tokens = {'accessToken': 'acc', 'refreshToken': 'ref', 'tokenType': 'Bearer', 'expiresIn': 3600};
const _user = {'id': '77', 'fullName': 'Awa Koné', 'phone': '0700000002', 'email': null, 'role': 'CLIENT'};

void main() {
  late List<http.Request> calls;

  ApiAuthRepository build(MockClientHandler handler) {
    calls = [];
    final client = ApiClient(
      baseUrl: 'http://gw.test',
      tokenProvider: () async => 'stale-token',
      httpClient: MockClient((req) async {
        calls.add(req);
        return handler(req);
      }),
    );
    return ApiAuthRepository(client, readRefreshToken: () async => 'ref', saveTokens: (_, __) async {});
  }

  group('Inscription (lot 1)', () {
    test('envoi du code : purpose REGISTER, sans jeton', () async {
      final repo = build((req) async => _json(204, null));

      await repo.requestOtp('0700000002', purpose: OtpPurpose.register);

      expect(calls.single.url.path, Endpoints.mobileOtpRequest);
      expect(jsonDecode(calls.single.body), {'phone': '0700000002', 'purpose': 'REGISTER'});
      expect(calls.single.headers.containsKey('Authorization'), isFalse);
    });

    test('vérification : renvoie le jeton de vérification', () async {
      final repo = build((req) async => _json(200, {'verificationToken': 'vt-123', 'expiresInSeconds': 600}));

      final token = await repo.verifyOtp(phone: '0700000002', code: '123456');

      expect(token, 'vt-123');
      expect(jsonDecode(calls.single.body), {'phone': '0700000002', 'code': '123456', 'purpose': 'REGISTER'});
    });

    test('vérification sans jeton dans la réponse : erreur serveur', () async {
      final repo = build((req) async => _json(200, {'expiresInSeconds': 600}));
      await expectLater(repo.verifyOtp(phone: '0700000002', code: '123456'), throwsA(isA<ApiException>()));
    });

    test('inscription : corps, session, et contrats retrouvés', () async {
      final repo = build((req) async =>
          _json(201, {'user': _user, 'tokens': _tokens, 'linkedInsured': true, 'linkedCount': 2}));

      final s = await repo.register(
        fullName: '  Awa Koné ',
        phone: '0700000002',
        pin: '735193',
        verificationToken: 'vt-123',
      );

      expect(jsonDecode(calls.single.body), {
        'fullName': 'Awa Koné',
        'phone': '0700000002',
        'pin': '735193',
        'verificationToken': 'vt-123',
      });
      expect(calls.single.url.path, Endpoints.mobileRegister);
      expect(s.token, 'acc');
      expect(s.refreshToken, 'ref');
      expect(s.user.phone, '0700000002');
      expect(s.linkedCount, 2);
    });

    test('inscription sans assuré existant : linkedCount = 0', () async {
      final repo = build((req) async => _json(201, {'user': _user, 'tokens': _tokens, 'linkedInsured': false}));
      final s = await repo.register(fullName: 'Awa Koné', phone: '0700000002', pin: '735193', verificationToken: 'vt');
      expect(s.linkedCount, 0);
    });

    test('numéro déjà inscrit : 409 avec le message du serveur', () async {
      final repo = build((req) async => _json(409, {
            'code': 'PHONE_ALREADY_REGISTERED',
            'message': 'Un compte existe déjà pour ce numéro. Connectez-vous.',
          }));

      await expectLater(
        repo.requestOtp('0700000002', purpose: OtpPurpose.register),
        throwsA(isA<ApiException>()
            .having((e) => e.code, 'code', 'PHONE_ALREADY_REGISTERED')
            .having((e) => e.statusCode, 'status', 409)
            .having((e) => e.message, 'message', contains('Connectez-vous'))),
      );
    });

    test('PIN trop simple : 400 avec field = pin', () async {
      final repo = build((req) async =>
          _json(400, {'code': 'PIN_TOO_WEAK', 'field': 'pin', 'message': 'Ce code PIN est trop simple.'}));

      await expectLater(
        repo.register(fullName: 'Awa Koné', phone: '0700000002', pin: '111111', verificationToken: 'vt'),
        throwsA(isA<ApiException>()
            .having((e) => e.kind, 'kind', ApiErrorKind.validation)
            .having((e) => e.field, 'field', 'pin')),
      );
    });

    test('quota de codes : 429 avec le délai', () async {
      final repo = build((req) async => _json(429, {
            'code': 'OTP_QUOTA_EXCEEDED',
            'message': 'Trop de demandes de code. Réessayez plus tard.',
            'retryAfterSeconds': 1800,
          }));

      await expectLater(
        repo.requestOtp('0700000002', purpose: OtpPurpose.register),
        throwsA(isA<ApiException>().having((e) => e.retryAfterSeconds, 'retryAfterSeconds', 1800)),
      );
    });
  });

  group('PIN et compte (lot 1)', () {
    test('PIN oublié : code envoyé avec purpose RESET_PIN', () async {
      final repo = build((req) async => _json(204, null));

      await repo.requestOtp('0700000002', purpose: OtpPurpose.resetPin);

      expect(calls.single.url.path, Endpoints.mobileOtpRequest);
      expect(jsonDecode(calls.single.body), {'phone': '0700000002', 'purpose': 'RESET_PIN'});
    });

    test('nouveau PIN : téléphone, code et newPin, sans jeton', () async {
      final repo = build((req) async => _json(204, null));

      await repo.resetPin(phone: '0700000002', code: '123456', newPin: '715304');

      expect(calls.single.url.path, Endpoints.mobilePinReset);
      expect(jsonDecode(calls.single.body), {'phone': '0700000002', 'code': '123456', 'newPin': '715304'});
      expect(calls.single.headers.containsKey('Authorization'), isFalse);
    });

    test('nouveau PIN identique à l’ancien : erreur PIN_REUSED', () async {
      final repo = build((req) async => _json(400, {'code': 'PIN_REUSED', 'message': 'Choisissez un autre PIN.'}));

      await expectLater(
        repo.resetPin(phone: '0700000002', code: '123456', newPin: '735193'),
        throwsA(isA<ApiException>().having((e) => e.code, 'code', 'PIN_REUSED')),
      );
    });

    test('vérification du PIN : envoyée avec le jeton de session', () async {
      final repo = build((req) async => _json(204, null));

      await repo.verifyPin('735193');

      expect(calls.single.url.path, Endpoints.mobilePinVerify);
      expect(jsonDecode(calls.single.body), {'pin': '735193'});
      expect(calls.single.headers['Authorization'], 'Bearer stale-token');
    });

    test('mauvais PIN : erreur métier, sans renouvellement de jeton ni écran de PIN', () async {
      var unauthorized = 0;
      calls = [];
      final client = ApiClient(
        baseUrl: 'http://gw.test',
        tokenProvider: () async => 'tok',
        refreshTokenProvider: () async => 'ref',
        onTokensRefreshed: (_, __) async {},
        onUnauthorized: () => unauthorized++,
        httpClient: MockClient((req) async {
          calls.add(req);
          return _json(401, {
            'code': 'INVALID_CREDENTIALS',
            'error': 'INVALID_CREDENTIALS',
            'message': 'Identifiants invalides',
            'remainingAttempts': 4,
          });
        }),
      );
      final repo = ApiAuthRepository(client, readRefreshToken: () async => 'ref', saveTokens: (_, __) async {});

      await expectLater(
        repo.verifyPin('000000'),
        throwsA(isA<ApiException>()
            .having((e) => e.code, 'code', 'INVALID_CREDENTIALS')
            .having((e) => e.kind, 'kind', ApiErrorKind.business)),
      );
      expect(calls.length, 1, reason: 'aucun appel /auth/refresh');
      expect(unauthorized, 0);
    });

    test('jeton expiré sur la vérification du PIN : renouvelé puis rejoué', () async {
      var access = 'old';
      calls = [];
      final client = ApiClient(
        baseUrl: 'http://gw.test',
        tokenProvider: () async => access,
        refreshTokenProvider: () async => 'ref',
        onTokensRefreshed: (a, _) async => access = a,
        httpClient: MockClient((req) async {
          calls.add(req);
          if (req.url.path == Endpoints.refresh) return _json(200, {'accessToken': 'new', 'refreshToken': 'r2'});
          return req.headers['Authorization'] == 'Bearer new' ? _json(204, null) : _json(401, {'code': 'UNAUTHORIZED', 'message': 'Jeton invalide'});
        }),
      );
      final repo = ApiAuthRepository(client, readRefreshToken: () async => 'ref', saveTokens: (_, __) async {});

      await repo.verifyPin('735193');

      expect(calls.map((c) => c.url.path), [Endpoints.mobilePinVerify, Endpoints.refresh, Endpoints.mobilePinVerify]);
    });

    test('suppression du compte : PIN envoyé, référence renvoyée', () async {
      final repo = build((req) async => _json(202, {'reference': 'DEL-2026-000123'}));

      final ref = await repo.requestAccountDeletion(pin: '735193');

      expect(ref, 'DEL-2026-000123');
      expect(calls.single.url.path, Endpoints.mobileDeleteRequest);
      expect(jsonDecode(calls.single.body), {'pin': '735193'});
    });

    test('suppression avec un mauvais PIN : 401 INVALID_CREDENTIALS affiché tel quel', () async {
      final repo = build((req) async => _json(401, {'code': 'INVALID_CREDENTIALS', 'message': 'Code PIN incorrect.'}));

      await expectLater(
        repo.requestAccountDeletion(pin: '000000'),
        throwsA(isA<ApiException>().having((e) => e.message, 'message', 'Code PIN incorrect.')),
      );
    });
  });

  group('Connexion', () {
    test('défi de double authentification', () async {
      final repo = build((req) async =>
          _json(200, {'otpRequired': true, 'challengeToken': 'ch-1', 'maskedPhone': '05******01', 'expiresInSeconds': 300}));

      final result = await repo.login(phone: '0500000009', pin: '258147');

      expect(result, isA<LoginOtpChallenge>());
      expect((result as LoginOtpChallenge).challengeToken, 'ch-1');
      expect(result.maskedPhone, '05******01');
    });

    test('compte client : session directe, sans étape SMS', () async {
      final repo = build((req) async => _json(200, {'user': _user, 'tokens': _tokens}));

      final result = await repo.login(phone: '0700000002', pin: '735193');

      expect(result, isA<LoginSuccess>());
      expect((result as LoginSuccess).session.user.fullName, 'Awa Koné');
      expect(calls.single.headers.containsKey('Authorization'), isFalse);
    });
  });
}
