import 'package:flutter/foundation.dart';

import '../core/security/pattern_lock.dart';
import '../core/storage/local_store.dart';
import '../data/models/models.dart';
import '../data/repositories/repositories.dart';

/// État de session partagé par toute l’application.
///
/// - [user] non nul : client connecté ;
/// - [isGuest] : visite sans compte (catalogue et devis, décision 4.6) ;
/// - [knownPhone] : appareil déjà utilisé → connexion par empreinte possible.
class SessionController extends ChangeNotifier {
  SessionController({required this.store, required this.repos});

  final LocalStore store;
  final Repositories repos;

  /// Accroches branchées au démarrage (notifications push) : après une connexion,
  /// et juste avant la déconnexion. Sans effet si non renseignées.
  Future<void> Function()? onSignedIn;
  Future<void> Function()? onBeforeSignOut;

  AppUser? _user;
  bool _isGuest = false;
  bool _locked = false;
  int _unreadCount = 0;

  AppUser? get user => _user;
  bool get isLoggedIn => _user != null;
  bool get isGuest => _isGuest;
  bool get isLocked => _locked;
  int get unreadCount => _unreadCount;
  String? get knownPhone => store.knownPhone;
  bool get biometricEnabled => store.biometricEnabled;
  bool get patternEnabled => store.patternEnabled;
  bool get lockChoiceDone => store.lockChoiceDone;

  Future<String?> token() => store.readToken();

  Future<String?> refreshToken() => store.readRefreshToken();

  Future<void> signIn(Session session) async {
    _user = session.user;
    _isGuest = false;
    _locked = false;
    await store.writeTokens(session.token, session.refreshToken);
    await store.setKnownPhone(session.user.phone);
    notifyListeners();
    await refreshUnread();
    try {
      await onSignedIn?.call();
    } catch (_) {
      // Le push n'est pas critique : une erreur ne bloque jamais la connexion.
    }
  }

  void continueAsGuest() {
    _isGuest = true;
    notifyListeners();
  }

  Future<void> refreshUnread() async {
    if (!isLoggedIn) return;
    try {
      _unreadCount = await repos.notifications.getUnreadCount();
      notifyListeners();
    } catch (_) {
      // Le badge n’est pas critique : on ignore l’erreur.
    }
  }

  void setUnread(int count) {
    _unreadCount = count;
    notifyListeners();
  }

  /// Décision 4.5 : verrouillage après 5 minutes d’inactivité.
  void lock() {
    if (!isLoggedIn) return;
    _locked = true;
    notifyListeners();
  }

  void unlock() {
    _locked = false;
    notifyListeners();
  }

  Future<void> setLockChoiceDone(bool done) async {
    await store.setLockChoiceDone(done);
  }

  Future<void> setBiometric(bool enabled) async {
    await store.setBiometricEnabled(enabled);
    notifyListeners();
  }

  /// Active (ou remplace) le schéma de déverrouillage local.
  Future<void> setPattern(List<int> dots) async {
    await PatternLock.save(dots);
    await store.setPatternEnabled(true);
    notifyListeners();
  }

  Future<void> clearPattern() async {
    await PatternLock.clear();
    await store.setPatternEnabled(false);
    notifyListeners();
  }

  void updateUser(AppUser user) {
    _user = user;
    notifyListeners();
  }

  Future<void> signOut({bool forgetDevice = false}) async {
    try {
      await onBeforeSignOut?.call(); // retrait de l'appareil push, session encore valide
    } catch (_) {}
    try {
      await repos.auth.logout();
    } catch (_) {
      // On se déconnecte localement même si l’appel échoue.
    }
    _user = null;
    _isGuest = false;
    _locked = false;
    _unreadCount = 0;
    await store.clearAll();
    // Le schéma appartient au compte : il ne survit pas à une déconnexion.
    await clearPattern();
    // Un autre client pourra choisir son propre mode de déverrouillage.
    await store.setLockChoiceDone(false);
    if (forgetDevice) {
      await store.setKnownPhone(null);
      await store.setBiometricEnabled(false);
    }
    notifyListeners();
  }
}
