import 'package:assurzen_mobile/app/app_scope.dart';
import 'package:assurzen_mobile/app/session_controller.dart';
import 'package:assurzen_mobile/core/storage/local_store.dart';
import 'package:assurzen_mobile/data/mock/mock_data.dart';
import 'package:assurzen_mobile/data/mock/mock_repositories.dart';
import 'package:assurzen_mobile/data/models/moto_quote.dart';
import 'package:assurzen_mobile/data/repositories/repositories.dart';
import 'package:assurzen_mobile/features/quote/my_quotes_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'helpers/fonts.dart';

// Forme réelle d'un devis établi par un conseiller puis rattaché au compte (docs/api-contracts de la
// Gateway, section « Rapprochement ») : channel = BACKOFFICE, prospectName et prospectPhone renseignés.
Map<String, dynamic> _json({required String reference, required String channel}) => {
      'reference': reference,
      'status': 'CALCULE',
      'canAccept': true,
      'productKey': 'moto',
      'formulaId': 'tiers-simple-moto',
      'formulaName': 'Tiers Simple Moto',
      'vehicleType': 'MOTO',
      'cylinderCc': 125,
      'brand': 'Yamaha',
      'model': 'YBR',
      'registrationNumber': 'AB123',
      'channel': channel,
      'prospectName': channel == 'BACKOFFICE' ? 'Prospect Test' : null,
      'prospectPhone': channel == 'BACKOFFICE' ? '0700000000' : null,
      'durationMonths': 12,
      'effectiveDate': '2026-11-10',
      'expiryDate': '2027-11-09',
      'validUntil': '2026-11-10',
      'premium': {'netPremium': 6447, 'taxes': 925, 'accessories': 500, 'fga': 128, 'total': 8000, 'currency': 'XOF'},
      'guarantees': <Object>[],
    };

/// Dépôt de devis qui renvoie une liste fixe : seul ce dont l'écran « Mes devis » a besoin.
class _FakeQuotes implements MotoQuoteRepository {
  _FakeQuotes(this.quotes);

  final List<MotoQuote> quotes;

  @override
  Future<List<MotoQuote>> list() async => quotes;

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError('${invocation.memberName}');
}

Future<Widget> _app(MotoQuoteRepository quotes) async {
  SharedPreferences.setMockInitialValues({'lock_choice_done': true});
  final store = await LocalStore.open();
  final base = MockRepositories.create();
  final repos = Repositories(
    auth: base.auth,
    catalog: base.catalog,
    quotes: base.quotes,
    motoQuotes: quotes,
    payments: base.payments,
    contracts: base.contracts,
    claims: base.claims,
    notifications: base.notifications,
    support: base.support,
  );
  final session = SessionController(store: store, repos: repos)..updateUser(MockData.demoUser);
  return AppScope(repos: repos, session: session, store: store, child: const MaterialApp(home: MyQuotesPage()));
}

void main() {
  setUpAll(loadAppFonts);

  test('le canal du devis est lu, et un devis de conseiller est reconnu', () {
    final advisor = MotoQuote.fromJson(_json(reference: 'DEV-2026-000018', channel: 'BACKOFFICE'));
    final mobile = MotoQuote.fromJson(_json(reference: 'DEV-2026-000019', channel: 'MOBILE'));

    expect(advisor.channel, 'BACKOFFICE');
    expect(advisor.isFromAdvisor, isTrue);
    expect(mobile.isFromAdvisor, isFalse);
  });

  test('un devis sans canal (ancienne réponse) n\'est pas présenté comme établi par un conseiller', () {
    final json = _json(reference: 'DEV-2026-000020', channel: 'MOBILE')..remove('channel');

    expect(MotoQuote.fromJson(json).isFromAdvisor, isFalse);
  });

  test('l\'acceptation garde l\'origine du devis', () {
    final advisor = MotoQuote.fromJson(_json(reference: 'DEV-2026-000018', channel: 'BACKOFFICE'));

    expect(advisor.copyAccepted(DateTime(2026, 11, 4)).isFromAdvisor, isTrue);
  });

  testWidgets('Mes devis : la mention n\'apparaît que sur le devis établi par un conseiller', (tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(411, 891);
    addTearDown(tester.view.reset);
    final quotes = [
      MotoQuote.fromJson(_json(reference: 'DEV-2026-000018', channel: 'BACKOFFICE')),
      MotoQuote.fromJson(_json(reference: 'DEV-2026-000019', channel: 'MOBILE')),
    ];
    await tester.pumpWidget(await _app(_FakeQuotes(quotes)));
    await tester.pump(const Duration(milliseconds: 600));

    expect(find.text('DEV-2026-000018'), findsOneWidget);
    expect(find.text('DEV-2026-000019'), findsOneWidget);
    expect(find.text('Établi par un conseiller'), findsOneWidget); // une seule ligne sur deux
    // Le nom du prospect saisi par le conseiller n'est pas affiché.
    expect(find.textContaining('Prospect Test'), findsNothing);
  });

  testWidgets('détail : la ligne « Origine » n\'existe que pour un devis de conseiller', (tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(411, 891);
    addTearDown(tester.view.reset);
    final advisor = MotoQuote.fromJson(_json(reference: 'DEV-2026-000018', channel: 'BACKOFFICE'));
    final mobile = MotoQuote.fromJson(_json(reference: 'DEV-2026-000019', channel: 'MOBILE'));

    await tester.pumpWidget(await _app(_FakeQuotes([])));
    for (final entry in {advisor: 1, mobile: 0}.entries) {
      // Page recréée à chaque cas, comme à chaque ouverture dans l'application (sinon l'état précédent est gardé).
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(MaterialApp(home: MotoQuoteDetailPage(quote: entry.key)));
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('Origine'), findsNWidgets(entry.value), reason: entry.key.reference);
    }
  });
}
