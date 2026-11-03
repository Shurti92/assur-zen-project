/// Erreur unifiée renvoyée par la couche réseau (décision 9.3).
///
/// Le backend renvoie idéalement : un code, un message lisible en français et,
/// pour une erreur de saisie, le champ concerné. L’application affiche
/// [message] tel quel, sous le champ [field] s’il est fourni.
class ApiException implements Exception {
  const ApiException({required this.kind, required this.message, this.code, this.field, this.statusCode, this.retryAfterSeconds});

  final ApiErrorKind kind;
  final String message;
  final String? code;
  final String? field;
  final int? statusCode;

  /// Attente avant de pouvoir réessayer (renvoi de code SMS, compte verrouillé…).
  final int? retryAfterSeconds;

  factory ApiException.network() => const ApiException(
        kind: ApiErrorKind.network,
        message: 'Connexion impossible. Vérifiez votre connexion internet ou vos données mobiles, puis réessayez.',
      );

  factory ApiException.timeout() => const ApiException(
        kind: ApiErrorKind.network,
        message: 'Le serveur met trop de temps à répondre. Réessayez dans un instant.',
      );

  factory ApiException.unauthorized() => const ApiException(
        kind: ApiErrorKind.unauthorized,
        statusCode: 401,
        message: 'Votre session a expiré. Saisissez à nouveau votre code PIN.',
      );

  factory ApiException.server([int? status]) => ApiException(
        kind: ApiErrorKind.server,
        statusCode: status,
        message: 'Un problème est survenu de notre côté. Réessayez dans un instant ou contactez le service client.',
      );

  factory ApiException.notImplemented(String what) => ApiException(
        kind: ApiErrorKind.server,
        message: 'Service non branché : $what. À relier à l’API AssurZen.',
      );

  bool get isNetwork => kind == ApiErrorKind.network;

  @override
  String toString() => 'ApiException($kind, $statusCode, $code, $message)';
}

enum ApiErrorKind { network, unauthorized, validation, business, server }
