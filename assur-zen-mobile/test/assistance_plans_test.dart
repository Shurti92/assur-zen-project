import 'package:assurzen_mobile/core/widgets/az_layout.dart';
import 'package:assurzen_mobile/data/api/api_catalog_repository.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:assurzen_mobile/features/catalog/product_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'helpers/fonts.dart';

// Réponse réelle de la Gateway (GET /public/products/auto), relevée sur le gateway de développement :
// docs/api-contracts/admin-mobile.md, section 11.
Map<String, dynamic> _auto(List<Object?>? plans) => {
      'id': 'auto',
      'title': 'Assurance Auto',
      'shortName': 'AssurZen Auto',
      'family': 'VEHICLE',
      'onlineSubscription': false,
      if (plans != null) 'assistancePlans': plans,
    };

final _plansFromServer = <Object?>[
  {
    'key': 'gratuit',
    'name': 'Gratuit',
    'price': 0,
    'priceLabel': 'Gratuit',
    'minVehicleAge': null,
    'maxVehicleAge': null,
    'benefit': true,
    'recommended': false,
    'sections': [
      {
        'title': 'En cas d\'accident',
        'items': [
          {'text': 'Aide à l\'établissement du constat amiable (facilitateur)', 'condition': null},
        ],
      },
    ],
  },
  {
    'key': 'confort',
    'name': 'Confort',
    'price': 38000,
    'priceLabel': '+38 000 F',
    'minVehicleAge': null,
    'maxVehicleAge': null,
    'benefit': false,
    'recommended': false,
    'sections': [
      {
        'title': 'En cas de panne',
        'items': [
          {'text': 'Réparation sur la place', 'condition': 'Véhicule de 0-10 ans'},
          {'text': 'Remorquage', 'condition': null},
        ],
      },
    ],
  },
  {
    'key': 'relax',
    'name': 'Relax',
    'price': 55000,
    'priceLabel': '55 000 F',
    'minVehicleAge': 0,
    'maxVehicleAge': 5,
    'benefit': false,
    'recommended': true,
    'sections': [
      {
        'title': 'En cas de vol',
        'items': [
          {'text': 'Prestation de vol', 'condition': null},
        ],
      },
    ],
  },
];

void main() {
  setUpAll(loadAppFonts);

  group('lecture de la réponse du catalogue', () {
    test('les niveaux sont lus dans l\'ordre du serveur, avec leurs champs', () {
      final p = ApiCatalogRepository.productFromJson(_auto(_plansFromServer));

      expect(p.assistancePlans.map((x) => x.key), ['gratuit', 'confort', 'relax']);
      final gratuit = p.assistancePlans[0];
      expect(gratuit.price, 0);
      expect(gratuit.priceLabel, 'Gratuit');
      expect(gratuit.benefit, isTrue);
      expect(gratuit.minVehicleAge, isNull);
      expect(gratuit.maxVehicleAge, isNull);
      final confort = p.assistancePlans[1];
      expect(confort.priceLabel, '+38 000 F');
      expect(confort.sections.single.items.first.condition, 'Véhicule de 0-10 ans');
      expect(confort.sections.single.items.last.condition, isNull);
      final relax = p.assistancePlans[2];
      expect(relax.minVehicleAge, 0);
      expect(relax.maxVehicleAge, 5);
      expect(relax.recommended, isTrue);
    });

    test('un produit sans le champ, ou avec une liste vide (moto, VTC) : aucun niveau', () {
      expect(ApiCatalogRepository.productFromJson(_auto(null)).assistancePlans, isEmpty);
      expect(ApiCatalogRepository.productFromJson(_auto([])).assistancePlans, isEmpty);
    });

    test('rien n\'est inventé : un niveau sans clé ou sans nom, une section vide sont ignorés', () {
      final p = ApiCatalogRepository.productFromJson(_auto([
        {'key': 'sans-nom', 'price': 1, 'sections': []},
        {'name': 'Sans clé', 'price': 1, 'sections': []},
        'pas un objet',
        {
          'key': 'ok',
          'name': 'Valide',
          'price': 100,
          'sections': [
            {'title': '', 'items': [{'text': 'titre vide'}]},
            {'title': 'Sans prestation', 'items': []},
            {'title': 'Bonne', 'items': [{'text': ''}, {'text': 'Une prestation'}]},
          ],
        },
      ]));

      expect(p.assistancePlans.map((x) => x.key), ['ok']);
      expect(p.assistancePlans.single.sections.map((s) => s.title), ['Bonne']);
      expect(p.assistancePlans.single.sections.single.items.map((i) => i.text), ['Une prestation']);
    });
  });

  group('libellés', () {
    AssistancePlan plan({int price = 0, String? label, int? min, int? max}) =>
        AssistancePlan(key: 'k', name: 'N', price: price, priceLabel: label, minVehicleAge: min, maxVehicleAge: max);

    test('le libellé du serveur est affiché tel quel ; sinon formaté depuis le prix, « Gratuit » à 0', () {
      expect(assistancePriceLabel(plan(price: 38000, label: '+38 000 F')), '+38 000 F');
      expect(assistancePriceLabel(plan(price: 0)), 'Gratuit');
      expect(assistancePriceLabel(plan(price: 38000)), contains('38'));
    });

    test('l\'unité du prix n\'est jamais ajoutée (elle n\'est pas connue)', () {
      final label = assistancePriceLabel(plan(price: 55000));
      expect(label.toLowerCase(), isNot(contains('an')));
      expect(label.toLowerCase(), isNot(contains('contrat')));
    });

    test('éligibilité par âge du véhicule : informative, absente sans borne', () {
      expect(assistanceEligibility(plan()), isNull);
      expect(assistanceEligibility(plan(min: 0, max: 5)), 'Véhicule de 0 à 5 ans');
      expect(assistanceEligibility(plan(min: 2, max: 2)), 'Véhicule de 2 ans');
      expect(assistanceEligibility(plan(max: 1)), 'Véhicule de 1 an maximum');
      expect(assistanceEligibility(plan(max: 8)), 'Véhicule de 8 ans maximum');
      expect(assistanceEligibility(plan(min: 3)), 'Véhicule de 3 ans minimum');
    });
  });

  group('affichage', () {
    List<AssistancePlan> real() => ApiCatalogRepository.productFromJson(_auto(_plansFromServer)).assistancePlans;

    Future<void> pump(WidgetTester tester, List<AssistancePlan> plans, {Size size = const Size(411, 891), double scale = 1}) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = size;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
          child: AzScreenFrame(child: child!),
        ),
        home: Scaffold(body: SingleChildScrollView(padding: const EdgeInsets.all(20), child: AssistancePlans(plans: plans))),
      ));
      await tester.pump();
    }

    testWidgets('une carte par niveau, dans l\'ordre du serveur, prix et éligibilité visibles', (tester) async {
      await pump(tester, real());

      expect(find.text('Niveaux d’assistance'), findsOneWidget);
      final y = [for (final n in ['Gratuit', 'Confort', 'Relax']) tester.getTopLeft(find.text(n).first).dy];
      expect(y, orderedEquals([...y]..sort()));
      expect(find.text('+38 000 F'), findsOneWidget);
      expect(find.text('55 000 F'), findsOneWidget);
      expect(find.text('Véhicule de 0 à 5 ans'), findsOneWidget);
    });

    testWidgets('« Recommandé » et « Avantage gratuit » n\'apparaissent que sur les niveaux concernés', (tester) async {
      await pump(tester, real());

      expect(find.text('Recommandé'), findsOneWidget); // Relax seulement
      expect(find.text('Avantage gratuit'), findsOneWidget); // Gratuit seulement
    });

    testWidgets('les prestations sont repliées, puis s\'affichent avec leur condition', (tester) async {
      await pump(tester, real());
      expect(find.text('Réparation sur la place'), findsNothing);

      await tester.tap(find.text('Confort'));
      await tester.pumpAndSettle();

      expect(find.text('En cas de panne'), findsOneWidget);
      expect(find.text('Réparation sur la place'), findsOneWidget);
      expect(find.text('Véhicule de 0-10 ans'), findsOneWidget);
      expect(find.text('Remorquage'), findsOneWidget);
    });

    testWidgets('aucun niveau : la rubrique n\'est pas affichée', (tester) async {
      await pump(tester, const []);

      expect(find.text('Niveaux d’assistance'), findsNothing);
    });

    testWidgets('aucun niveau n\'est masqué ni grisé selon l\'âge du véhicule', (tester) async {
      await pump(tester, real());

      expect(find.byType(Opacity), findsNothing);
      expect(find.text('Relax'), findsOneWidget);
    });
  });

  group('mise en page : plusieurs tailles d\'écran, texte à 100 % et 130 %, longueurs maximales du contrat', () {
    // Limites du contrat : name 60, priceLabel 60, title 60, text 200, condition 200.
    String longText(int n) => List.generate(n, (i) => i % 7 == 6 ? ' ' : 'x').join();

    final extreme = [
      for (var i = 0; i < 4; i++)
        AssistancePlan(
          key: 'p$i',
          name: 'Niveau ${longText(52)}',
          price: 80000,
          priceLabel: 'Prix ${longText(55)}',
          minVehicleAge: 0,
          maxVehicleAge: 5,
          benefit: i == 0,
          recommended: i == 1,
          sections: [
            for (var s = 0; s < 4; s++)
              AssistanceSection('En cas de ${longText(50)}', [
                for (var k = 0; k < 3; k++) AssistanceItem(longText(200), condition: longText(200)),
              ]),
          ],
        ),
    ];

    const screens = <String, Size>{
      'petit 320x568': Size(320, 568),
      'courant 360x640': Size(360, 640),
      'grand 411x891': Size(411, 891),
      'tablette 800x1280': Size(800, 1280),
      'paysage 891x411': Size(891, 411),
    };

    for (final screen in screens.entries) {
      for (final scale in [1.0, 1.3]) {
        testWidgets('${screen.key}, texte ${(scale * 100).round()} %', (tester) async {
          tester.view.devicePixelRatio = 1;
          tester.view.physicalSize = screen.value;
          addTearDown(tester.view.reset);
          await tester.pumpWidget(MaterialApp(
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
              child: AzScreenFrame(child: child!),
            ),
            home: Scaffold(body: SingleChildScrollView(padding: const EdgeInsets.all(20), child: AssistancePlans(plans: extreme))),
          ));
          await tester.pump();
          expect(tester.takeException(), isNull, reason: 'cartes repliées');

          // Déplie chaque carte : aucun débordement avec les textes les plus longs.
          for (var i = 0; i < 4; i++) {
            final tiles = find.byType(ExpansionTile);
            await tester.ensureVisible(tiles.at(i));
            await tester.tap(tiles.at(i), warnIfMissed: false);
            await tester.pumpAndSettle();
          }
          expect(tester.takeException(), isNull, reason: 'cartes dépliées');
        });
      }
    }
  });
}
