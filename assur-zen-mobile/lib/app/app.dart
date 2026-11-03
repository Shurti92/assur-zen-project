import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import '../core/storage/local_store.dart';
import '../core/theme/az_theme.dart';
import '../core/widgets/az_layout.dart';
import '../data/repositories/repositories.dart';
import 'app_scope.dart';
import 'navigation.dart';
import 'env.dart';
import 'routes.dart';
import 'session_controller.dart';

/// Racine de l’application.
class AssurZenApp extends StatefulWidget {
  const AssurZenApp({super.key, required this.store, required this.repos, required this.session});

  final LocalStore store;
  final Repositories repos;
  final SessionController session;

  @override
  State<AssurZenApp> createState() => _AssurZenAppState();
}

class _AssurZenAppState extends State<AssurZenApp> with WidgetsBindingObserver {
  DateTime? _pausedAt;
  bool _lockShown = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.session.addListener(_onSession);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.session.removeListener(_onSession);
    super.dispose();
  }

  /// Décision 4.5 : PIN redemandé après 5 minutes passées hors de l’application.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused || state == AppLifecycleState.hidden) {
      _pausedAt ??= DateTime.now();
    } else if (state == AppLifecycleState.resumed) {
      final paused = _pausedAt;
      _pausedAt = null;
      if (paused != null && DateTime.now().difference(paused) > Env.inactivityLock) {
        widget.session.lock();
      }
    }
  }

  void _onSession() {
    if (widget.session.isLocked && !_lockShown) {
      _lockShown = true;
      appNavigatorKey.currentState?.pushNamed(AppRoutes.lock).whenComplete(() => _lockShown = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: AzTheme.lightOverlay,
      child: AppScope(
        repos: widget.repos,
        session: widget.session,
        store: widget.store,
        child: MaterialApp(
          title: 'AssurZen',
          debugShowCheckedModeBanner: false,
          navigatorKey: appNavigatorKey,
          theme: AzTheme.light(),
          locale: const Locale('fr', 'CI'),
          supportedLocales: const [Locale('fr', 'CI'), Locale('fr')],
          localizationsDelegates: const [
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          initialRoute: AppRoutes.splash,
          onGenerateRoute: AppRoutes.onGenerateRoute,
          builder: (context, child) {
            // Texte agrandi accepté jusqu’à 130 % sans casser les montants.
            final mq = MediaQuery.of(context);
            return MediaQuery(
              data: mq.copyWith(textScaler: mq.textScaler.clamp(minScaleFactor: 0.9, maxScaleFactor: 1.3)),
              child: AzScreenFrame(child: child ?? const SizedBox.shrink()),
            );
          },
        ),
      ),
    );
  }
}
