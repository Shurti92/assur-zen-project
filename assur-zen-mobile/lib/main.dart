import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'app/app.dart';
import 'app/env.dart';
import 'app/session_controller.dart';
import 'core/network/api_client.dart';
import 'core/push/push_service.dart';
import 'core/storage/local_store.dart';
import 'core/utils/contact_directory.dart';
import 'core/theme/az_theme.dart';
import 'data/api/api_repositories.dart';
import 'data/mock/mock_repositories.dart';
import 'data/repositories/repositories.dart';

/// AssurZen Mobile — Votre sérénité, notre priorité.
///
/// Par défaut l’application tourne sur des données de démonstration.
/// Pour le backend : `flutter run --dart-define=USE_MOCK=false --dart-define=API_BASE_URL=…`
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setSystemUIOverlayStyle(AzTheme.lightOverlay);
  await SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);

  final store = await LocalStore.open();

  late final SessionController session;
  final Repositories repos = Env.useMock
      ? MockRepositories.create()
      : ApiRepositories.create(
          readRefreshToken: store.readRefreshToken,
          saveTokens: (access, refresh) => store.writeTokens(access, refresh),
          ApiClient(
            baseUrl: Env.apiBaseUrl,
            tokenProvider: store.readToken,
            refreshTokenProvider: store.readRefreshToken,
            onTokensRefreshed: (access, refresh) => store.writeTokens(access, refresh),
            onUnauthorized: () => session.lock(),
          ),
        );
  session = SessionController(store: store, repos: repos);

  // Coordonnées AssurZen : fournies par le serveur (route publique), sans jeton.
  ContactDirectory.load(repos.support);

  // Notifications push : uniquement avec le vrai backend (jamais en démonstration).
  if (!Env.useMock) {
    final push = PushService(repos: repos, store: store, session: session);
    session.onSignedIn = push.register;
    session.onBeforeSignOut = push.unregister;
    push.start(); // sans attendre : n'échoue jamais
  }

  runApp(AssurZenApp(store: store, repos: repos, session: session));
}
