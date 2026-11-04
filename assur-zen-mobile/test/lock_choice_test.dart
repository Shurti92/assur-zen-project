import 'package:assurzen_mobile/app/app_scope.dart';
import 'package:assurzen_mobile/app/session_controller.dart';
import 'package:assurzen_mobile/core/security/biometric.dart';
import 'package:assurzen_mobile/core/storage/local_store.dart';
import 'package:assurzen_mobile/core/widgets/az_pattern_pad.dart';
import 'package:assurzen_mobile/data/mock/mock_data.dart';
import 'package:assurzen_mobile/data/mock/mock_repositories.dart';
import 'package:assurzen_mobile/features/shell/main_shell.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'helpers/fonts.dart';

const _title = 'Comment protéger votre appli ?';

Future<(SessionController, LocalStore)> _setup({bool loggedIn = true, Map<String, Object> prefs = const {}}) async {
  SharedPreferences.setMockInitialValues(prefs);
  FlutterSecureStorage.setMockInitialValues({});
  final store = await LocalStore.open();
  final repos = MockRepositories.create();
  final session = SessionController(store: store, repos: repos);
  if (loggedIn) session.updateUser(MockData.demoUser);
  return (session, store);
}

Future<void> _pumpShell(WidgetTester tester, SessionController session, LocalStore store) async {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = const Size(411, 891);
  addTearDown(tester.view.reset);
  await tester.pumpWidget(AppScope(
    repos: MockRepositories.create(),
    session: session,
    store: store,
    child: const MaterialApp(home: MainShell(initialTab: ShellTab.home)),
  ));
  await tester.pump(const Duration(milliseconds: 800));
  // La fenêtre s'ouvre après la vérification de l'empreinte : on laisse son animation se terminer.
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 600));
}

/// Laisse se terminer l'animation d'une feuille (ouverture ou fermeture) et les attentes qui suivent.
Future<void> _settle(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 600));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 100));
}

Future<void> _finish(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pump(const Duration(seconds: 5)); // latences simulées du mode démonstration
}

/// Trace un schéma en L (haut gauche → haut droit → bas droit) sur la grille ouverte.
Future<void> _drawPattern(WidgetTester tester) async {
  final canvas = find.descendant(of: find.byType(AzPatternPad), matching: find.byType(CustomPaint));
  final origin = tester.getTopLeft(canvas.first);
  Offset dot(int i) => origin + Offset(90.0 * (i % 3) + 45, 90.0 * (i ~/ 3) + 45);
  final g = await tester.startGesture(dot(0));
  for (final i in [1, 2, 5, 8]) {
    await g.moveTo(dot(i));
  }
  await g.up();
  await tester.pump();
}

void main() {
  setUpAll(loadAppFonts);

  // Par défaut : pas d'empreinte sur l'appareil. Chaque test peut en simuler une.
  setUp(() {
    Biometric.debugAvailability = () async => false;
    Biometric.debugAuthenticate = (_) async => false;
  });
  tearDown(() {
    Biometric.debugAvailability = null;
    Biometric.debugAuthenticate = null;
  });

  testWidgets('un client connecté sans choix voit la fenêtre, avec le PIN toujours disponible', (tester) async {
    final (session, store) = await _setup();
    await _pumpShell(tester, session, store);

    expect(find.text(_title), findsOneWidget);
    expect(find.text('Code PIN'), findsOneWidget);
    expect(find.text('Toujours disponible'), findsOneWidget);
    expect(find.text('Schéma'), findsOneWidget);
    await _finish(tester);
  });

  testWidgets('« Continuer » sans rien cocher : PIN seul, choix mémorisé, jamais redemandé', (tester) async {
    final (session, store) = await _setup();
    await _pumpShell(tester, session, store);

    await tester.tap(find.text('Continuer'));
    await _settle(tester);

    expect(session.lockChoiceDone, isTrue);
    expect(session.patternEnabled, isFalse);
    expect(session.biometricEnabled, isFalse);
    expect(find.text(_title), findsNothing);

    // Nouvelle ouverture de l'accueil : la fenêtre ne revient pas.
    await tester.pumpWidget(const SizedBox.shrink());
    await _pumpShell(tester, session, store);
    expect(find.text(_title), findsNothing);
    await _finish(tester);
  });

  testWidgets('choisir « Schéma » : le client dessine puis confirme son schéma, qui est enregistré', (tester) async {
    final (session, store) = await _setup();
    await _pumpShell(tester, session, store);

    await tester.tap(find.text('Schéma'));
    await tester.pump();
    await tester.tap(find.text('Continuer'));
    await _settle(tester);

    expect(find.text('Schéma de déverrouillage'), findsOneWidget);
    await _drawPattern(tester);
    expect(find.text('Dessinez-le une seconde fois pour confirmer.'), findsOneWidget);
    await _drawPattern(tester);
    await _settle(tester);

    expect(session.patternEnabled, isTrue);
    expect(session.lockChoiceDone, isTrue);
    await _finish(tester);
  });

  testWidgets('fermer la fenêtre sans valider : PIN seul, et on ne redemande pas', (tester) async {
    final (session, store) = await _setup();
    await _pumpShell(tester, session, store);

    await tester.tapAt(const Offset(200, 60)); // hors de la feuille
    await _settle(tester);

    expect(find.text(_title), findsNothing);
    expect(session.lockChoiceDone, isTrue);
    expect(session.patternEnabled, isFalse);
    await _finish(tester);
  });

  testWidgets('sans capteur d’empreinte, l’option n’est pas proposée', (tester) async {
    final (session, store) = await _setup();
    await _pumpShell(tester, session, store);

    expect(find.text('Empreinte digitale'), findsNothing);
    await _finish(tester);
  });

  testWidgets('choisir « Empreinte digitale » : le système la confirme, puis elle est activée', (tester) async {
    Biometric.debugAvailability = () async => true;
    String? asked;
    Biometric.debugAuthenticate = (reason) async {
      asked = reason;
      return true;
    };
    final (session, store) = await _setup();
    await _pumpShell(tester, session, store);

    await tester.tap(find.text('Empreinte digitale'));
    await tester.pump();
    await tester.tap(find.text('Continuer'));
    await _settle(tester);

    expect(asked, isNotNull);
    expect(session.biometricEnabled, isTrue);
    expect(session.lockChoiceDone, isTrue);
    await _finish(tester);
  });

  testWidgets('empreinte refusée par le système : elle n’est pas activée et le client est prévenu', (tester) async {
    Biometric.debugAvailability = () async => true;
    Biometric.debugAuthenticate = (_) async => false;
    final (session, store) = await _setup();
    await _pumpShell(tester, session, store);

    await tester.tap(find.text('Empreinte digitale'));
    await tester.pump();
    await tester.tap(find.text('Continuer'));
    await _settle(tester);

    expect(session.biometricEnabled, isFalse);
    expect(find.textContaining('Empreinte non activée'), findsOneWidget);
    await _finish(tester);
  });

  testWidgets('un client qui avait déjà l’empreinte la voit cochée, et peut la retirer', (tester) async {
    Biometric.debugAvailability = () async => true;
    final (session, store) = await _setup(prefs: {'biometric_enabled': true});
    await _pumpShell(tester, session, store);

    await tester.tap(find.text('Empreinte digitale')); // la décoche
    await tester.pump();
    await tester.tap(find.text('Continuer'));
    await _settle(tester);

    expect(session.biometricEnabled, isFalse);
    await _finish(tester);
  });

  testWidgets('un visiteur sans compte ne voit pas la fenêtre', (tester) async {
    final (session, store) = await _setup(loggedIn: false);
    await _pumpShell(tester, session, store);

    expect(find.text(_title), findsNothing);
    expect(session.lockChoiceDone, isFalse);
    await _finish(tester);
  });

  testWidgets('une connexion faite depuis le catalogue déclenche la fenêtre, sans recréer l\'accueil', (tester) async {
    final (session, store) = await _setup(loggedIn: false);
    await _pumpShell(tester, session, store);
    expect(find.text(_title), findsNothing);

    session.updateUser(MockData.demoUser); // le client se connecte
    await tester.pump(const Duration(milliseconds: 800));
    await tester.pump();
    await _settle(tester);

    expect(find.text(_title), findsOneWidget);
    await _finish(tester);
  });

  testWidgets('une déconnexion rouvre le choix pour le client suivant', (tester) async {
    final (session, store) = await _setup(prefs: {'lock_choice_done': true});
    expect(session.lockChoiceDone, isTrue);

    final done = session.signOut();
    await tester.pump(const Duration(seconds: 5)); // latence simulée du mode démonstration
    await done;

    expect(session.lockChoiceDone, isFalse);
  });
}
