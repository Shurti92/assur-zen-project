import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:local_auth/local_auth.dart';

/// Empreinte digitale / reconnaissance faciale (décision 4.4).
///
/// La biométrie déverrouille l’accès sur cet appareil ; elle ne remplace pas
/// l’authentification serveur. Elle n’est proposée qu’après une première
/// connexion par code PIN.
abstract final class Biometric {
  static final LocalAuthentication _auth = LocalAuthentication();

  /// Réservé aux tests : remplace la vérification et la demande d'empreinte du système, qui ne
  /// répondent jamais hors d'un téléphone.
  @visibleForTesting
  static Future<bool> Function()? debugAvailability;
  @visibleForTesting
  static Future<bool> Function(String reason)? debugAuthenticate;

  static Future<bool> isAvailable() async {
    final override = debugAvailability;
    if (override != null) return override();
    try {
      final supported = await _auth.isDeviceSupported();
      final enrolled = await _auth.canCheckBiometrics;
      return supported && enrolled;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<bool> authenticate(String reason) async {
    final override = debugAuthenticate;
    if (override != null) return override(reason);
    try {
      return await _auth.authenticate(
        localizedReason: reason,
        options: const AuthenticationOptions(biometricOnly: true, stickyAuth: true),
      );
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }
}
