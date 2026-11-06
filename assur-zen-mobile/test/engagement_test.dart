import 'dart:convert';

import 'package:assurzen_mobile/app/app_scope.dart';
import 'package:assurzen_mobile/app/routes.dart';
import 'package:assurzen_mobile/app/session_controller.dart';
import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/network/endpoints.dart';
import 'package:assurzen_mobile/core/storage/local_store.dart';
import 'package:assurzen_mobile/data/api/api_engagement_repository.dart';
import 'package:assurzen_mobile/data/mock/mock_data.dart';
import 'package:assurzen_mobile/data/mock/mock_repositories.dart';
import 'package:assurzen_mobile/data/models/engagement.dart';
import 'package:assurzen_mobile/data/repositories/repositories.dart';
import 'package:assurzen_mobile/features/engagement/badges_page.dart';
import 'package:assurzen_mobile/features/engagement/engagement_card.dart';
import 'package:flutter/material.dart';
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

Map<String, dynamic> _engagement({
  bool enabled = true,
  bool accepted = false,
  int? acceptedVersion,
  Object? text = 'Texte de consentement du serveur.',
  Object? version = 4,
  bool score = true,
  bool badges = false,
}) =>
    {
      'enabled': enabled,
      'consent': {'accepted': accepted, 'acceptedAt': accepted ? '2026-11-05T10:00:00Z' : null, 'acceptedVersion': acceptedVersion},
      'consentText': text,
      'consentVersion': version,
      'features': {'protectionScore': score, 'dailyQuiz': false, 'badges': badges},
    };

Map<String, dynamic> _score({int score = 45}) => {
      'score': score,
      'items': [
        {'id': 'PROFILE_COMPLETE', 'label': 'Profil complet', 'done': true, 'hint': null, 'action': null},
        {'id': 'ATTESTATION_DOWNLOADED', 'label': 'Attestation téléchargée', 'done': false, 'hint': 'Ouvrez-la une fois.', 'action': 'DOCUMENTS'},
      ],
      'updatedAt': '2026-11-05T10:00:00Z',
    };

void main() {
  setUpAll(loadAppFonts);

  group('Lecture des réponses', () {
    test('engagement complet : consentement, version acceptée, fonctions', () {
      final e = Engagement.fromJson(_engagement(accepted: true, acceptedVersion: 4));

      expect(e.enabled, isTrue);
      expect(e.consent.accepted, isTrue);
      expect(e.consent.acceptedVersion, 4);
      expect(e.consent.acceptedAt, isNotNull);
      expect(e.consentText, 'Texte de consentement du serveur.');
      expect(e.consentVersion, 4);
      expect(e.features.protectionScore, isTrue);
      expect(e.features.dailyQuiz, isFalse);
      expect(e.showsScore, isTrue);
      expect(e.canAskConsent, isTrue);
    });

    test('réponse vide ou mal formée : tout est coupé, aucune exception', () {
      for (final bad in [null, 'oups', 3, <String, dynamic>{}]) {
        final e = Engagement.fromJson(bad);
        expect(e.enabled, isFalse, reason: '$bad');
        expect(e.showsScore, isFalse);
        expect(e.canAskConsent, isFalse);
        expect(e.consent.accepted, isFalse);
      }
    });

    test('sans texte ni version : on ne peut pas demander le consentement', () {
      expect(Engagement.fromJson(_engagement(text: '  ', version: 4)).canAskConsent, isFalse);
      expect(Engagement.fromJson(_engagement(text: 'Texte', version: null)).canAskConsent, isFalse);
    });

    test('fonction coupée par le serveur : le score n\'est pas proposé', () {
      expect(Engagement.fromJson(_engagement(score: false)).showsScore, isFalse);
      expect(Engagement.fromJson(_engagement(enabled: false)).showsScore, isFalse);
    });

    test('score : valeur bornée, critères, gestes connus, critère sans libellé ignoré', () {
      final s = ProtectionScore.fromJson({
        'score': 140,
        'items': [
          {'id': 'A', 'label': 'Fait', 'done': true},
          {'id': 'B', 'label': 'À faire', 'done': false, 'hint': 'Conseil', 'action': 'PROFILE'},
          {'id': 'C', 'label': 'Geste inconnu', 'done': false, 'action': 'AUTRE_CHOSE'},
          {'id': 'D', 'label': '   ', 'done': false},
        ],
        'updatedAt': '2026-11-05T10:00:00Z',
      });

      expect(s.score, 100);
      expect(s.items.map((i) => i.id), ['A', 'B', 'C']);
      expect(s.items[1].action, ScoreAction.profile);
      expect(s.items[2].action, isNull, reason: 'geste hors liste fermée : pas de bouton');
      expect(s.items[0].hint, isNull);
      expect(s.todo.map((i) => i.id), ['B', 'C']);
      expect(s.updatedAt, isNotNull);
      expect(ProtectionScore.fromJson(null).score, 0);
      expect(ProtectionScore.fromJson({'score': -5}).score, 0);
    });
  });

  group('Appels au serveur', () {
    late List<http.Request> calls;

    ApiEngagementRepository build(MockClientHandler h) {
      calls = [];
      return ApiEngagementRepository(ApiClient(
        baseUrl: 'http://gw.test',
        tokenProvider: () async => 'jeton',
        httpClient: MockClient((req) async {
          calls.add(req);
          return h(req);
        }),
      ));
    }

    test('lecture des réglages', () async {
      final repo = build((_) async => _json(200, _engagement()));

      final e = await repo.getEngagement();

      expect(calls.single.method, 'GET');
      expect(calls.single.url.path, endsWith('/mobile/engagement'));
      expect(calls.single.headers['Authorization'], 'Bearer jeton');
      expect(e.consentVersion, 4);
    });

    test('accepter : PUT avec la version lue par le client', () async {
      final repo = build((_) async => _json(200, _engagement(accepted: true, acceptedVersion: 4)));

      final e = await repo.setConsent(accepted: true, consentVersion: 4);

      expect(calls.single.method, 'PUT');
      expect(calls.single.url.path, endsWith('/mobile/engagement/consent'));
      expect(jsonDecode(calls.single.body), {'accepted': true, 'consentVersion': 4});
      expect(e.consent.acceptedVersion, 4);
    });

    test('retirer : aucune version envoyée', () async {
      final repo = build((_) async => _json(200, _engagement()));

      await repo.setConsent(accepted: false, consentVersion: 4);

      expect(jsonDecode(calls.single.body), {'accepted': false});
    });

    test('texte modifié entre l\'affichage et l\'envoi : 409 CONSENT_VERSION_OUTDATED', () async {
      final repo = build((_) async => _json(409, {'code': 'CONSENT_VERSION_OUTDATED', 'message': 'Texte mis à jour.'}));

      await expectLater(
        repo.setConsent(accepted: true, consentVersion: 3),
        throwsA(isA<ApiException>().having((e) => e.code, 'code', 'CONSENT_VERSION_OUTDATED')),
      );
    });

    test('score : lecture', () async {
      final repo = build((_) async => _json(200, _score()));

      final s = await repo.getProtectionScore();

      expect(calls.single.url.path, endsWith('/mobile/protection-score'));
      expect(s.score, 45);
      expect(s.items, hasLength(2));
    });

    test('score sans consentement (403) ou fonction coupée (404) : le code du serveur est conservé', () async {
      final noConsent = build((_) async => _json(403, {'code': 'ENGAGEMENT_CONSENT_REQUIRED', 'message': 'Accord requis.'}));
      await expectLater(noConsent.getProtectionScore(), throwsA(isA<ApiException>().having((e) => e.code, 'code', 'ENGAGEMENT_CONSENT_REQUIRED')));

      final off = build((_) async => _json(404, {'code': 'FEATURE_DISABLED', 'message': 'Indisponible.'}));
      await expectLater(off.getProtectionScore(), throwsA(isA<ApiException>().having((e) => e.code, 'code', 'FEATURE_DISABLED')));
    });

    test('adresses', () {
      expect(Endpoints.engagement, endsWith('/mobile/engagement'));
      expect(Endpoints.engagementConsent, endsWith('/mobile/engagement/consent'));
      expect(Endpoints.protectionScore, endsWith('/mobile/protection-score'));
    });
  });

  group('Carte « Ma protection »', () {
    Future<_Fake> show(WidgetTester tester, _Fake fake) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(411, 891);
      addTearDown(tester.view.reset);
      SharedPreferences.setMockInitialValues({'lock_choice_done': true});
      final store = await LocalStore.open();
      final base = MockRepositories.create();
      final repos = Repositories(
        auth: base.auth,
        catalog: base.catalog,
        quotes: base.quotes,
        motoQuotes: base.motoQuotes,
        payments: base.payments,
        contracts: base.contracts,
        claims: base.claims,
        notifications: base.notifications,
        support: base.support,
        engagement: fake,
      );
      final session = SessionController(store: store, repos: repos)..updateUser(MockData.demoUser);
      await tester.pumpWidget(AppScope(
        repos: repos,
        session: session,
        store: store,
        child: MaterialApp(
          onGenerateRoute: (s) => MaterialPageRoute<void>(settings: s, builder: (_) => Scaffold(body: Text('page ${s.name}'))),
          home: const Scaffold(body: SingleChildScrollView(child: EngagementCard())),
        ),
      ));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      return fake;
    }

    testWidgets('engagement coupé : rien ne s\'affiche', (tester) async {
      await show(tester, _Fake(engagement: Engagement.fromJson(_engagement(enabled: false))));

      expect(find.text('Ma protection'), findsNothing);
    });

    testWidgets('erreur serveur : la carte reste invisible, aucun message', (tester) async {
      await show(tester, _Fake(failEngagement: true));

      expect(find.text('Ma protection'), findsNothing);
      expect(find.byType(SnackBar), findsNothing);
    });

    testWidgets('sans consentement et sans texte du serveur : rien à proposer', (tester) async {
      await show(tester, _Fake(engagement: Engagement.fromJson(_engagement(text: null))));

      expect(find.text('Ma protection'), findsNothing);
    });

    testWidgets('sans consentement : invitation, et le texte du serveur s\'affiche tel quel', (tester) async {
      await show(tester, _Fake(engagement: Engagement.fromJson(_engagement())));

      expect(find.text('Activer'), findsOneWidget);
      await tester.tap(find.text('Ma protection'));
      await tester.pumpAndSettle();

      expect(find.text('Texte de consentement du serveur.'), findsOneWidget);
      expect(find.text('J’accepte'), findsOneWidget);
      expect(find.text('Plus tard'), findsOneWidget);
    });

    testWidgets('« Plus tard » : rien n\'est enregistré', (tester) async {
      final fake = await show(tester, _Fake(engagement: Engagement.fromJson(_engagement())));

      await tester.tap(find.text('Ma protection'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Plus tard'));
      await tester.pumpAndSettle();

      expect(fake.consentCalls, isEmpty);
      expect(find.text('Activer'), findsOneWidget);
    });

    testWidgets('« J\'accepte » : la version lue est envoyée, puis le score apparaît', (tester) async {
      final fake = await show(tester, _Fake(engagement: Engagement.fromJson(_engagement())));

      await tester.tap(find.text('Ma protection'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('J’accepte'));
      await tester.pumpAndSettle();

      expect(fake.consentCalls, [(true, 4)]);
      expect(find.text('45'), findsOneWidget);
      expect(find.text('Attestation téléchargée'), findsOneWidget, reason: 'premier geste à faire, texte du serveur');
    });

    testWidgets('texte mis à jour pendant la lecture : le nouveau texte s\'affiche, sans enregistrer', (tester) async {
      final fake = await show(tester, _Fake(engagement: Engagement.fromJson(_engagement())));
      fake.nextConsentError = const ApiException(kind: ApiErrorKind.business, statusCode: 409, code: 'CONSENT_VERSION_OUTDATED', message: 'Texte mis à jour.');
      fake.engagement = Engagement.fromJson(_engagement(text: 'Nouveau texte du serveur.', version: 5));

      await tester.tap(find.text('Ma protection'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('J’accepte'));
      await tester.pumpAndSettle();

      expect(find.text('Nouveau texte du serveur.'), findsOneWidget);
      expect(find.textContaining('mis à jour'), findsOneWidget);
      expect(fake.engagement.consent.accepted, isFalse);
    });

    testWidgets('avec consentement : jauge, score du serveur et premier geste', (tester) async {
      await show(tester, _Fake(engagement: Engagement.fromJson(_engagement(accepted: true, acceptedVersion: 4))));

      expect(find.text('Ma protection'), findsOneWidget);
      expect(find.text('45'), findsOneWidget);
      expect(find.text('Attestation téléchargée'), findsOneWidget);
      expect(find.text('Activer'), findsNothing);
    });

    testWidgets('avec consentement mais score refusé par le serveur : la carte se replie', (tester) async {
      await show(tester, _Fake(engagement: Engagement.fromJson(_engagement(accepted: true, acceptedVersion: 4)), scoreError: true));

      expect(find.text('Ma protection'), findsNothing);
    });

    testWidgets('toucher la carte ouvre le détail du score', (tester) async {
      await show(tester, _Fake(engagement: Engagement.fromJson(_engagement(accepted: true, acceptedVersion: 4))));

      await tester.tap(find.textContaining('sur 100'));
      await tester.pumpAndSettle();

      expect(find.text('page ${AppRoutes.protectionScore}'), findsOneWidget);
    });
  });

  group('Badges', () {
    test('lecture : obtenus, à gagner avec progression, badge sans libellé ignoré', () {
      final list = AppBadge.listFromJson({
        'items': [
          {'id': 'PHONE_VERIFIED', 'label': 'Numéro vérifié', 'description': 'Confirmé par SMS.', 'earned': true, 'earnedAt': '2026-11-01T08:00:00Z'},
          {'id': 'QUIZ_STREAK_7', 'label': '7 jours de suite', 'earned': false, 'progress': {'current': 2, 'target': 7}},
          {'id': 'X', 'label': '  ', 'earned': true},
        ],
      });

      expect(list.map((b) => b.id), ['PHONE_VERIFIED', 'QUIZ_STREAK_7']);
      expect(list[0].earned, isTrue);
      expect(list[0].earnedAt, isNotNull);
      expect(list[0].description, 'Confirmé par SMS.');
      expect(list[1].description, isNull);
      expect(list[1].progress?.current, 2);
      expect(list[1].progress?.target, 7);
      expect(list[1].progress?.ratio, closeTo(2 / 7, 0.001));
    });

    test('progression : absente ou sans cible = aucune barre, valeur bornée à la cible', () {
      expect(BadgeProgress.fromJson(null), isNull);
      expect(BadgeProgress.fromJson({'current': 3}), isNull);
      expect(BadgeProgress.fromJson({'current': 3, 'target': 0}), isNull);
      expect(BadgeProgress.fromJson({'current': 12, 'target': 7})?.current, 7);
      expect(BadgeProgress.fromJson({'current': -4, 'target': 7})?.ratio, 0);
    });

    test('réponses mal formées : liste vide, aucune exception', () {
      expect(AppBadge.listFromJson(null), isEmpty);
      expect(AppBadge.listFromJson('oups'), isEmpty);
      expect(AppBadge.listFromJson({'items': 3}), isEmpty);
    });

    test('icône : un code inconnu reçoit une icône générique', () {
      expect(badgeIcon('QUIZ_STREAK_7'), isNot(badgeIcon('CODE_FUTUR')));
      expect(badgeIcon('CODE_FUTUR'), badgeIcon('AUTRE_CODE_FUTUR'));
    });

    test('appel : GET /mobile/badges avec le jeton', () async {
      final calls = <http.Request>[];
      final repo = ApiEngagementRepository(ApiClient(
        baseUrl: 'http://gw.test',
        tokenProvider: () async => 'jeton',
        httpClient: MockClient((req) async {
          calls.add(req);
          return _json(200, {
            'items': [
              {'id': 'PHONE_VERIFIED', 'label': 'Numéro vérifié', 'earned': true},
            ],
          });
        }),
      ));

      final list = await repo.getBadges();

      expect(calls.single.url.path, endsWith('/mobile/badges'));
      expect(calls.single.headers['Authorization'], 'Bearer jeton');
      expect(list.single.label, 'Numéro vérifié');
      expect(Endpoints.badges, endsWith('/mobile/badges'));
    });

    testWidgets('page : obtenus d\'abord, puis à gagner avec leur progression', (tester) async {
      await _showBadges(tester, [
        const AppBadge(id: 'QUIZ_STREAK_7', label: '7 jours de suite', earned: false, progress: BadgeProgress(current: 2, target: 7)),
        AppBadge(id: 'PHONE_VERIFIED', label: 'Numéro vérifié', description: 'Confirmé par SMS.', earned: true, earnedAt: DateTime(2026, 11, 1)),
      ]);

      expect(find.text('1 sur 2'), findsOneWidget);
      final earnedY = tester.getTopLeft(find.text('Numéro vérifié')).dy;
      final todoY = tester.getTopLeft(find.text('7 jours de suite')).dy;
      expect(earnedY, lessThan(todoY), reason: 'le badge obtenu passe avant celui à gagner');
      expect(find.textContaining('Obtenu le'), findsOneWidget);
      expect(find.text('2 sur 7'), findsOneWidget);
      expect(find.byType(LinearProgressIndicator), findsOneWidget);
    });

    testWidgets('carte d\'accueil : « Mes badges » avec le compte, seulement si le serveur l\'active', (tester) async {
      await _showCard(tester, _Fake(
        engagement: Engagement.fromJson(_engagement(accepted: true, acceptedVersion: 4, score: false, badges: true)),
        badges: const [
          AppBadge(id: 'A', label: 'Un', earned: true),
          AppBadge(id: 'B', label: 'Deux', earned: false),
          AppBadge(id: 'C', label: 'Trois', earned: false),
        ],
      ));

      expect(find.text('Mes badges · 1 sur 3'), findsOneWidget);
      expect(find.textContaining('sur 100'), findsNothing, reason: 'score coupé par le serveur : pas de ligne de score');
    });

    testWidgets('carte d\'accueil : fonction badges coupée ou aucun badge = rien', (tester) async {
      await _showCard(tester, _Fake(
        engagement: Engagement.fromJson(_engagement(accepted: true, acceptedVersion: 4, score: false, badges: false)),
        badges: const [AppBadge(id: 'A', label: 'Un', earned: true)],
      ));
      expect(find.textContaining('Mes badges'), findsNothing);

      await _showCard(tester, _Fake(
        engagement: Engagement.fromJson(_engagement(accepted: true, acceptedVersion: 4, score: false, badges: true)),
      ));
      expect(find.textContaining('Mes badges'), findsNothing);
    });

    testWidgets('score et badges ensemble : une seule carte, les badges sous le score', (tester) async {
      await _showCard(tester, _Fake(
        engagement: Engagement.fromJson(_engagement(accepted: true, acceptedVersion: 4, badges: true)),
        badges: const [AppBadge(id: 'A', label: 'Un', earned: true)],
      ));

      expect(find.text('Ma protection'), findsOneWidget);
      expect(find.textContaining('Mes badges'), findsOneWidget);
      expect(tester.getTopLeft(find.textContaining('Mes badges')).dy, greaterThan(tester.getTopLeft(find.textContaining('sur 100')).dy), reason: 'badges sous le score');
    });

    testWidgets('sans consentement, la seule fonction « badges » propose aussi l\'accord', (tester) async {
      await _showCard(tester, _Fake(engagement: Engagement.fromJson(_engagement(score: false, badges: true))));

      expect(find.text('Activer'), findsOneWidget);
    });

    testWidgets('toucher « Mes badges » ouvre la page des badges', (tester) async {
      await _showCard(tester, _Fake(
        engagement: Engagement.fromJson(_engagement(accepted: true, acceptedVersion: 4, score: false, badges: true)),
        badges: const [AppBadge(id: 'A', label: 'Un', earned: true)],
      ));

      await tester.tap(find.textContaining('Mes badges'));
      await tester.pumpAndSettle();

      expect(find.text('page ${AppRoutes.badges}'), findsOneWidget);
    });
  });
}

Future<void> _setup(WidgetTester tester, _Fake fake, Widget Function(Widget) wrap) async {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = const Size(411, 891);
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({'lock_choice_done': true});
  final store = await LocalStore.open();
  final base = MockRepositories.create();
  final repos = Repositories(
    auth: base.auth,
    catalog: base.catalog,
    quotes: base.quotes,
    motoQuotes: base.motoQuotes,
    payments: base.payments,
    contracts: base.contracts,
    claims: base.claims,
    notifications: base.notifications,
    support: base.support,
    engagement: fake,
  );
  final session = SessionController(store: store, repos: repos)..updateUser(MockData.demoUser);
  await tester.pumpWidget(AppScope(repos: repos, session: session, store: store, child: wrap(const SizedBox.shrink())));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 100));
}

Future<void> _showCard(WidgetTester tester, _Fake fake) => _setup(
      tester,
      fake,
      (_) => MaterialApp(
        onGenerateRoute: (s) => MaterialPageRoute<void>(settings: s, builder: (_) => Scaffold(body: Text('page ${s.name}'))),
        home: const Scaffold(body: SingleChildScrollView(child: EngagementCard())),
      ),
    );

Future<void> _showBadges(WidgetTester tester, List<AppBadge> badges) => _setup(
      tester,
      _Fake(engagement: Engagement.fromJson(_engagement(accepted: true, acceptedVersion: 4, score: false, badges: true)), badges: badges),
      (_) => const MaterialApp(home: BadgesPage()),
    );

/// Dépôt d'engagement de test : il répond ce qu'on lui demande et note les appels.
class _Fake implements EngagementRepository {
  _Fake({Engagement? engagement, this.failEngagement = false, this.scoreError = false, this.badges = const []})
      : engagement = engagement ?? Engagement.disabled;

  final List<AppBadge> badges;

  Engagement engagement;
  final bool failEngagement;
  final bool scoreError;
  final List<(bool, int?)> consentCalls = [];
  ApiException? nextConsentError;

  @override
  Future<Engagement> getEngagement() async {
    if (failEngagement) throw ApiException.server();
    return engagement;
  }

  @override
  Future<Engagement> setConsent({required bool accepted, int? consentVersion}) async {
    final err = nextConsentError;
    if (err != null) {
      nextConsentError = null;
      throw err;
    }
    consentCalls.add((accepted, consentVersion));
    engagement = Engagement.fromJson(_engagement(accepted: accepted, acceptedVersion: accepted ? consentVersion : null, version: consentVersion ?? 4));
    return engagement;
  }

  @override
  Future<ProtectionScore> getProtectionScore() async {
    if (scoreError || !engagement.consent.accepted) {
      throw const ApiException(kind: ApiErrorKind.business, statusCode: 403, code: 'ENGAGEMENT_CONSENT_REQUIRED', message: 'Accord requis.');
    }
    return ProtectionScore.fromJson(_score());
  }

  @override
  Future<List<AppBadge>> getBadges() async {
    if (!engagement.consent.accepted) {
      throw const ApiException(kind: ApiErrorKind.business, statusCode: 403, code: 'ENGAGEMENT_CONSENT_REQUIRED', message: 'Accord requis.');
    }
    return badges;
  }

  @override
  Future<QuizQuestion?> getQuizToday() async => null;

  @override
  Future<QuizAnswer> answerQuiz({required String questionId, required String choiceId}) async => throw UnimplementedError();
}
