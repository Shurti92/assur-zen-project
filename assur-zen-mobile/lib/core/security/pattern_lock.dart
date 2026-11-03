import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Schéma de déverrouillage local (grille 3×3), en complément du PIN et de
/// l'empreinte. Il ne sert qu'à rouvrir une session déjà ouverte sur cet
/// appareil : le PIN reste la référence (paiement, suppression de compte).
///
/// Le schéma n'est jamais stocké en clair : seul un hachage salé est conservé
/// dans le stockage chiffré.
abstract final class PatternLock {
  static const minDots = 4;
  static const maxAttempts = 5;
  static const _kHash = 'unlock_pattern_hash';
  static const _kSalt = 'unlock_pattern_salt';
  static const FlutterSecureStorage _secure = FlutterSecureStorage();

  static String hash(List<int> dots, String salt) {
    List<int> digest = utf8.encode('$salt:${dots.join('-')}');
    for (var i = 0; i < 20000; i++) {
      digest = sha256.convert([...digest, ...utf8.encode(salt)]).bytes;
    }
    return base64Encode(digest);
  }

  static String _newSalt() {
    final r = Random.secure();
    return base64Encode(List<int>.generate(16, (_) => r.nextInt(256)));
  }

  /// Valide la forme d'un schéma ; renvoie le message d'erreur ou null.
  static String? validate(List<int> dots) =>
      dots.length < minDots ? 'Reliez au moins $minDots points.' : null;

  static Future<bool> exists() async {
    try {
      return (await _secure.read(key: _kHash)) != null;
    } catch (_) {
      return false;
    }
  }

  static Future<void> save(List<int> dots) async {
    final salt = _newSalt();
    await _secure.write(key: _kSalt, value: salt);
    await _secure.write(key: _kHash, value: hash(dots, salt));
  }

  static Future<bool> verify(List<int> dots) async {
    try {
      final salt = await _secure.read(key: _kSalt);
      final stored = await _secure.read(key: _kHash);
      if (salt == null || stored == null) return false;
      return hash(dots, salt) == stored;
    } catch (_) {
      return false;
    }
  }

  static Future<void> clear() async {
    try {
      await _secure.delete(key: _kHash);
      await _secure.delete(key: _kSalt);
    } catch (_) {}
  }
}
