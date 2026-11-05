import 'dart:convert';

import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/network/endpoints.dart';
import 'package:assurzen_mobile/data/api/api_auth_repository.dart';
import 'package:assurzen_mobile/data/api/api_notification_repository.dart';
import 'package:assurzen_mobile/data/api/api_support_repository.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

http.Response _json(int status, Object? body) => http.Response.bytes(
      utf8.encode(jsonEncode(body)),
      status,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );

ApiClient _client(MockClientHandler handler, List<http.Request> calls) => ApiClient(
      baseUrl: 'http://gw.test',
      tokenProvider: () async => 'tok',
      httpClient: MockClient((req) async {
        calls.add(req);
        return handler(req);
      }),
    );

void main() {
  late List<http.Request> calls;
  setUp(() => calls = []);

  group('Notifications', () {
    test('liste : catégories du serveur, textes tels quels, liens profonds', () async {
      final repo = ApiNotificationRepository(_client(
        (req) async => _json(200, {
          'items': [
            {'id': 7, 'category': 'CLAIM', 'title': 'Dossier mis à jour', 'body': 'Votre sinistre est en analyse.', 'createdAt': '2026-11-03T08:00:00Z', 'read': false, 'contractId': null, 'claimId': '1945'},
            {'id': 6, 'category': 'EXPIRY', 'title': 'Échéance proche', 'body': 'Dans 7 jours.', 'createdAt': '2026-11-02T08:00:00Z', 'read': true, 'contractId': '655', 'claimId': null},
            {'id': 5, 'category': 'AUTRE', 'title': 'Info', 'body': '', 'createdAt': '2026-11-01T08:00:00Z', 'read': false},
          ],
          'unreadCount': 2,
          'page': 0,
          'size': 50,
          'totalElements': 3,
        }),
        calls,
      ));

      final list = await repo.getNotifications();

      expect(calls.single.url.path, Endpoints.notifications);
      expect(calls.single.url.queryParameters, {'page': '0', 'size': '50'});
      expect(list.map((n) => n.category), [NotificationCategory.sinistre, NotificationCategory.echeance, NotificationCategory.service]);
      expect(list[0].claimId, '1945');
      expect(list[0].contractId, isNull);
      expect(list[1].contractId, '655');
      expect(list[1].read, isTrue);
      expect(list[0].title, 'Dossier mis à jour');
    });

    test('devis : « Paiement reçu » porte la référence du devis, « Contrat émis » le contrat et le devis', () {
      final received = ApiNotificationRepository.notificationFromJson({
        'id': '41', 'category': 'PAYMENT', 'title': 'Paiement reçu', 'body': "Votre contrat est en cours d'émission.",
        'createdAt': '2026-11-05T02:56:51Z', 'read': false, 'contractId': null, 'claimId': null, 'quoteReference': 'DEV-2026-000019',
      });
      final issued = ApiNotificationRepository.notificationFromJson({
        'id': '42', 'category': 'SUBSCRIPTION', 'title': 'Votre contrat est émis', 'body': 'Contrat POL-2026-0001.',
        'createdAt': '2026-11-05T09:00:00Z', 'read': false, 'contractId': '901', 'claimId': null, 'quoteReference': 'DEV-2026-000019',
      });
      final old = ApiNotificationRepository.notificationFromJson({'id': '7', 'category': 'CLAIM', 'title': 'x', 'body': '', 'createdAt': '2026-11-03T08:00:00Z'});

      expect(received.category, NotificationCategory.paiement);
      expect(received.quoteReference, 'DEV-2026-000019');
      expect(received.contractId, isNull, reason: "le contrat n'existe pas encore");
      expect(issued.category, NotificationCategory.souscription);
      expect(issued.contractId, '901');
      expect(issued.quoteReference, 'DEV-2026-000019');
      expect(old.quoteReference, isNull, reason: 'anciennes notifications : aucun devis');
      expect(received.markRead().quoteReference, 'DEV-2026-000019');
    });

    test('compteur de non lues, lu, tout lu', () async {
      final repo = ApiNotificationRepository(_client((req) async {
        if (req.url.path.endsWith('unread-count')) return _json(200, {'unreadCount': 3});
        return _json(200, {'unreadCount': 0});
      }, calls));

      expect(await repo.getUnreadCount(), 3);
      await repo.markRead('7');
      await repo.markAllRead();

      expect(calls[1].method, 'POST');
      expect(calls[1].url.path, Endpoints.notificationRead('7'));
      expect(calls[2].url.path, Endpoints.notificationsReadAll);
    });

    test('liste vide : aucune erreur', () async {
      final repo = ApiNotificationRepository(_client((req) async => _json(200, {'items': [], 'unreadCount': 0}), calls));
      expect(await repo.getNotifications(), isEmpty);
    });
  });

  group('Support', () {
    test('sujets du serveur', () async {
      final repo = ApiSupportRepository(_client(
        (req) async => _json(200, {
          'items': [
            {'id': 'CONTRACT', 'label': 'Contrat'},
            {'id': 'OTHER', 'label': 'Autre'},
          ],
        }),
        calls,
      ));
      final topics = await repo.getTopics();
      expect(calls.single.url.path, Endpoints.supportTopics);
      expect(topics.map((t) => t.id), ['CONTRACT', 'OTHER']);
      expect(topics.first.label, 'Contrat');
    });

    test('envoi : sujet et message, délai seulement s’il est fourni', () async {
      final repo = ApiSupportRepository(_client((req) async => _json(201, {'reference': 'SP-2026-000001', 'expectedWithinMinutes': null}), calls));
      final r = await repo.sendRequest(topicId: 'CLAIM', message: '  Bonjour, j’ai une question.  ');
      expect(calls.single.url.path, Endpoints.supportRequests);
      expect(jsonDecode(calls.single.body), {'topic': 'CLAIM', 'message': 'Bonjour, j’ai une question.'});
      expect(r.reference, 'SP-2026-000001');
      expect(r.expectedWithinMinutes, isNull, reason: 'clé de configuration vide = aucun engagement');
    });

    test('envoi : trop de demandes, 429 avec délai', () async {
      final repo = ApiSupportRepository(_client((req) async => _json(429, {'code': 'RATE_LIMITED', 'message': 'Trop de demandes.', 'retryAfterSeconds': 3000}), calls));
      await expectLater(
        repo.sendRequest(topicId: 'OTHER', message: 'Un message assez long'),
        throwsA(isA<ApiException>().having((e) => e.retryAfterSeconds, 'retryAfter', 3000)),
      );
    });

    test('mes demandes : statut et réponse du conseiller', () async {
      final repo = ApiSupportRepository(_client(
        (req) async => _json(200, {
          'items': [
            {'id': 2, 'reference': 'SP-2026-000002', 'topic': 'PAYMENT', 'topicLabel': 'Paiement', 'message': 'Question', 'status': 'REPONDU', 'response': 'Voici la réponse.', 'respondedAt': '2026-11-03T09:00:00Z', 'createdAt': '2026-11-03T08:00:00Z'},
            {'id': 1, 'reference': 'SP-2026-000001', 'topic': 'OTHER', 'topicLabel': null, 'message': 'Autre', 'status': 'EN_ATTENTE', 'response': null, 'respondedAt': null, 'createdAt': '2026-11-02T08:00:00Z'},
          ],
        }),
        calls,
      ));
      final list = await repo.getRequests();
      expect(list[0].status, SupportStatus.repondu);
      expect(list[0].response, 'Voici la réponse.');
      expect(list[0].topicLabel, 'Paiement');
      expect(list[1].status, SupportStatus.enAttente);
      expect(list[1].response, isNull);
      expect(list[1].respondedAt, isNull);
      expect(list[1].topicLabel, 'OTHER', reason: 'libellé absent : on retombe sur le code du serveur');
    });

    test('FAQ : triée par ordre, entrée incomplète ignorée', () async {
      final repo = ApiSupportRepository(_client(
        (req) async => _json(200, {
          'items': [
            {'id': 2, 'question': 'Deuxième ?', 'answer': 'B', 'order': 2},
            {'id': 1, 'question': 'Première ?', 'answer': 'A', 'order': 1},
            {'id': 3, 'question': 'Sans réponse ?', 'answer': '', 'order': 3},
          ],
        }),
        calls,
      ));
      final faq = await repo.getFaq();
      expect(faq.map((f) => f.question), ['Première ?', 'Deuxième ?']);
    });

    test('liens légaux : null = masqué', () async {
      final repo = ApiSupportRepository(_client((req) async => _json(200, {'termsUrl': null, 'privacyUrl': ' https://x.test/p ', 'updatedAt': null}), calls));
      final l = await repo.getLegalLinks();
      expect(l.termsUrl, isNull);
      expect(l.privacyUrl, 'https://x.test/p');
    });

    test('avis : note et commentaire facultatif', () async {
      final repo = ApiSupportRepository(_client((req) async => _json(201, {'reference': 'FB-2026-000001'}), calls));
      expect(await repo.sendFeedback(rating: 5), 'FB-2026-000001');
      expect(jsonDecode(calls.single.body), {'rating': 5});
    });
  });

  group('Profil', () {
    test('PATCH /mobile/me : nom et e-mail, e-mail vide pour l’effacer', () async {
      final repo = ApiAuthRepository(
        _client((req) async => _json(200, {'id': '77', 'fullName': 'Awa Koné', 'phone': '0700000002', 'email': ''}), calls),
        readRefreshToken: () async => null,
        saveTokens: (_, __) async {},
      );
      final user = await repo.updateProfile(fullName: ' Awa Koné ', email: '');
      expect(calls.single.method, 'PATCH');
      expect(calls.single.url.path, Endpoints.mobileMe);
      expect(jsonDecode(calls.single.body), {'fullName': 'Awa Koné', 'email': ''});
      expect(user.fullName, 'Awa Koné');
      expect(user.email, isNull);
      expect(user.phone, '0700000002');
    });

    test('e-mail invalide : erreur sur le champ email', () async {
      final repo = ApiAuthRepository(
        _client((req) async => _json(400, {'code': 'VALIDATION_FAILED', 'message': 'email: invalide', 'field': 'email'}), calls),
        readRefreshToken: () async => null,
        saveTokens: (_, __) async {},
      );
      await expectLater(
        repo.updateProfile(email: 'pas-un-email'),
        throwsA(isA<ApiException>().having((e) => e.field, 'field', 'email')),
      );
    });
  });
}
