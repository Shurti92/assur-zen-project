import 'package:assurzen_mobile/app/app_scope.dart';
import 'package:assurzen_mobile/app/session_controller.dart';
import 'package:assurzen_mobile/core/storage/local_store.dart';
import 'package:assurzen_mobile/core/utils/formatters.dart';
import 'package:assurzen_mobile/data/api/api_loyalty_repository.dart';
import 'package:assurzen_mobile/data/mock/mock_data.dart';
import 'package:assurzen_mobile/data/mock/mock_repositories.dart';
import 'package:assurzen_mobile/data/models/loyalty.dart';
import 'package:assurzen_mobile/data/repositories/repositories.dart';
import 'package:assurzen_mobile/features/loyalty/loyalty_pages.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'helpers/fonts.dart';

class _FakeLoyalty implements LoyaltyRepository {
  _FakeLoyalty({this.joined = true});

  bool joined;
  String? acceptedVersion;

  LoyaltyOverview get _overview => joined
      ? const LoyaltyOverview(
          consent: LoyaltyConsent(accepted: true, currentVersion: 'v1'),
          balance: 320,
          level: LoyaltyLevel(code: 'BRONZE', label: 'Bronze', qualifyingPoints: 320, next: LoyaltyNextLevel(label: 'Silver', threshold: 500, pointsToGo: 180)),
          expiring: LoyaltyExpiring(points: 40),
        )
      : const LoyaltyOverview(consent: LoyaltyConsent(accepted: false, currentVersion: 'v1', text: 'Conditions du programme.'));

  @override
  Future<LoyaltyOverview?> getOverview() async => _overview;

  @override
  Future<LoyaltyOverview?> setConsent({required bool accepted, String? version}) async {
    acceptedVersion = version;
    joined = accepted;
    return _overview;
  }

  @override
  Future<LoyaltyRules> getRules() async => const LoyaltyRules(
        expiryMonths: 12,
        actions: [
          LoyaltyAction(code: 'RENEWAL_EARLY', label: 'Renouveler avant l’échéance', points: 200, limit: '1 fois par contrat'),
          LoyaltyAction(code: 'QUIZ', label: 'Bonne réponse au quiz du jour', points: 5),
        ],
        levels: [LoyaltyLevelRule(code: 'BRONZE', label: 'Bronze', threshold: 0, perks: ['Accès au programme'])],
      );

  LoyaltyDiscounts? discounts;

  @override
  Future<LoyaltyDiscounts?> getDiscounts() async => discounts;

  @override
  Future<LoyaltyHistory> getHistory({int page = 0, int size = 20}) async => LoyaltyHistory(
        total: 2,
        items: [
          LoyaltyEntry(id: '1', date: DateTime(2026, 11, 5), type: LoyaltyEntryType.earn, label: 'Quiz du jour', points: 5),
          LoyaltyEntry(id: '2', date: DateTime(2026, 10, 1), type: LoyaltyEntryType.expire, label: 'Points expirés', points: -30),
        ],
      );
}

Repositories _repos(LoyaltyRepository loyalty) {
  final base = MockRepositories.create();
  return Repositories(
    auth: base.auth,
    catalog: base.catalog,
    quotes: base.quotes,
    motoQuotes: base.motoQuotes,
    payments: base.payments,
    contracts: base.contracts,
    claims: base.claims,
    notifications: base.notifications,
    support: base.support,
    engagement: base.engagement,
    loyalty: loyalty,
  );
}

Future<void> _pump(WidgetTester tester, Repositories repos, Widget home, {Size size = const Size(360, 800), double scale = 1.0}) async {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = size;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({'lock_choice_done': true});
  final store = await LocalStore.open();
  final session = SessionController(store: store, repos: repos)..updateUser(MockData.demoUser);
  await tester.pumpWidget(
    AppScope(
      repos: repos,
      session: session,
      store: store,
      child: MaterialApp(
        builder: (context, child) => MediaQuery(data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)), child: child!),
        home: home,
      ),
    ),
  );
  await tester.pump(const Duration(milliseconds: 600));
}

void main() {
  setUpAll(loadAppFonts);

  test('la synthèse du serveur est lue, rien n’est inventé', () {
    final o = ApiLoyaltyRepository.overviewFromJson({
      'enabled': true,
      'consent': {'accepted': true, 'currentVersion': 'v1'},
      'balance': 320,
      'level': {
        'code': 'BRONZE',
        'label': 'Bronze',
        'qualifyingPoints': 320,
        'nextLevel': {'code': 'SILVER', 'label': 'Silver', 'threshold': 500, 'pointsToGo': 180},
      },
      'expiring': {'points': 40, 'date': '2027-01-15'},
    })!;
    expect(o.joined, isTrue);
    expect(o.balance, 320);
    expect(o.level?.next?.pointsToGo, 180);
    expect(o.expiring?.points, 40);

    // Sans adhésion : ni solde ni niveau.
    final before = ApiLoyaltyRepository.overviewFromJson({
      'enabled': true,
      'consent': {'accepted': false, 'currentVersion': 'v1', 'text': 'Conditions'},
    })!;
    expect(before.joined, isFalse);
    expect(before.balance, isNull);
    expect(before.consent.canAccept, isTrue);

    // Programme coupé ou corps inattendu : masqué.
    expect(ApiLoyaltyRepository.overviewFromJson({'enabled': false}), isNull);
    expect(ApiLoyaltyRepository.overviewFromJson('x'), isNull);
    // Aucun point qui expire : pas d'alerte.
    expect(ApiLoyaltyRepository.overviewFromJson({'consent': {'accepted': true}, 'balance': 0, 'expiring': {'points': 0}})!.expiring, isNull);
  });

  test('l’historique est signé et les types inconnus sont des gains', () {
    final h = ApiLoyaltyRepository.historyFromJson({
      'total': 3,
      'items': [
        {'id': 'a', 'date': '2026-11-05T10:00:00Z', 'type': 'EARN', 'label': 'Quiz', 'points': 5},
        {'id': 'b', 'date': '2026-10-01T10:00:00Z', 'type': 'EXPIRE', 'label': 'Expirés', 'points': -30},
        {'id': 'c', 'date': '2026-09-01T10:00:00Z', 'type': 'AUTRE', 'label': 'Autre', 'points': 1},
      ],
    });
    expect(h.total, 3);
    expect(h.items[1].type, LoyaltyEntryType.expire);
    expect(h.items[1].points, -30);
    expect(h.items[2].type, LoyaltyEntryType.earn);
  });

  for (final (size, scale) in [(const Size(320, 640), 1.3), (const Size(360, 800), 1.0)]) {
    testWidgets('la page Zen Points tient sur ${size.width.toInt()} dp, texte ${(scale * 100).toInt()} %', (tester) async {
      await _pump(tester, _repos(_FakeLoyalty()), const LoyaltyPage(), size: size, scale: scale);
      expect(find.text('320 points'), findsOneWidget);
      expect(find.textContaining('Niveau Bronze'), findsOneWidget);
      expect(find.textContaining('180 points pour passer Silver'), findsOneWidget);
      expect(find.text('Comment gagner'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('sans adhésion : invitation, conditions du serveur, puis adhésion', (tester) async {
    final fake = _FakeLoyalty(joined: false);
    await _pump(tester, _repos(fake), const LoyaltyPage());
    expect(find.text('Rejoignez les Zen Points'), findsOneWidget);
    expect(find.text('320 points'), findsNothing);
    await tester.tap(find.text('Rejoindre'));
    await tester.pump(const Duration(milliseconds: 600));
    expect(find.text('Conditions du programme.'), findsOneWidget);
    await tester.ensureVisible(find.text('J’accepte et je rejoins'));
    await tester.pump(const Duration(milliseconds: 300));
    await tester.tap(find.text('J’accepte et je rejoins'), warnIfMissed: true);
    await tester.pump(const Duration(milliseconds: 600));
    await tester.pump(const Duration(milliseconds: 600));
    expect(fake.acceptedVersion, 'v1');
    expect(find.text('320 points'), findsOneWidget);
  });

  test('les remises sont lues telles que le serveur les donne', () {
    final d = ApiLoyaltyRepository.discountsFromJson({
      'enabled': true,
      'levelCode': 'SILVER',
      'levelLabel': 'Silver',
      'tiers': [
        {'productKey': 'moto', 'minMonths': 1, 'maxMonths': 12, 'percent': 10},
        {'productKey': 'automobile-vtc', 'minMonths': 3, 'maxMonths': 6, 'percent': 7.5},
        {'productKey': 'automobile-vtc', 'minMonths': 12, 'maxMonths': 12, 'percent': 0},
      ],
    })!;
    expect(d.tiers.length, 2); // le palier à 0 % est ignoré
    expect(d.tiers[0].durationLabel, '1 à 12 mois');
    expect(d.tiers[1].durationLabel, '3 à 6 mois');
    expect(formatPercent(d.tiers[1].percent), '7,5');
    expect(formatPercent(10), '10');

    // Coupées, Bronze (aucun palier) ou corps inattendu : rien à montrer.
    expect(ApiLoyaltyRepository.discountsFromJson({'enabled': false}), isNull);
    expect(ApiLoyaltyRepository.discountsFromJson({'enabled': false, 'levelCode': 'BRONZE', 'tiers': []}), isNull);
    expect(ApiLoyaltyRepository.discountsFromJson({'enabled': true, 'tiers': []}), isNull);
    expect(ApiLoyaltyRepository.discountsFromJson('x'), isNull);
  });

  testWidgets('sans remise du serveur (Bronze, coupées) : aucun bloc', (tester) async {
    await _pump(tester, _repos(_FakeLoyalty()), const LoyaltyPage(), size: const Size(360, 1600));
    expect(find.text('320 points'), findsOneWidget);
    expect(find.text('Vos remises assurance auto'), findsNothing);
  });

  testWidgets('les remises du serveur s’affichent dans la page Zen Points', (tester) async {
    final fake = _FakeLoyalty()
      ..discounts = const LoyaltyDiscounts(levelLabel: 'Silver', tiers: [
        LoyaltyDiscountTier(productKey: 'moto', minMonths: 1, maxMonths: 12, percent: 10),
        LoyaltyDiscountTier(productKey: 'automobile-vtc', minMonths: 12, maxMonths: 12, percent: 20),
      ]);
    await _pump(tester, _repos(fake), const LoyaltyPage(), size: const Size(360, 1600));
    expect(find.text('Vos remises assurance auto'), findsOneWidget);
    expect(find.text('−10 %'), findsOneWidget);
    expect(find.text('Durée 12 mois'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('programme absent : message neutre, aucun point', (tester) async {
    await _pump(tester, _repos(const NoLoyalty()), const LoyaltyPage());
    expect(find.text('Programme indisponible'), findsOneWidget);
  });

  testWidgets('règles et historique s’affichent', (tester) async {
    await _pump(tester, _repos(_FakeLoyalty()), const LoyaltyRulesPage(), size: const Size(320, 640), scale: 1.3);
    expect(find.text('Tous les gestes'), findsOneWidget);
    expect(find.text('+200'), findsOneWidget);
    expect(find.text('Les points durent 12 mois.'), findsOneWidget);
    expect(tester.takeException(), isNull);

    // Durée à 0 (pas d'expiration) : aucune phrase.
    expect(ApiLoyaltyRepository.rulesFromJson({'expiryMonths': 0}).expiryMonths, 0);

    await _pump(tester, _repos(_FakeLoyalty()), const LoyaltyHistoryPage(), size: const Size(320, 640), scale: 1.3);
    expect(find.text('Points expirés'), findsOneWidget);
    expect(find.text('-30'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
