import 'dart:convert';

import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/network/endpoints.dart';
import 'package:assurzen_mobile/data/api/api_notification_repository.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

http.Response _json(int status, Object? body) => http.Response.bytes(
      body == null ? const [] : utf8.encode(jsonEncode(body)),
      status,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );

Map<String, dynamic> _prefs({bool claim = true}) => {
      'items': [
        {'category': 'EXPIRY', 'enabled': true},
        {'category': 'SUBSCRIPTION', 'enabled': true},
        {'category': 'PAYMENT', 'enabled': true},
        {'category': 'CLAIM', 'enabled': claim},
        {'category': 'DOCUMENT', 'enabled': true},
        {'category': 'SERVICE', 'enabled': true},
      ],
    };

void main() {
  late List<http.Request> calls;

  ApiNotificationRepository build(MockClientHandler handler) {
    calls = [];
    return ApiNotificationRepository(ApiClient(
      baseUrl: 'http://gw.test',
      tokenProvider: () async => 'tok',
      httpClient: MockClient((req) async {
        calls.add(req);
        return handler(req);
      }),
    ));
  }

  group('Préférences push par catégorie', () {
    test('lecture : 6 catégories du serveur', () async {
      final repo = build((req) async => _json(200, _prefs(claim: false)));

      final prefs = await repo.getPreferences();

      expect(calls.single.url.path, Endpoints.notificationPreferences);
      expect(prefs, hasLength(6));
      expect(prefs.firstWhere((p) => p.category == NotificationCategory.sinistre).enabled, isFalse);
      expect(prefs.firstWhere((p) => p.category == NotificationCategory.echeance).enabled, isTrue);
    });

    test('écriture : seule la catégorie modifiée est envoyée, liste complète renvoyée', () async {
      final repo = build((req) async => _json(200, _prefs(claim: false)));

      final prefs = await repo.setPreference(NotificationCategory.sinistre, false);

      expect(calls.single.method, 'PUT');
      expect(jsonDecode(calls.single.body), {
        'items': [
          {'category': 'CLAIM', 'enabled': false},
        ],
      });
      expect(prefs.firstWhere((p) => p.category == NotificationCategory.sinistre).enabled, isFalse);
    });

    test('catégorie invalide : 400 avec field', () async {
      final repo = build((req) async => _json(400, {'code': 'VALIDATION_FAILED', 'message': 'Catégorie inconnue.', 'field': 'items[0].category'}));
      await expectLater(
        repo.setPreference(NotificationCategory.service, true),
        throwsA(isA<ApiException>().having((e) => e.field, 'field', 'items[0].category')),
      );
    });
  });

  group('Appareils (push)', () {
    test('enregistrement : jeton, plateforme et version ; identifiant renvoyé', () async {
      final repo = build((req) async => _json(201, {'id': 12, 'platform': 'ANDROID', 'appVersion': '1.0.0'}));

      final id = await repo.registerDevice(token: 'fcm-token-xyz', appVersion: '1.0.0');

      expect(calls.single.method, 'POST');
      expect(calls.single.url.path, Endpoints.devices);
      expect(jsonDecode(calls.single.body), {'token': 'fcm-token-xyz', 'platform': 'ANDROID', 'appVersion': '1.0.0'});
      expect(id, '12');
    });

    test('version absente : non envoyée', () async {
      final repo = build((req) async => _json(201, {'id': 3, 'platform': 'ANDROID'}));
      await repo.registerDevice(token: 't');
      expect(jsonDecode(calls.single.body).containsKey('appVersion'), isFalse);
    });

    test('retrait à la déconnexion : DELETE, 204 idempotent', () async {
      final repo = build((req) async => _json(204, null));
      await repo.unregisterDevice('12');
      expect(calls.single.method, 'DELETE');
      expect(calls.single.url.path, Endpoints.device('12'));
    });

    test('jeton invalide : 400 avec field token', () async {
      final repo = build((req) async => _json(400, {'code': 'VALIDATION_FAILED', 'message': 'Jeton invalide.', 'field': 'token'}));
      await expectLater(
        repo.registerDevice(token: ''),
        throwsA(isA<ApiException>().having((e) => e.field, 'field', 'token')),
      );
    });
  });
}
