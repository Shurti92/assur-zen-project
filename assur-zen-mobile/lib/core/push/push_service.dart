import 'dart:async';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';

import '../../app/navigation.dart';
import '../../app/routes.dart';
import '../../app/session_controller.dart';
import '../../data/repositories/repositories.dart';
import '../storage/local_store.dart';
import '../widgets/az_feedback.dart';

/// Notifications push (Firebase Cloud Messaging) ↔ Gateway.
///
/// - à la connexion : permission, jeton FCM, `POST /mobile/devices` ;
/// - au renouvellement du jeton : nouvel enregistrement ;
/// - à la déconnexion : `DELETE /mobile/devices/{id}` ;
/// - à la réception : compteur de non lues mis à jour ; au toucher, lien profond
///   vers le contrat ou le sinistre (`contractId`, `claimId`) et marquage « lu »
///   (`notificationId`).
///
/// Le texte du push est rédigé par le serveur. Aucune erreur Firebase ne doit
/// empêcher l'application de fonctionner : le push est un plus. Le jeton n'est
/// jamais écrit dans les journaux.
class PushService {
  PushService({required this.repos, required this.store, required this.session});

  final Repositories repos;
  final LocalStore store;
  final SessionController session;

  bool _started = false;
  bool _available = false;
  RemoteMessage? _pending; // toucher reçu avant la connexion (démarrage à froid)

  /// Démarre Firebase et les écouteurs. N'échoue jamais.
  Future<void> start() async {
    if (_started) return;
    _started = true;
    try {
      await Firebase.initializeApp();
      final messaging = FirebaseMessaging.instance;
      // `onTokenRefresh` et `getToken` sont signalés obsolètes, mais les remplaçants
      // (`register`, `onRegistered`) fournissent un identifiant d'installation, pas le
      // jeton FCM que la Gateway attend pour l'API HTTP v1 : on garde le jeton classique.
      // ignore: deprecated_member_use
      messaging.onTokenRefresh.listen(_sendToken);
      FirebaseMessaging.onMessage.listen(_onForeground);
      FirebaseMessaging.onMessageOpenedApp.listen(_open);
      _pending = await messaging.getInitialMessage();
      _available = true;
    } catch (e) {
      debugPrint('Push indisponible : ${e.runtimeType}');
    }
  }

  /// À appeler après la connexion.
  Future<void> register() async {
    await start();
    if (!_available) return;
    try {
      final messaging = FirebaseMessaging.instance;
      final settings = await messaging.requestPermission();
      if (settings.authorizationStatus == AuthorizationStatus.denied) return;
      // ignore: deprecated_member_use
      final token = await messaging.getToken();
      if (token != null) await _sendToken(token);
    } catch (e) {
      debugPrint('Enregistrement push impossible : ${e.runtimeType}');
    }
    final pending = _pending;
    _pending = null;
    if (pending != null) _open(pending);
  }

  /// À appeler à la déconnexion, avant de révoquer la session.
  Future<void> unregister() async {
    final id = store.deviceId;
    if (id == null) return;
    try {
      await repos.notifications.unregisterDevice(id);
      await store.writeDeviceId(null);
    } catch (e) {
      debugPrint('Retrait de l’appareil impossible : ${e.runtimeType}');
    }
  }

  Future<void> _sendToken(String token) async {
    if (!session.isLoggedIn) return;
    try {
      final id = await repos.notifications.registerDevice(token: token);
      if (id.isNotEmpty) await store.writeDeviceId(id);
    } catch (e) {
      debugPrint('Appareil non enregistré : ${e.runtimeType}');
    }
  }

  /// Application au premier plan : le système n'affiche pas le message, on le
  /// signale dans l'application et on met le compteur à jour.
  void _onForeground(RemoteMessage message) {
    session.refreshUnread();
    final title = message.notification?.title;
    final context = appNavigatorKey.currentContext;
    if (title != null && title.isNotEmpty && context != null && context.mounted) {
      showAzSnack(context, title);
    }
  }

  /// Toucher sur la notification : lien profond et marquage « lu ».
  void _open(RemoteMessage message) {
    if (!session.isLoggedIn || session.isLocked) {
      _pending = message; // repris après la connexion ou le déverrouillage
      return;
    }
    final nav = appNavigatorKey.currentState;
    if (nav == null) return;
    final data = message.data;
    final notificationId = data['notificationId']?.toString();
    if (notificationId != null && notificationId.isNotEmpty) {
      repos.notifications.markRead(notificationId).then((_) => session.refreshUnread()).catchError((_) {});
    }
    final claimId = _nonEmpty(data['claimId']);
    final contractId = _nonEmpty(data['contractId']);
    if (claimId != null) {
      nav.pushNamed(AppRoutes.claim, arguments: claimId);
    } else if (contractId != null) {
      nav.pushNamed(AppRoutes.contract, arguments: contractId);
    } else {
      nav.pushNamed(AppRoutes.notifications);
    }
  }

  static String? _nonEmpty(Object? v) {
    final s = v?.toString().trim();
    return (s == null || s.isEmpty) ? null : s;
  }
}
