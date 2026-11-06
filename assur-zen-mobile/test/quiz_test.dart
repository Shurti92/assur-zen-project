import 'dart:convert';

import 'package:assurzen_mobile/app/app_scope.dart';
import 'package:assurzen_mobile/app/routes.dart';
import 'package:assurzen_mobile/app/session_controller.dart';
import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/network/endpoints.dart';
import 'package:assurzen_mobile/core/storage/local_store.dart';
import 'package:assurzen_mobile/core/widgets/az_buttons.dart';
import 'package:assurzen_mobile/data/api/api_engagement_repository.dart';
import 'package:assurzen_mobile/data/mock/mock_data.dart';
import 'package:assurzen_mobile/data/mock/mock_repositories.dart';
import 'package:assurzen_mobile/data/models/engagement.dart';
import 'package:assurzen_mobile/data/repositories/repositories.dart';
import 'package:assurzen_mobile/features/engagement/engagement_card.dart';
import 'package:assurzen_mobile/features/engagement/quiz_page.dart';
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

Map<String, dynamic> _question({bool answered = false, Object? result, Object? choices, Object? text = 'Que faire en premier ?', Object? id = '12'}) => {
      'id': id,
      'theme': 'Prévention',
      'question': text,
      'choices': choices ??
          [
            {'id': '31', 'label': 'Sécuriser les lieux'},
            {'id': '32', 'label': 'Partir'},
            {'id': '33', 'label': 'Attendre'},
          ],
      'answered': answered,
      if (result != null) 'result': result,
      'streak': {'current': 2, 'best': 5},
    };

const _result = {'choiceId': '32', 'correct': false, 'correctChoiceId': '31', 'explanation': 'On sécurise d\'abord les lieux.'};

Map<String, dynamic> _engagement({bool accepted = true, bool quiz = true}) => {
      'enabled': true,
      'consent': {'accepted': accepted, 'acceptedAt': null, 'acceptedVersion': accepted ? 4 : null},
      'consentText': 'Texte.',
      'consentVersion': 4,
      'features': {'protectionScore': false, 'dailyQuiz': quiz, 'badges': false},
    };

void main() {
  setUpAll(loadAppFonts);

  group('Lecture de la question', () {
    test('question complète : identifiants en chaînes, choix dans l\'ordre du serveur, série', () {
      final q = QuizQuestion.fromJson(_question())!;

      expect(q.id, '12');
      expect(q.theme, 'Prévention');
      expect(q.question, 'Que faire en premier ?');
      expect(q.choices.map((c) => c.id), ['31', '32', '33']);
      expect(q.choices.first.label, 'Sécuriser les lieux');
      expect(q.answered, isFalse);
      expect(q.result, isNull, reason: 'la bonne réponse n\'est jamais connue avant de répondre');
      expect(q.streak.current, 2);
      expect(q.streak.best, 5);
    });

    test('identifiants numériques envoyés par le serveur : convertis en texte', () {
      final q = QuizQuestion.fromJson(_question(id: 12, choices: [
        {'id': 31, 'label': 'A'},
        {'id': 32, 'label': 'B'},
      ]))!;

      expect(q.id, '12');
      expect(q.choices.map((c) => c.id), ['31', '32']);
    });

    test('déjà répondu : le résultat du serveur est rappelé', () {
      final q = QuizQuestion.fromJson(_question(answered: true, result: _result))!;

      expect(q.answered, isTrue);
      expect(q.result?.choiceId, '32');
      expect(q.result?.correct, isFalse);
      expect(q.result?.correctChoiceId, '31');
      expect(q.result?.explanation, 'On sécurise d\'abord les lieux.');
    });

    test('question injouable ou mal formée : masquée, jamais affichée cassée', () {
      expect(QuizQuestion.fromJson(null), isNull);
      expect(QuizQuestion.fromJson('oups'), isNull);
      expect(QuizQuestion.fromJson(_question(id: null)), isNull);
      expect(QuizQuestion.fromJson(_question(text: '   ')), isNull);
      expect(QuizQuestion.fromJson(_question(choices: [{'id': '1', 'label': 'Seul choix'}])), isNull);
      expect(
        QuizQuestion.fromJson(_question(choices: [
          {'id': '1', 'label': 'A'},
          {'id': '', 'label': 'sans id'},
          {'id': '3', 'label': '  '},
        ])),
        isNull,
        reason: 'les choix sans identifiant ou sans texte sont écartés : il n\'en reste qu\'un',
      );
    });

    test('série : valeurs bornées, absente = aucune série', () {
      expect(QuizStreak.fromJson(null).current, 0);
      expect(QuizStreak.fromJson({'current': -3, 'best': 4}).current, 0);
      expect(QuizStreak.fromJson({'current': 3}).best, 0);
    });
  });

  group('Appels au serveur', () {
    late List<http.Request> calls;

    ApiEngagementRepository build(MockClientHandler h) {
      calls = [];
      return ApiEngagementRepository(
        ApiClient(
          baseUrl: 'http://gw.test',
          tokenProvider: () async => 'jeton',
          httpClient: MockClient((req) async {
            calls.add(req);
            return h(req);
          }),
        ),
        newKey: () => 'cle-fixe',
      );
    }

    test('question du jour : GET avec le jeton', () async {
      final repo = build((_) async => _json(200, _question()));

      final q = await repo.getQuizToday();

      expect(calls.single.method, 'GET');
      expect(calls.single.url.path, endsWith('/mobile/quiz/today'));
      expect(calls.single.headers['Authorization'], 'Bearer jeton');
      expect(q?.id, '12');
    });

    test('204 : rien à proposer, pas d\'erreur', () async {
      final repo = build((_) async => http.Response('', 204));

      expect(await repo.getQuizToday(), isNull);
    });

    test('consentement manquant (403) ou fonction coupée (404) : le code du serveur est conservé', () async {
      final noConsent = build((_) async => _json(403, {'code': 'ENGAGEMENT_CONSENT_REQUIRED', 'message': 'Accord requis.'}));
      await expectLater(noConsent.getQuizToday(), throwsA(isA<ApiException>().having((e) => e.code, 'code', 'ENGAGEMENT_CONSENT_REQUIRED')));

      final off = build((_) async => _json(404, {'code': 'FEATURE_DISABLED', 'message': 'Indisponible.'}));
      await expectLater(off.getQuizToday(), throwsA(isA<ApiException>().having((e) => e.code, 'code', 'FEATURE_DISABLED')));
    });

    test('réponse : POST avec l\'identifiant du choix et une clé d\'idempotence', () async {
      final repo = build((_) async => _json(200, {
            'correct': true,
            'correctChoiceId': '31',
            'explanation': 'Bien vu.',
            'streak': {'current': 3, 'best': 5},
          }));

      final a = await repo.answerQuiz(questionId: '12', choiceId: '31');

      expect(calls.single.method, 'POST');
      expect(calls.single.url.path, endsWith('/mobile/quiz/12/answer'));
      expect(jsonDecode(calls.single.body), {'choiceId': '31', 'idempotencyKey': 'cle-fixe'});
      expect(a.result.correct, isTrue);
      expect(a.result.choiceId, '31', reason: 'le choix envoyé est rappelé pour colorer la bonne carte');
      expect(a.result.correctChoiceId, '31');
      expect(a.result.explanation, 'Bien vu.');
      expect(a.streak.current, 3);
    });

    test('question qui n\'est plus celle du jour : 409 QUIZ_QUESTION_NOT_CURRENT', () async {
      final repo = build((_) async => _json(409, {'code': 'QUIZ_QUESTION_NOT_CURRENT', 'message': 'Question périmée.'}));

      await expectLater(
        repo.answerQuiz(questionId: '12', choiceId: '31'),
        throwsA(isA<ApiException>().having((e) => e.code, 'code', 'QUIZ_QUESTION_NOT_CURRENT')),
      );
    });

    test('choix inconnu : 400 VALIDATION_FAILED', () async {
      final repo = build((_) async => _json(400, {'code': 'VALIDATION_FAILED', 'message': 'choiceId: inconnu'}));

      await expectLater(repo.answerQuiz(questionId: '12', choiceId: '99'), throwsA(isA<ApiException>()));
    });

    test('adresses', () {
      expect(Endpoints.quizToday, endsWith('/mobile/quiz/today'));
      expect(Endpoints.quizAnswer('7'), endsWith('/mobile/quiz/7/answer'));
    });
  });

  group('Écran du quiz', () {
    testWidgets('question non répondue : choix du serveur, « Valider » désactivé tant que rien n\'est choisi', (tester) async {
      await _show(tester, _Fake(today: QuizQuestion.fromJson(_question())), const QuizPage());

      expect(find.text('Que faire en premier ?'), findsOneWidget);
      expect(find.text('Sécuriser les lieux'), findsOneWidget);
      expect(find.text('2 jours de suite'), findsOneWidget);
      expect(tester.widget<AzButton>(find.widgetWithText(AzButton, 'Valider')).onPressed, isNull);

      await tester.tap(find.text('Partir'));
      await tester.pump();
      expect(tester.widget<AzButton>(find.widgetWithText(AzButton, 'Valider')).onPressed, isNotNull, reason: 'actif dès qu\'un choix est fait');
    });

    testWidgets('mauvaise réponse : la bonne est montrée, avec l\'explication du serveur', (tester) async {
      final fake = _Fake(
        today: QuizQuestion.fromJson(_question()),
        answer: const QuizAnswer(
          result: QuizResult(choiceId: '32', correct: false, correctChoiceId: '31', explanation: 'On sécurise d\'abord les lieux.'),
          streak: QuizStreak(current: 3, best: 5),
        ),
      );
      await _show(tester, fake, const QuizPage());

      await tester.tap(find.text('Partir'));
      await tester.pump();
      await tester.tap(find.text('Valider'));
      await tester.pumpAndSettle();

      expect(fake.answers, [('12', '32')]);
      expect(find.textContaining('Pas tout à fait', findRichText: true), findsOneWidget);
      expect(find.textContaining('On sécurise d\'abord les lieux.', findRichText: true), findsOneWidget);
      expect(find.byIcon(Icons.check_circle_rounded), findsOneWidget, reason: 'la bonne réponse est signalée');
      expect(find.byIcon(Icons.cancel_rounded), findsOneWidget, reason: 'le choix erroné aussi');
      expect(find.text('Valider'), findsNothing);
      expect(find.text('3 jours de suite'), findsOneWidget, reason: 'série mise à jour par le serveur');
    });

    testWidgets('bonne réponse sans explication : seulement le verdict, aucune promesse écrite en dur', (tester) async {
      final fake = _Fake(
        today: QuizQuestion.fromJson(_question()),
        answer: const QuizAnswer(
          result: QuizResult(choiceId: '31', correct: true, correctChoiceId: '31'),
          streak: QuizStreak(current: 1, best: 1),
        ),
      );
      await _show(tester, fake, const QuizPage());

      await tester.tap(find.text('Sécuriser les lieux'));
      await tester.pump();
      await tester.tap(find.text('Valider'));
      await tester.pumpAndSettle();

      expect(find.textContaining('Bonne réponse', findRichText: true), findsOneWidget);
      expect(find.textContaining('demain', findRichText: true), findsNothing);
    });

    testWidgets('déjà répondu : le résultat est rappelé, on ne peut plus répondre', (tester) async {
      final fake = _Fake(today: QuizQuestion.fromJson(_question(answered: true, result: _result)));
      await _show(tester, fake, const QuizPage());

      expect(find.textContaining('Pas tout à fait', findRichText: true), findsOneWidget);
      expect(find.textContaining('On sécurise d\'abord les lieux.', findRichText: true), findsOneWidget);
      expect(find.text('Valider'), findsNothing);

      await tester.tap(find.text('Attendre'));
      await tester.pump();
      expect(fake.answers, isEmpty, reason: 'aucun envoi après une réponse déjà enregistrée');
    });

    testWidgets('aucune question : message neutre', (tester) async {
      await _show(tester, _Fake(), const QuizPage());

      expect(find.text('Pas de question pour le moment'), findsOneWidget);
    });

    testWidgets('journée changée pendant la lecture : la question est rechargée', (tester) async {
      final fake = _Fake(today: QuizQuestion.fromJson(_question()), answerError: const ApiException(kind: ApiErrorKind.business, statusCode: 409, code: 'QUIZ_QUESTION_NOT_CURRENT', message: 'Périmée.'));
      await _show(tester, fake, const QuizPage());
      fake.today = QuizQuestion.fromJson(_question(id: '13', text: 'Nouvelle question ?'));

      await tester.tap(find.text('Partir'));
      await tester.pump();
      await tester.tap(find.text('Valider'));
      await tester.pumpAndSettle();

      expect(find.text('Nouvelle question ?'), findsOneWidget);
      expect(find.text('Valider'), findsOneWidget);
    });

    testWidgets('autre erreur : message du serveur, on peut réessayer', (tester) async {
      final fake = _Fake(today: QuizQuestion.fromJson(_question()), answerError: const ApiException(kind: ApiErrorKind.validation, statusCode: 400, code: 'VALIDATION_FAILED', message: 'Choix inconnu.'));
      await _show(tester, fake, const QuizPage());

      await tester.tap(find.text('Partir'));
      await tester.pump();
      await tester.tap(find.text('Valider'));
      await tester.pumpAndSettle();

      expect(find.textContaining('Choix inconnu.', findRichText: true), findsOneWidget);
      expect(find.text('Valider'), findsOneWidget);
    });
  });

  group('Carte du quiz à l\'accueil', () {
    testWidgets('question à jouer : carte avec le thème et « Jouer »', (tester) async {
      await _show(tester, _Fake(engagement: Engagement.fromJson(_engagement()), today: QuizQuestion.fromJson(_question())), const EngagementCard());

      expect(find.text('Quiz du jour'), findsOneWidget);
      expect(find.textContaining('Prévention', findRichText: true), findsOneWidget);
      expect(find.textContaining('Jouer', findRichText: true), findsOneWidget);
    });

    testWidgets('déjà répondu : la série est rappelée, pas de « Jouer »', (tester) async {
      await _show(
        tester,
        _Fake(engagement: Engagement.fromJson(_engagement()), today: QuizQuestion.fromJson(_question(answered: true, result: _result))),
        const EngagementCard(),
      );

      expect(find.text('2 jours de suite'), findsOneWidget);
      expect(find.textContaining('Jouer', findRichText: true), findsNothing);
    });

    testWidgets('aucune question, fonction coupée ou erreur : rien ne s\'affiche', (tester) async {
      await _show(tester, _Fake(engagement: Engagement.fromJson(_engagement())), const EngagementCard());
      expect(find.text('Quiz du jour'), findsNothing);

      await _show(tester, _Fake(engagement: Engagement.fromJson(_engagement(quiz: false)), today: QuizQuestion.fromJson(_question())), const EngagementCard());
      expect(find.text('Quiz du jour'), findsNothing);

      await _show(tester, _Fake(engagement: Engagement.fromJson(_engagement()), todayError: true), const EngagementCard());
      expect(find.text('Quiz du jour'), findsNothing);
    });

    testWidgets('sans accord : seulement l\'invitation à l\'accorder', (tester) async {
      await _show(tester, _Fake(engagement: Engagement.fromJson(_engagement(accepted: false)), today: QuizQuestion.fromJson(_question())), const EngagementCard());

      expect(find.text('Activer'), findsOneWidget);
      expect(find.text('Quiz du jour'), findsNothing);
    });

    testWidgets('toucher la carte ouvre le quiz', (tester) async {
      await _show(tester, _Fake(engagement: Engagement.fromJson(_engagement()), today: QuizQuestion.fromJson(_question())), const EngagementCard());

      await tester.tap(find.text('Quiz du jour'));
      await tester.pumpAndSettle();

      expect(find.text('page ${AppRoutes.quiz}'), findsOneWidget);
    });
  });
}

Future<void> _show(WidgetTester tester, _Fake fake, Widget page) async {
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
      home: page is EngagementCard ? Scaffold(body: SingleChildScrollView(child: page)) : page,
    ),
  ));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 100));
}

/// Dépôt d'engagement de test : il répond ce qu'on lui demande et note les réponses envoyées.
class _Fake implements EngagementRepository {
  _Fake({Engagement? engagement, this.today, this.answer, this.answerError, this.todayError = false})
      : engagement = engagement ?? Engagement.fromJson(_engagement());

  Engagement engagement;
  QuizQuestion? today;
  final QuizAnswer? answer;
  final ApiException? answerError;
  final bool todayError;
  final List<(String, String)> answers = [];

  @override
  Future<Engagement> getEngagement() async => engagement;

  @override
  Future<Engagement> setConsent({required bool accepted, int? consentVersion}) async => engagement;

  @override
  Future<ProtectionScore> getProtectionScore() async => const ProtectionScore(score: 0, items: []);

  @override
  Future<List<AppBadge>> getBadges() async => const [];

  @override
  Future<QuizQuestion?> getQuizToday() async {
    if (todayError) throw ApiException.server();
    return today;
  }

  @override
  Future<QuizAnswer> answerQuiz({required String questionId, required String choiceId}) async {
    final err = answerError;
    if (err != null) throw err;
    answers.add((questionId, choiceId));
    return answer!;
  }
}
