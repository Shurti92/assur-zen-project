/// Configuration d’environnement, passée au lancement avec `--dart-define`.
///
/// Trois environnements, choisis par `APP_ENV` :
///   local   → http://localhost:9191 (gateway de dev, via `adb reverse tcp:9191 tcp:9191`)
///   recette → https://medicis-apps.com:9443/assur-zen-gateway-recette/
///   prod    → https://medicis-apps.com:9443/assur-zen-gateway/
///
/// Exemples :
///   flutter run                                              → données de démonstration
///   flutter run --dart-define=USE_MOCK=false                 → gateway locale
///   flutter run --dart-define=USE_MOCK=false --dart-define=APP_ENV=recette
///   flutter build apk --release --dart-define=USE_MOCK=false --dart-define=APP_ENV=prod
///
/// `API_BASE_URL` reste possible pour forcer une adresse précise (prioritaire).
abstract final class Env {
  /// `true` : l’application tourne sur les dépôts de démonstration (données fictives).
  static const bool useMock = bool.fromEnvironment('USE_MOCK', defaultValue: true);

  /// Environnement visé : `local` (défaut), `recette` ou `prod`.
  static const String appEnv = String.fromEnvironment('APP_ENV', defaultValue: 'local');

  static const String _baseUrlOverride = String.fromEnvironment('API_BASE_URL');

  static const Map<String, String> _baseUrls = {
    'local': 'http://localhost:9191',
    'recette': 'https://medicis-apps.com:9443/assur-zen-gateway-recette/',
    'prod': 'https://medicis-apps.com:9443/assur-zen-gateway/',
  };

  /// URL de base de la gateway pour l’environnement choisi.
  static String get apiBaseUrl => resolveBaseUrl(appEnv, _baseUrlOverride);

  /// Résolution testable : l’adresse forcée l’emporte, sinon celle de l’environnement.
  static String resolveBaseUrl(String env, String override) {
    if (override.isNotEmpty) return override;
    final url = _baseUrls[env];
    if (url == null) throw StateError('APP_ENV inconnu : « $env » (local, recette ou prod).');
    return url;
  }

  /// Vrai hors production : permet d’afficher un repère d’environnement.
  static bool get isProd => appEnv == 'prod' && _baseUrlOverride.isEmpty;

  /// Délai maximal d’un appel réseau.
  static const Duration requestTimeout = Duration(seconds: 20);

  /// Décision 4.5 : PIN redemandé après 5 minutes d’inactivité.
  static const Duration inactivityLock = Duration(minutes: 5);

  /// Décision 3.4 : 3 minutes d’attente à l’écran pour la confirmation du paiement.
  static const Duration paymentWaitMax = Duration(minutes: 5);

  /// Décision 2.5 : brouillons conservés 30 jours.
  static const Duration draftLifetime = Duration(days: 30);
}
