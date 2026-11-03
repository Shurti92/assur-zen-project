import 'package:flutter/widgets.dart';

import '../core/storage/local_store.dart';
import '../data/repositories/repositories.dart';
import 'session_controller.dart';

/// Injection simple des services, sans paquet tiers.
///
/// Utilisation : `AppScope.of(context).repos.contracts.getContracts()`.
class AppScope extends InheritedWidget {
  const AppScope({
    super.key,
    required this.repos,
    required this.session,
    required this.store,
    required super.child,
  });

  final Repositories repos;
  final SessionController session;
  final LocalStore store;

  static AppScope of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<AppScope>();
    assert(scope != null, 'AppScope introuvable dans l’arbre de widgets.');
    return scope!;
  }

  /// Accès sans abonnement (dans un `onPressed`, un `initState`…).
  static AppScope read(BuildContext context) {
    final scope = context.getInheritedWidgetOfExactType<AppScope>();
    assert(scope != null, 'AppScope introuvable dans l’arbre de widgets.');
    return scope!;
  }

  @override
  bool updateShouldNotify(AppScope oldWidget) =>
      repos != oldWidget.repos || session != oldWidget.session || store != oldWidget.store;
}

extension AppScopeX on BuildContext {
  Repositories get repos => AppScope.read(this).repos;
  SessionController get session => AppScope.read(this).session;
  LocalStore get store => AppScope.read(this).store;
}
