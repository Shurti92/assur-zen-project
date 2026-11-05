import 'package:assurzen_mobile/app/app_scope.dart';
import 'package:assurzen_mobile/app/routes.dart';
import 'package:assurzen_mobile/app/session_controller.dart';
import 'package:assurzen_mobile/core/storage/local_store.dart';
import 'package:assurzen_mobile/core/widgets/az_layout.dart';
import 'package:assurzen_mobile/core/widgets/az_selection.dart';
import 'package:assurzen_mobile/data/mock/mock_data.dart';
import 'package:assurzen_mobile/data/mock/mock_repositories.dart';
import 'package:assurzen_mobile/features/auth/forgot_pin_page.dart';
import 'package:assurzen_mobile/features/auth/lock_page.dart';
import 'package:assurzen_mobile/features/auth/login_page.dart';
import 'package:assurzen_mobile/features/auth/otp_page.dart';
import 'package:assurzen_mobile/features/auth/register_page.dart';
import 'package:assurzen_mobile/features/catalog/finder_page.dart';
import 'package:assurzen_mobile/features/catalog/product_page.dart';
import 'package:assurzen_mobile/features/claims/claim_declare_page.dart';
import 'package:assurzen_mobile/features/notifications/notifications_page.dart';
import 'package:assurzen_mobile/features/profile/profile_page.dart';
import 'package:assurzen_mobile/features/quote/my_quotes_page.dart';
import 'package:assurzen_mobile/features/quote/quote_flow_page.dart';
import 'package:assurzen_mobile/features/shell/main_shell.dart';
import 'package:assurzen_mobile/features/support/support_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'helpers/fonts.dart';
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

/// Reproduit le `builder` de l'application : texte agrandi borné, puis cadre d'écran.
Widget _app(double textScale, Widget home) => MaterialApp(
      builder: (context, child) {
        final mq = MediaQuery.of(context);
        return MediaQuery(
          data: mq.copyWith(textScaler: TextScaler.linear(textScale)),
          child: AzScreenFrame(child: child ?? const SizedBox.shrink()),
        );
      },
      home: home,
    );

void main() {
  setUpAll(loadAppFonts);

  group('AzScreenFrame', () {
    Future<void> pumpFrame(WidgetTester tester, Size size, {EdgeInsets padding = EdgeInsets.zero}) async {
      _useScreen(tester, size);
      tester.view.padding = FakeViewPadding(left: padding.left, right: padding.right, top: padding.top, bottom: padding.bottom);
      addTearDown(tester.view.resetPadding);
      await tester.pumpWidget(MaterialApp(
        builder: (context, child) => AzScreenFrame(child: child!),
        home: Builder(
          builder: (context) {
            final mq = MediaQuery.of(context);
            return Scaffold(
              body: Center(child: Text('largeur ${mq.size.width.round()} gauche ${mq.padding.left.round()}', key: const Key('mq'))),
            );
          },
        ),
      ));
    }

    testWidgets('téléphone : aucun changement, la page occupe tout l\'écran', (tester) async {
      await pumpFrame(tester, const Size(411, 891));
      expect(tester.getSize(find.byType(Scaffold)).width, 411);
      expect(find.text('largeur 411 gauche 0'), findsOneWidget);
    });

    testWidgets('tablette : colonne centrée de 640 dp, et la page voit cette largeur', (tester) async {
      await pumpFrame(tester, const Size(800, 1280));
      final r = tester.getRect(find.byType(Scaffold));
      expect(r.width, 640);
      expect(r.left, 80); // centrée
      expect(find.text('largeur 640 gauche 0'), findsOneWidget);
    });

    testWidgets('paysage : les échancrures latérales ne décalent pas la colonne', (tester) async {
      await pumpFrame(tester, const Size(891, 411), padding: const EdgeInsets.only(left: 48, right: 48));
      expect(tester.getRect(find.byType(Scaffold)).width, 640);
      expect(find.text('largeur 640 gauche 0'), findsOneWidget);
    });
  });

  group('Autres écrans : plusieurs tailles d\'écran, texte à 100 % et 130 %', () {
    Future<Widget> app(Widget Function(String productId) page, double scale) async {
      SharedPreferences.setMockInitialValues({'lock_choice_done': true});
      final store = await LocalStore.open();
      final repos = MockRepositories.create();
      final session = SessionController(store: store, repos: repos)..updateUser(MockData.demoUser);
      // Identifiant pris dans les données de démonstration : attendre le dépôt bloquerait l'horloge simulée.
      return AppScope(repos: repos, session: session, store: store, child: _app(scale, page(MockData.products.first.id)));
    }

    final pages = <String, Widget Function(String productId)>{
      'connexion': (_) => const LoginPage(),
      'inscription': (_) => const RegisterPage(args: RegisterArgs()),
      'PIN oublié': (_) => const ForgotPinPage(),
      'code SMS': (_) => const OtpPage(
            args: OtpArgs(phone: '0708123456', purpose: OtpFlow.login, challengeToken: 'jeton', maskedPhone: '07******56'),
          ),
      'verrouillage': (_) => const LockPage(),
      'fiche produit': (id) => ProductPage(productId: id),
      'trouver mon assurance': (_) => const FinderPage(),
      'déclaration de sinistre': (_) => const ClaimDeclarePage(contractId: null),
      'assistance': (_) => const SupportPage(),
      'notifications': (_) => const NotificationsPage(),
      'profil': (_) => const Scaffold(body: ProfilePage()),
      'devis générique': (id) => QuoteFlowPage(args: QuoteArgs(productId: id)),
      'accueil et onglets': (_) => const MainShell(initialTab: ShellTab.home),
    };

    for (final screen in _screens.entries) {
      for (final scale in [1.0, 1.3]) {
        for (final p in pages.entries) {
          testWidgets('${p.key}, ${screen.key}, texte ${(scale * 100).round()} %', (tester) async {
            _useScreen(tester, screen.value);
            await tester.pumpWidget(await app(p.value, scale));
            await tester.pump(const Duration(milliseconds: 800));

            // Sur grand écran la page est dans une colonne de 640 dp ; sur téléphone elle prend tout l'écran.
            final width = tester.getSize(find.byType(Scaffold).first).width;
            expect(width, closeTo(screen.value.width < 640 ? screen.value.width : 640, 0.6));

            final scrollables = find.byType(Scrollable);
            if (scrollables.evaluate().isNotEmpty) {
              for (var i = 0; i < 10; i++) {
                await tester.drag(scrollables.first, const Offset(0, -450), warnIfMissed: false);
                await tester.pump(const Duration(milliseconds: 100));
              }
            }
            expect(tester.takeException(), isNull);

            // Démontage propre : arrête les minuteries (décompte du code SMS…).
            await tester.pumpWidget(const SizedBox.shrink());
            // Laisse s'écouler les latences simulées du mode démonstration.
            await tester.pump(const Duration(seconds: 5));
          });
        }
      }
    }
  });

  group('Pastilles des autres écrans', () {
    testWidgets('notifications : « Toutes » et « Non lues » se partagent la largeur', (tester) async {
      _useScreen(tester, const Size(360, 640));
      SharedPreferences.setMockInitialValues({'lock_choice_done': true});
      final store = await LocalStore.open();
      final repos = MockRepositories.create();
      final session = SessionController(store: store, repos: repos)..updateUser(MockData.demoUser);
      await tester.pumpWidget(AppScope(repos: repos, session: session, store: store, child: _app(1, const NotificationsPage())));
      await tester.pump(const Duration(milliseconds: 800));

      final rects = tester.widgetList(find.byType(AzChoiceChip)).map((w) => tester.getRect(find.byWidget(w))).toList()
        ..sort((a, b) => a.left.compareTo(b.left));
      expect(rects.length, 2);
      expect(rects.first.left, closeTo(20, 0.6));
      expect(rects.last.right, closeTo(340, 0.6));
      expect(rects.first.width, closeTo(rects.last.width, 0.6));
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });

  group('Accueil : accès à Mes devis', () {
    Future<void> openHome(WidgetTester tester, {required bool loggedIn}) async {
      _useScreen(tester, const Size(360, 800));
      SharedPreferences.setMockInitialValues({'lock_choice_done': true});
      final store = await LocalStore.open();
      final repos = MockRepositories.create();
      final session = SessionController(store: store, repos: repos);
      if (loggedIn) session.updateUser(MockData.demoUser);
      // Comme l'application : les pages poussées par nom passent par les vraies routes.
      await tester.pumpWidget(AppScope(
        repos: repos,
        session: session,
        store: store,
        child: const MaterialApp(onGenerateRoute: AppRoutes.onGenerateRoute, home: MainShell(initialTab: ShellTab.home)),
      ));
      await tester.pump(const Duration(milliseconds: 800));
    }

    testWidgets('client connecté : la tuile « Mes devis » ouvre la liste des devis', (tester) async {
      await openHome(tester, loggedIn: true);

      // La page d'accueil est une liste : on la fait défiler jusqu'à la tuile.
      await tester.scrollUntilVisible(find.text('Mes devis'), 200, scrollable: find.byType(Scrollable).first);
      expect(find.text('Mes devis'), findsOneWidget);
      await tester.tap(find.text('Mes devis'));
      await tester.pump(const Duration(milliseconds: 100));
      await tester.pump(const Duration(milliseconds: 800));

      expect(find.text('Mes devis'), findsWidgets);
      expect(find.byType(MyQuotesPage), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 5));
    });

    testWidgets('sans compte : la tuile « Mes devis » est masquée', (tester) async {
      await openHome(tester, loggedIn: false);

      expect(find.text('Mes devis'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 5));
    });
  });

  group('Barre de navigation Android', () {
    testWidgets('assistance : le bas de la page défile au-dessus de la barre de navigation', (tester) async {
      _useScreen(tester, const Size(360, 640));
      const navBar = 48.0;
      tester.view.padding = const FakeViewPadding(bottom: navBar);
      tester.view.viewPadding = const FakeViewPadding(bottom: navBar);
      addTearDown(tester.view.resetPadding);
      addTearDown(tester.view.resetViewPadding);
      SharedPreferences.setMockInitialValues({'lock_choice_done': true});
      final store = await LocalStore.open();
      final repos = MockRepositories.create();
      final session = SessionController(store: store, repos: repos)..updateUser(MockData.demoUser);
      await tester.pumpWidget(AppScope(repos: repos, session: session, store: store, child: _app(1, const SupportPage())));
      await tester.pump(const Duration(milliseconds: 800));

      await tester.drag(find.byType(Scrollable).first, const Offset(0, -5000), warnIfMissed: false);
      await tester.pump(const Duration(milliseconds: 300));

      // Sans le correctif, la dernière rubrique s'arrêtait sous la barre (bas de l'écran = 640).
      final lastBottom = tester.getRect(find.byType(ExpansionTile).last).bottom;
      expect(lastBottom, lessThanOrEqualTo(640 - navBar + 0.6));
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 5));
    });
  });
}
