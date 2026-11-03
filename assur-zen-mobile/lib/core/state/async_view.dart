import 'package:flutter/material.dart';

import '../network/api_exception.dart';
import '../widgets/az_states.dart';

/// Affiche les 4 états d’un écran qui lit des données serveur :
/// chargement (squelette), erreur (avec « Réessayer »), vide, données.
class AsyncView<T> extends StatefulWidget {
  const AsyncView({
    super.key,
    required this.load,
    required this.builder,
    this.isEmpty,
    this.empty,
    this.loading,
  });

  final Future<T> Function() load;
  final Widget Function(BuildContext context, T data, Future<void> Function() reload) builder;
  final bool Function(T data)? isEmpty;
  final Widget? empty;
  final Widget? loading;

  @override
  State<AsyncView<T>> createState() => _AsyncViewState<T>();
}

class _AsyncViewState<T> extends State<AsyncView<T>> {
  late Future<T> _future;

  @override
  void initState() {
    super.initState();
    _future = widget.load();
  }

  Future<void> _reload() async {
    final next = widget.load();
    // Corps en bloc : une flèche renverrait le Future assigné, ce que setState refuse.
    setState(() {
      _future = next;
    });
    try {
      await next;
    } catch (_) {
      // L’erreur est affichée par le FutureBuilder.
    }
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<T>(
      future: _future,
      builder: (context, snap) {
        if (snap.connectionState != ConnectionState.done) {
          return widget.loading ?? const AzSkeletonList();
        }
        if (snap.hasError) {
          final err = snap.error;
          return AzErrorState(
            offline: err is ApiException && err.isNetwork,
            message: err is ApiException ? err.message : 'Une erreur inattendue est survenue.',
            onRetry: _reload,
          );
        }
        final data = snap.data as T;
        if (widget.isEmpty != null && widget.isEmpty!(data) && widget.empty != null) {
          return widget.empty!;
        }
        return widget.builder(context, data, _reload);
      },
    );
  }
}

/// Message d’erreur lisible à partir de n’importe quelle exception.
String errorMessage(Object error) {
  if (error is ApiException) {
    // PIN faux : le nombre de tentatives restantes n'est annoncé que s'il vient du serveur.
    final n = error.remainingAttempts;
    if (n != null && n > 0) {
      return '${error.message}. Il vous reste $n ${n == 1 ? 'tentative' : 'tentatives'}.';
    }
    return error.message;
  }
  // Diagnostic : en développement, l’erreur réelle est écrite dans la console.
  assert(() {
    debugPrint('[AssurZen] Erreur inattendue : $error');
    return true;
  }());
  return 'Une erreur inattendue est survenue. Réessayez.';
}
