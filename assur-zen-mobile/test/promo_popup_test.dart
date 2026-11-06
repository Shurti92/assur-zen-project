import 'package:assurzen_mobile/app/app_scope.dart';
import 'package:assurzen_mobile/app/session_controller.dart';
import 'package:assurzen_mobile/core/storage/local_store.dart';
import 'package:assurzen_mobile/data/api/api_promotion_repository.dart';
import 'package:assurzen_mobile/data/mock/mock_repositories.dart';
import 'package:assurzen_mobile/data/models/promo_popup.dart';
import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/features/promo/promo_popup.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'helpers/fonts.dart';

Map<String, dynamic> _json({String id = 'POP-1', String type = 'IMAGE', String? media = 'https://cdn.example/p.jpg', Map<String, dynamic> extra = const {}}) =>
    {'id': id, 'mediaType': type, 'mediaUrl': media, 'frequency': 'DAILY', 'version': 'v1', ...extra};

void main() {
  setUpAll(loadAppFonts);

  group('Lecture d\'une campagne', () {
    test('champs lus, délai borné à 30 s', () {
      final p = PromoPopup.tryParse(_json(extra: {'title': 'Voyage', 'linkUrl': 'https://assurzen.ci/v', 'linkLabel': 'En savoir plus', 'displayAfterSeconds': 99, 'frequency': 'ONCE'}))!;
      expect(p.id, 'POP-1');
      expect(p.mediaType, PromoMediaType.image);
      expect(p.frequency, PromoFrequency.once);
      expect(p.displayAfterSeconds, 30);
      expect(p.hasAction, isTrue);
    });

    test('ignorée : sans identifiant, sans média https, type inconnu', () {
      expect(PromoPopup.tryParse(_json(id: '')), isNull);
      expect(PromoPopup.tryParse(_json(media: 'ftp://cdn.example/p.jpg')), isNull);
      expect(PromoPopup.tryParse(_json(media: null)), isNull);
      expect(PromoPopup.tryParse(_json(type: 'GIF')), isNull);
    });

    test('un lien non https est écarté ; sans libellé, pas de bouton', () {
      final p = PromoPopup.tryParse(_json(extra: {'linkUrl': 'ftp://x.ci', 'linkLabel': 'Voir'}))!;
      expect(p.linkUrl, isNull);
      expect(p.hasAction, isFalse);
      expect(PromoPopup.tryParse(_json(extra: {'productId': 'moto'}))!.hasAction, isFalse);
      expect(PromoPopup.tryParse(_json(extra: {'productId': 'moto', 'linkLabel': 'Voir'}))!.hasAction, isTrue);
    });

    test('environnement local : le média http de la Gateway de développement est toléré', () {
      expect(PromoPopup.tryParse(_json(media: 'http://192.168.1.10:9191/public/promotions/media/a.jpg')), isNotNull);
    });

    test('liste : les campagnes invalides sont ignorées', () {
      final list = PromoPopup.listFromJson({'items': [_json(), _json(id: 'X', media: null), 'n\'importe quoi']});
      expect(list.map((p) => p.id), ['POP-1']);
      expect(PromoPopup.listFromJson(null), isEmpty);
    });
  });

  group('Choix selon la fréquence', () {
    final now = DateTime(2026, 11, 6, 10);
    PromoPopup p(PromoFrequency f, {String version = 'v1'}) =>
        PromoPopup(id: 'POP-1', mediaType: PromoMediaType.image, mediaUrl: 'https://x.ci/a.jpg', frequency: f, version: version);

    test('jamais vue : proposée', () => expect(pickPopup([p(PromoFrequency.once)], {}, now)?.id, 'POP-1'));
    test('une seule fois : plus jamais après', () => expect(pickPopup([p(PromoFrequency.once)], {'POP-1': 'v1|2026-11-01'}, now), isNull));
    test('quotidienne : pas deux fois le même jour', () {
      expect(pickPopup([p(PromoFrequency.daily)], {'POP-1': 'v1|2026-11-06'}, now), isNull);
      expect(pickPopup([p(PromoFrequency.daily)], {'POP-1': 'v1|2026-11-05'}, now)?.id, 'POP-1');
    });
    test('à chaque ouverture : toujours', () => expect(pickPopup([p(PromoFrequency.everyOpen)], {'POP-1': 'v1|2026-11-06'}, now)?.id, 'POP-1'));
    test('campagne modifiée : proposée à nouveau', () => expect(pickPopup([p(PromoFrequency.once, version: 'v2')], {'POP-1': 'v1|2026-11-01'}, now)?.id, 'POP-1'));
    test('la première éligible de la liste est choisie', () {
      const a = PromoPopup(id: 'A', mediaType: PromoMediaType.image, mediaUrl: 'https://x.ci/a.jpg', frequency: PromoFrequency.once, version: 'v1');
      const b = PromoPopup(id: 'B', mediaType: PromoMediaType.image, mediaUrl: 'https://x.ci/b.jpg', frequency: PromoFrequency.once, version: 'v1');
      expect(pickPopup([a, b], {'A': 'v1|2026-11-01'}, now)?.id, 'B');
    });
  });

  group('Dépôt API', () {
    ApiPromotionRepository repo(http.Client c) => ApiPromotionRepository(ApiClient(baseUrl: 'http://x', tokenProvider: () async => null, httpClient: c));

    test('liste lue', () async {
      final r = repo(MockClient((req) async {
        expect(req.url.path, endsWith('/public/promotions/popups'));
        expect(req.url.queryParameters['placement'], 'HOME');
        return http.Response('{"items":[{"id":"POP-1","mediaType":"IMAGE","mediaUrl":"https://x.ci/a.jpg","frequency":"DAILY"}]}', 200, headers: {'content-type': 'application/json'});
      }));
      expect((await r.getPopups()).single.id, 'POP-1');
    });

    test('route absente (404) ou panne : aucune campagne, sans erreur', () async {
      expect(await repo(MockClient((_) async => http.Response('{}', 404))).getPopups(), isEmpty);
      expect(await repo(MockClient((_) async => throw Exception('hors ligne'))).getPopups(), isEmpty);
    });

    test('mesure : un échec ne se voit pas', () async {
      await repo(MockClient((_) async => http.Response('', 500))).sendPopupEvent('POP-1', 'VIEW');
    });
  });

  group('Affichage', () {
    PromoPopup image({String? label = 'En savoir plus'}) => PromoPopup(
          id: 'POP-1',
          mediaType: PromoMediaType.image,
          mediaUrl: 'https://x.ci/a.jpg',
          frequency: PromoFrequency.daily,
          linkUrl: label == null ? null : 'https://assurzen.ci/v',
          linkLabel: label,
        );

    Future<void> show(WidgetTester tester, PromoPopup popup, {void Function(bool?)? onResult}) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(360, 740);
      addTearDown(tester.view.reset);
      await tester.pumpWidget(MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: TextButton(
                onPressed: () async {
                  final r = await showGeneralDialog<bool>(
                    context: context,
                    barrierDismissible: true,
                    barrierLabel: 'Fermer',
                    pageBuilder: (_, __, ___) => PromoPopupView(popup: popup),
                  );
                  onResult?.call(r);
                },
                child: const Text('ouvrir'),
              ),
            ),
          ),
        ),
      ));
      await tester.tap(find.text('ouvrir'));
      await tester.pump(const Duration(milliseconds: 300));
    }

    testWidgets('la croix ferme toujours le pop-up', (tester) async {
      bool? result = true;
      await show(tester, image(), onResult: (r) => result = r);

      expect(find.byTooltip('Fermer'), findsOneWidget);
      await tester.tap(find.byTooltip('Fermer'));
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.byType(PromoPopupView), findsNothing);
      expect(result, isFalse);
    });

    testWidgets('bouton du serveur : affiché avec son libellé, valide l\'action', (tester) async {
      bool? result;
      await show(tester, image(), onResult: (r) => result = r);

      expect(find.text('En savoir plus'), findsOneWidget);
      await tester.tap(find.text('En savoir plus'));
      await tester.pump(const Duration(milliseconds: 300));
      expect(result, isTrue);
    });

    testWidgets('sans lien : aucun bouton, seulement la fermeture', (tester) async {
      await show(tester, image(label: null));

      expect(find.text('En savoir plus'), findsNothing);
      expect(find.byTooltip('Fermer'), findsOneWidget);
    });

    testWidgets('le toucher en dehors ferme aussi', (tester) async {
      bool? result = true;
      await show(tester, image(), onResult: (r) => result = r);

      await tester.tapAt(const Offset(4, 4));
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.byType(PromoPopupView), findsNothing);
      expect(result, isNull);
    });
  });

  group('Pop-up à l\'accueil', () {
    testWidgets('aucune campagne : rien ne s\'affiche', (tester) async {
      SharedPreferences.setMockInitialValues({});
      final store = await LocalStore.open();
      PromoPopups.resetForTest();
      await tester.pumpWidget(AppScope(
        repos: MockRepositories.create(),
        session: SessionController(store: store, repos: MockRepositories.create()),
        store: store,
        child: MaterialApp(home: Builder(builder: (context) {
          WidgetsBinding.instance.addPostFrameCallback((_) => PromoPopups.maybeShow(context));
          return const Scaffold(body: Text('accueil'));
        })),
      ));
      await tester.pump(const Duration(seconds: 1));

      expect(find.byType(PromoPopupView), findsNothing);
      expect(find.text('accueil'), findsOneWidget);
    });
  });
}
