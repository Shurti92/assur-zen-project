import 'package:assurzen_mobile/app/app_scope.dart';
import 'package:assurzen_mobile/app/session_controller.dart';
import 'package:assurzen_mobile/core/storage/local_store.dart';
import 'package:assurzen_mobile/core/widgets/az_selection.dart';
import 'package:assurzen_mobile/data/mock/mock_repositories.dart';
import 'package:assurzen_mobile/features/quote/moto_quote_page.dart';
import 'package:assurzen_mobile/features/quote/vtc_quote_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

// Tailles d'écran (dp) : petit téléphone, téléphone courant, grand téléphone, tablette, paysage.
const _screens = <String, Size>{
  'petit 320x568': Size(320, 568),
  'courant 360x640': Size(360, 640),
  'grand 411x891': Size(411, 891),
  'tablette 800x1280': Size(800, 1280),
  'paysage 891x411': Size(891, 411),
};

void _useScreen(WidgetTester tester, Size size) {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = size;
  addTearDown(tester.view.reset);
}

Widget _scaled(double textScale, Widget child) => MaterialApp(
      builder: (context, c) => MediaQuery(
        data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
        child: c!,
      ),
      home: child,
    );

void main() {
  group('AzChipFill : les pastilles occupent toute la largeur', () {
    // Libellés réels des écrans de devis.
    final cases = <String, (List<Object>, double, String Function(Object)?)>{
      'places 4 à 8': ([4, 5, 6, 7, 8], 52, null),
      'énergies': (['Essence', 'Diesel', 'Hybride', 'Électrique'], 110, null),
      'durées': ([1, 3, 6, 12], 120, (o) => '$o mois'),
      'oui / non': (['Oui', 'Non'], 100, null),
      'une seule durée': ([12], 120, (o) => '$o mois'),
    };

    for (final screen in _screens.entries) {
      for (final scale in [1.0, 1.3]) {
        testWidgets('${screen.key}, texte ${(scale * 100).round()} %', (tester) async {
          _useScreen(tester, screen.value);
          final width = screen.value.width;

          for (final c in cases.entries) {
            final (options, minWidth, labelOf) = c.value;
            await tester.pumpWidget(_scaled(
              scale,
              Scaffold(
                body: SingleChildScrollView(
                  padding: const EdgeInsets.all(20),
                  child: AzChipFill<Object>(
                    options: options,
                    selected: options.first,
                    minChipWidth: minWidth,
                    labelOf: labelOf,
                    onSelected: (_) {},
                  ),
                ),
              ),
            ));

            expect(tester.takeException(), isNull, reason: '${c.key} : aucun débordement');
            final rects = tester.widgetList(find.byType(AzChoiceChip)).map((w) => tester.getRect(find.byWidget(w))).toList();
            expect(rects.length, options.length, reason: '${c.key} : une puce par option');

            // Regroupe par ligne : chaque ligne va du bord gauche au bord droit du contenu.
            final rows = <double, List<Rect>>{};
            for (final r in rects) {
              rows.putIfAbsent(r.top.roundToDouble(), () => []).add(r);
            }
            for (final row in rows.values) {
              row.sort((a, b) => a.left.compareTo(b.left));
              expect(row.first.left, closeTo(20, 0.6), reason: '${c.key} : démarre au bord gauche');
              expect(row.last.right, closeTo(width - 20, 0.6), reason: '${c.key} : finit au bord droit');
              for (final r in row) {
                expect(r.height, closeTo(row.first.height, 0.6), reason: '${c.key} : même hauteur dans la ligne');
                expect(r.width, closeTo(row.first.width, 0.6), reason: '${c.key} : même largeur dans la ligne');
              }
            }
          }
        });
      }
    }

    testWidgets('lignes équilibrées : 4 puces sur 3 colonnes possibles donnent 2 lignes de 2', (tester) async {
      _useScreen(tester, const Size(360, 640));
      await tester.pumpWidget(_scaled(
        1,
        Scaffold(
          body: Padding(
            padding: const EdgeInsets.all(20),
            child: AzChipFill<String>(
              options: const ['A', 'B', 'C', 'D'],
              selected: 'A',
              minChipWidth: 90, // 3 colonnes tiendraient, mais 3 + 1 est déséquilibré
              onSelected: (_) {},
            ),
          ),
        ),
      ));

      final tops = tester.widgetList(find.byType(AzChoiceChip)).map((w) => tester.getTopLeft(find.byWidget(w)).dy.round()).toSet();
      expect(tops.length, 2);
    });
  });

  group('Écrans de devis : plusieurs tailles d\'écran', () {
    Future<Widget> app(Widget page, double scale) async {
      SharedPreferences.setMockInitialValues({});
      final store = await LocalStore.open();
      final repos = MockRepositories.create();
      final session = SessionController(store: store, repos: repos);
      return AppScope(repos: repos, session: session, store: store, child: _scaled(scale, page));
    }

    Future<void> scrollThrough(WidgetTester tester, void Function() visit) async {
      visit();
      for (var i = 0; i < 14; i++) {
        await tester.drag(find.byType(ListView).first, const Offset(0, -420));
        await tester.pump(const Duration(milliseconds: 100));
        visit();
      }
    }

    for (final screen in _screens.entries) {
      for (final scale in [1.0, 1.3]) {
        testWidgets('devis moto, ${screen.key}, texte ${(scale * 100).round()} %', (tester) async {
          _useScreen(tester, screen.value);
          await tester.pumpWidget(await app(const MotoQuotePage(title: 'Assurance Moto'), scale));
          await tester.pump(const Duration(milliseconds: 600));

          expect(find.text('Assurance Moto'), findsWidgets);
          // Sur grand écran le contenu est borné et centré, sur téléphone il occupe tout l'écran.
          final listWidth = tester.getSize(find.byType(ListView).first).width;
          expect(listWidth, lessThanOrEqualTo(640.5));
          expect(listWidth, closeTo(screen.value.width < 640 ? screen.value.width : 640, 0.6));

          await scrollThrough(tester, () {});
          expect(tester.takeException(), isNull);
        });

        testWidgets('devis VTC, ${screen.key}, texte ${(scale * 100).round()} %', (tester) async {
          _useScreen(tester, screen.value);
          await tester.pumpWidget(await app(const VtcQuotePage(title: 'Assurance Automobile VTC'), scale));
          await tester.pump(const Duration(milliseconds: 600));

          final listWidth = tester.getSize(find.byType(ListView).first).width;
          expect(listWidth, closeTo(screen.value.width < 640 ? screen.value.width : 640, 0.6));

          await scrollThrough(tester, () {});
          expect(tester.takeException(), isNull);
        });
      }
    }

    testWidgets('devis moto : des placeholders dans les champs', (tester) async {
      _useScreen(tester, const Size(411, 891));
      await tester.pumpWidget(await app(const MotoQuotePage(title: 'Assurance Moto'), 1));
      await tester.pump(const Duration(milliseconds: 600));

      final seen = <String>{};
      await scrollThrough(tester, () {
        for (final t in tester.widgetList<TextField>(find.byType(TextField))) {
          final h = t.decoration?.hintText;
          if (h != null) seen.add(h);
        }
      });

      expect(seen, containsAll(['Ex. : 125', 'Tapez pour chercher une marque', 'Saisissez le modèle', 'Ex. : 1234 AB 01', 'Saisissez le numéro de châssis']));
    });

    testWidgets('devis VTC : des placeholders dans les champs, et des durées sans prix', (tester) async {
      _useScreen(tester, const Size(411, 891));
      await tester.pumpWidget(await app(const VtcQuotePage(title: 'Assurance Automobile VTC'), 1));
      await tester.pump(const Duration(milliseconds: 600));

      final seen = <String>{};
      await scrollThrough(tester, () {
        for (final t in tester.widgetList<TextField>(find.byType(TextField))) {
          final h = t.decoration?.hintText;
          if (h != null) seen.add(h);
        }
      });

      expect(
        seen,
        containsAll([
          'Tapez pour chercher une marque',
          'Saisissez le modèle',
          'Ex. : 5',
          'AAAA',
          'Tapez pour chercher une plateforme',
          'Ex. : 1234 AB 01',
          'Saisissez le numéro de châssis',
        ]),
      );
      // Les durées s'affichent sans prix (demande de l'utilisateur).
      expect(find.textContaining('FCFA'), findsNothing);
    });
  });
}
