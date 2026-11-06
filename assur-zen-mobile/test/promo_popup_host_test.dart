import 'dart:async';
import 'dart:convert';

import 'package:assurzen_mobile/app/app_scope.dart';
import 'package:assurzen_mobile/app/routes.dart';
import 'package:assurzen_mobile/app/session_controller.dart';
import 'package:assurzen_mobile/core/storage/local_store.dart';
import 'package:assurzen_mobile/data/mock/mock_repositories.dart';
import 'package:assurzen_mobile/data/models/promo_popup.dart';
import 'package:assurzen_mobile/data/repositories/repositories.dart';
import 'package:assurzen_mobile/features/promo/promo_popup.dart';
import 'package:flutter/foundation.dart' show SynchronousFuture;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:video_player/video_player.dart';

import 'helpers/fonts.dart';

/// Image de 1 pixel : le pop-up s'affiche sans réseau.
final _png = base64Decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==');

class _FakePromos implements PromotionRepository {
  _FakePromos(this.list, {this.fail = false});

  List<PromoPopup> list;
  final bool fail;
  int fetches = 0;
  final events = <(String, String)>[];

  @override
  Future<List<PromoPopup>> getPopups({String placement = 'HOME'}) async {
    fetches++;
    if (fail) return const [];
    return list;
  }

  @override
  Future<void> sendPopupEvent(String id, String type) async => events.add((id, type));

  List<String> get types => [for (final e in events) e.$2];
}

PromoPopup _popup({
  String id = 'POP-1',
  PromoFrequency frequency = PromoFrequency.daily,
  String version = 'v1',
  int delay = 0,
  String? label,
  String? link,
  String? product,
}) =>
    PromoPopup(
      id: id,
      mediaType: PromoMediaType.image,
      mediaUrl: 'https://x.ci/$id.png',
      frequency: frequency,
      version: version,
      displayAfterSeconds: delay,
      linkLabel: label,
      linkUrl: link,
      productId: product,
    );

class _Env {
  _Env(this.store, this.promos);

  final LocalStore store;
  final _FakePromos promos;
}

void main() {
  setUpAll(loadAppFonts);
  setUp(() {
    PromoPopups.resetForTest();
    PromoPopups.debugImageFactory = (_) => MemoryImage(_png);
  });

  final today = DateTime(2026, 11, 6, 10);

  /// Laisse l'horloge réelle décoder l'image, puis redessine.
  Future<void> settle(WidgetTester tester) async {
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 80)));
    await tester.pump(const Duration(milliseconds: 400));
  }

  /// Ouvre un « accueil » qui demande le pop-up, comme le fait l'écran d'accueil au lancement.
  Future<_Env> launch(
    WidgetTester tester,
    _FakePromos promos, {
    Map<String, Object> prefs = const {},
    DateTime? now,
    bool open = true,
  }) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(360, 740);
    addTearDown(tester.view.reset);
    SharedPreferences.setMockInitialValues(prefs);
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
      engagement: base.engagement,
      promotions: promos,
    );
    await tester.pumpWidget(AppScope(
      repos: repos,
      session: SessionController(store: store, repos: repos),
      store: store,
      child: MaterialApp(
        onGenerateRoute: (s) => MaterialPageRoute<void>(
          settings: s,
          builder: (_) => s.name == '/'
              ? Builder(builder: (context) {
                  if (open) WidgetsBinding.instance.addPostFrameCallback((_) => PromoPopups.maybeShow(context, now: now ?? today));
                  return const Scaffold(body: Text('accueil'));
                })
              : Scaffold(body: Text('page ${s.name}')),
        ),
      ),
    ));
    await settle(tester);
    return _Env(store, promos);
  }

  /// Relance l'application : nouvelle vérification, mais les préférences gardées.
  Future<void> relaunch(WidgetTester tester, _Env env, {DateTime? now}) async {
    await tester.pumpWidget(const SizedBox.shrink());
    PromoPopups.resetForTest();
    PromoPopups.debugImageFactory = (_) => MemoryImage(_png);
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
      engagement: base.engagement,
      promotions: env.promos,
    );
    await tester.pumpWidget(AppScope(
      repos: repos,
      session: SessionController(store: env.store, repos: repos),
      store: env.store,
      child: MaterialApp(
        home: Builder(builder: (context) {
          WidgetsBinding.instance.addPostFrameCallback((_) => PromoPopups.maybeShow(context, now: now ?? today));
          return const Scaffold(body: Text('accueil'));
        }),
      ),
    ));
    await settle(tester);
  }

  group('Affichage et mesure', () {
    testWidgets('campagne active : affichée, vue notée en mémoire et envoyée au serveur', (tester) async {
      final env = await launch(tester, _FakePromos([_popup()]));

      expect(find.byType(PromoPopupView), findsOneWidget);
      await settle(tester);
      expect(env.promos.types, ['VIEW']);
      expect(env.store.readPopupsSeen(), {'POP-1': 'v1|2026-11-06'});
    });

    testWidgets('aucune campagne : rien, et aucune mesure', (tester) async {
      final env = await launch(tester, _FakePromos([]));

      expect(find.byType(PromoPopupView), findsNothing);
      expect(env.promos.events, isEmpty);
    });

    testWidgets('liste en échec côté dépôt (vide) : l\'accueil n\'est pas gêné', (tester) async {
      final env = await launch(tester, _FakePromos([_popup()], fail: true));

      expect(find.byType(PromoPopupView), findsNothing);
      expect(find.text('accueil'), findsOneWidget);
      expect(env.promos.events, isEmpty);
    });

    testWidgets('deux campagnes : la première de la liste (priorité) est montrée', (tester) async {
      await launch(tester, _FakePromos([_popup(id: 'POP-7'), _popup(id: 'POP-3')]));

      final view = tester.widget<PromoPopupView>(find.byType(PromoPopupView));
      expect(view.popup.id, 'POP-7');
    });

    testWidgets('la première est déjà vue : la suivante est proposée', (tester) async {
      await launch(
        tester,
        _FakePromos([_popup(id: 'POP-7', frequency: PromoFrequency.once), _popup(id: 'POP-3')]),
        prefs: {'promo_popups_seen': jsonEncode({'POP-7': 'v1|2026-11-01'})},
      );

      expect(tester.widget<PromoPopupView>(find.byType(PromoPopupView)).popup.id, 'POP-3');
    });

    testWidgets('une seule vérification par lancement', (tester) async {
      final env = await launch(tester, _FakePromos([_popup()]));
      await tester.tap(find.byTooltip('Fermer'));
      await tester.pump(const Duration(milliseconds: 400));
      final ctx = tester.element(find.text('accueil'));
      await PromoPopups.maybeShow(ctx, now: today);
      await tester.pump(const Duration(milliseconds: 400));

      expect(env.promos.fetches, 1);
      expect(find.byType(PromoPopupView), findsNothing);
    });
  });

  group('Délai et écran courant', () {
    testWidgets('délai du serveur : rien avant, affiché après', (tester) async {
      final env = await launch(tester, _FakePromos([_popup(delay: 3)]));

      expect(find.byType(PromoPopupView), findsNothing);
      await tester.pump(const Duration(seconds: 2));
      expect(find.byType(PromoPopupView), findsNothing);
      await tester.pump(const Duration(seconds: 2));
      expect(find.byType(PromoPopupView), findsOneWidget);
      expect(env.promos.types, ['VIEW']);
    });

    testWidgets('pendant le délai, le client est parti sur un autre écran : rien n\'est montré ni compté', (tester) async {
      final env = await launch(tester, _FakePromos([_popup(delay: 3)]));
      final ctx = tester.element(find.text('accueil'));
      Navigator.of(ctx).pushNamed('/devis');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pump(const Duration(seconds: 4));

      expect(find.byType(PromoPopupView), findsNothing);
      expect(env.promos.events, isEmpty);
      expect(env.store.readPopupsSeen(), isEmpty, reason: 'une campagne non vue n\'est pas notée comme vue');
    });
  });

  group('Fermeture : toujours possible', () {
    testWidgets('croix : fermé, mesure CLOSE, pas de CLICK', (tester) async {
      final env = await launch(tester, _FakePromos([_popup(label: 'En savoir plus', link: 'https://assurzen.ci')]));

      await tester.tap(find.byTooltip('Fermer'));
      await tester.pump(const Duration(milliseconds: 400));

      expect(find.byType(PromoPopupView), findsNothing);
      expect(env.promos.types, ['VIEW', 'CLOSE']);
    });

    testWidgets('toucher à côté du pop-up : fermé, mesure CLOSE', (tester) async {
      final env = await launch(tester, _FakePromos([_popup()]));

      await tester.tapAt(const Offset(3, 3));
      await tester.pump(const Duration(milliseconds: 400));

      expect(find.byType(PromoPopupView), findsNothing);
      expect(env.promos.types, ['VIEW', 'CLOSE']);
    });

    testWidgets('retour Android : fermé, mesure CLOSE', (tester) async {
      final env = await launch(tester, _FakePromos([_popup()]));

      await tester.binding.handlePopRoute();
      await tester.pump(const Duration(milliseconds: 400));

      expect(find.byType(PromoPopupView), findsNothing);
      expect(env.promos.types, ['VIEW', 'CLOSE']);
    });

    testWidgets('la croix est atteignable sur un très petit écran (320 x 480)', (tester) async {
      await launch(tester, _FakePromos([_popup(label: 'En savoir plus', link: 'https://assurzen.ci')]));
      tester.view.physicalSize = const Size(320, 480);
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(tester.getRect(find.byTooltip('Fermer')).bottom, lessThanOrEqualTo(480));
      expect(tester.getRect(find.byTooltip('Fermer')).top, greaterThanOrEqualTo(0));
    });
  });

  group('Bouton de la campagne', () {
    testWidgets('lien https : mesure CLICK (l\'ouverture du navigateur est hors test)', (tester) async {
      final env = await launch(tester, _FakePromos([_popup(label: 'En savoir plus', link: 'https://assurzen.ci')]));

      await tester.tap(find.text('En savoir plus'));
      await tester.pump(const Duration(milliseconds: 400));

      expect(find.byType(PromoPopupView), findsNothing);
      expect(env.promos.types, ['VIEW', 'CLICK']);
    });

    testWidgets('produit ciblé : la fiche du produit s\'ouvre', (tester) async {
      final env = await launch(tester, _FakePromos([_popup(label: 'Voir le voyage', product: 'voyage')]));

      await tester.tap(find.text('Voir le voyage'));
      await tester.pumpAndSettle();

      expect(find.text('page ${AppRoutes.product}'), findsOneWidget);
      expect(env.promos.types, ['VIEW', 'CLICK']);
    });

    testWidgets('sans lien ni produit : aucun bouton', (tester) async {
      await launch(tester, _FakePromos([_popup()]));

      expect(find.byType(ElevatedButton), findsNothing);
      expect(find.text('En savoir plus'), findsNothing);
    });
  });

  group('Fréquence', () {
    testWidgets('une seule fois : plus jamais au lancement suivant, même plusieurs jours après', (tester) async {
      final env = await launch(tester, _FakePromos([_popup(frequency: PromoFrequency.once)]));
      expect(find.byType(PromoPopupView), findsOneWidget);

      await relaunch(tester, env);
      expect(find.byType(PromoPopupView), findsNothing);
      await relaunch(tester, env, now: DateTime(2026, 12, 25));
      expect(find.byType(PromoPopupView), findsNothing);
    });

    testWidgets('une fois par jour : non le même jour, oui le lendemain', (tester) async {
      final env = await launch(tester, _FakePromos([_popup(frequency: PromoFrequency.daily)]));
      expect(find.byType(PromoPopupView), findsOneWidget);

      await relaunch(tester, env);
      expect(find.byType(PromoPopupView), findsNothing, reason: 'même jour');
      await relaunch(tester, env, now: DateTime(2026, 11, 7, 8));
      expect(find.byType(PromoPopupView), findsOneWidget, reason: 'lendemain');
    });

    testWidgets('à chaque ouverture : revient à chaque lancement', (tester) async {
      final env = await launch(tester, _FakePromos([_popup(frequency: PromoFrequency.everyOpen)]));
      expect(find.byType(PromoPopupView), findsOneWidget);

      await relaunch(tester, env);
      expect(find.byType(PromoPopupView), findsOneWidget);
      await relaunch(tester, env);
      expect(find.byType(PromoPopupView), findsOneWidget);
    });

    testWidgets('campagne modifiée au back-office (version changée) : montrée à nouveau', (tester) async {
      final env = await launch(tester, _FakePromos([_popup(frequency: PromoFrequency.once, version: 'v1')]));
      await relaunch(tester, env);
      expect(find.byType(PromoPopupView), findsNothing);

      env.promos.list = [_popup(frequency: PromoFrequency.once, version: 'v2')];
      await relaunch(tester, env);
      expect(find.byType(PromoPopupView), findsOneWidget);
    });

    testWidgets('fermer ne change pas la règle : « une fois par jour » fermé reste fermé aujourd\'hui', (tester) async {
      final env = await launch(tester, _FakePromos([_popup(frequency: PromoFrequency.daily)]));
      await tester.tap(find.byTooltip('Fermer'));
      await tester.pump(const Duration(milliseconds: 400));

      await relaunch(tester, env);
      expect(find.byType(PromoPopupView), findsNothing);
    });
  });

  group('Média illisible', () {
    testWidgets('image impossible à charger : fermeture silencieuse, non comptée comme vue', (tester) async {
      PromoPopups.debugImageFactory = (_) => _FailImage();
      final env = await launch(tester, _FakePromos([_popup(frequency: PromoFrequency.once)]));
      await settle(tester);
      await settle(tester);
      await tester.pump(const Duration(seconds: 1));
      for (var i = 0; i < 5; i++) {
        await tester.pump(const Duration(milliseconds: 500));
      }

      expect(find.byType(PromoPopupView), findsNothing);
      expect(env.promos.events, isEmpty);
      expect(env.store.readPopupsSeen(), isEmpty);
    });
  });

  group('Vidéo', () {
    PromoPopup video() => const PromoPopup(
          id: 'POP-9',
          mediaType: PromoMediaType.video,
          mediaUrl: 'https://x.ci/v.mp4',
          frequency: PromoFrequency.daily,
          linkLabel: 'Découvrir',
          linkUrl: 'https://assurzen.ci',
        );

    testWidgets('vidéo illisible : le pop-up se ferme sans bruit, rien n\'est compté', (tester) async {
      var ready = 0;
      bool? result = true;
      await tester.pumpWidget(MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () async {
                result = await showGeneralDialog<bool>(
                  context: context,
                  barrierDismissible: true,
                  barrierLabel: 'Fermer',
                  pageBuilder: (_, __, ___) => PromoPopupView(
                    popup: video(),
                    onReady: () => ready++,
                    videoFactory: (uri) => _BrokenVideo(uri),
                  ),
                );
              },
              child: const Text('ouvrir'),
            ),
          ),
        ),
      ));
      await tester.tap(find.text('ouvrir'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
      await tester.pump(const Duration(milliseconds: 600));

      for (var i = 0; i < 4; i++) {
        await tester.pump(const Duration(milliseconds: 600));
      }
      expect(find.byType(PromoPopupView), findsNothing);
      expect(ready, 0);
      expect(result, isFalse);
    });

    testWidgets('pendant le chargement de la vidéo : la croix est déjà là', (tester) async {
      await tester.pumpWidget(MaterialApp(home: Scaffold(body: PromoPopupView(popup: video(), videoFactory: (uri) => _SlowVideo(uri)))));
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.byTooltip('Fermer'), findsOneWidget);
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
    });
  });

  group('Mémoire locale', () {
    test('données illisibles : traitées comme vides', () async {
      SharedPreferences.setMockInitialValues({'promo_popups_seen': 'pas du json'});
      final store = await LocalStore.open();
      expect(store.readPopupsSeen(), isEmpty);
    });

    test('au plus 30 campagnes gardées, les plus anciennes sont oubliées', () async {
      SharedPreferences.setMockInitialValues({});
      final store = await LocalStore.open();
      for (var i = 0; i < 35; i++) {
        await store.markPopupSeen('POP-$i', 'v1', '2026-11-06');
      }
      final seen = store.readPopupsSeen();
      expect(seen.length, 30);
      expect(seen.containsKey('POP-0'), isFalse);
      expect(seen.containsKey('POP-34'), isTrue);
    });
  });
}

/// Vidéo qui ne peut pas être lue.
class _BrokenVideo extends VideoPlayerController {
  // ignore: use_super_parameters
  _BrokenVideo(Uri uri) : super.networkUrl(uri);

  @override
  Future<void> initialize() async => throw Exception('illisible');
}

/// Vidéo qui ne termine jamais son chargement.
class _SlowVideo extends VideoPlayerController {
  // ignore: use_super_parameters
  _SlowVideo(Uri uri) : super.networkUrl(uri);

  @override
  Future<void> initialize() => Completer<void>().future;
}

/// Image qui échoue au chargement (réseau coupé, fichier introuvable).
class _FailImage extends ImageProvider<_FailImage> {
  @override
  Future<_FailImage> obtainKey(ImageConfiguration configuration) => SynchronousFuture<_FailImage>(this);

  @override
  ImageStreamCompleter loadImage(_FailImage key, ImageDecoderCallback decode) =>
      OneFrameImageStreamCompleter(Future<ImageInfo>.error(Exception('image introuvable')));
}
