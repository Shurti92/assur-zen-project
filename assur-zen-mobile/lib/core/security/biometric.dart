import 'package:flutter/services.dart';
import 'package:local_auth/local_auth.dart';

/// Empreinte digitale / reconnaissance faciale (décision 4.4).
///
/// La biométrie déverrouille l’accès sur cet appareil ; elle ne remplace pas
/// l’authentification serveur. Elle n’est proposée qu’après une première
/// connexion par code PIN.
abstract final class Biometric {
  static final LocalAuthentication _auth = LocalAuthentication();

  static Future<bool> isAvailable() async {
    try {
      final supported = await _auth.isDeviceSupported();
      final enrolled = await _auth.canCheckBiometrics;
      return supported && enrolled;
    } on PlatformException {
      return false;
    }
  }

  static Future<bool> authenticate(String reason) async {
    try {
      return await _auth.authenticate(
        localizedReason: reason,
        options: const AuthenticationOptions(biometricOnly: true, stickyAuth: true),
      );
    } on PlatformException {
      return false;
    }
  }
}
