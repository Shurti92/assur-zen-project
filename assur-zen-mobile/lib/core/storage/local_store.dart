import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../app/env.dart';
import '../../data/models/models.dart';

/// Stockage local :
/// - [FlutterSecureStorage] (chiffré) pour le jeton de session ;
/// - [SharedPreferences] pour les réglages, le brouillon de devis et le cache
///   de l’attestation consultable hors connexion (décision 5.4).
///
/// Aucune pièce d’identité ni photo n’est stockée en clair (décision 8.4) :
/// seuls des chemins de fichiers temporaires sont gardés le temps de l’envoi.
class LocalStore {
  LocalStore._(this._prefs);

  static Future<LocalStore> open() async => LocalStore._(await SharedPreferences.getInstance());

  final SharedPreferences _prefs;
  static const FlutterSecureStorage _secure = FlutterSecureStorage();

  static const _kToken = 'session_token';
  static const _kRefresh = 'session_refresh_token';
  static const _kOnboarding = 'onboarding_done';
  static const _kKnownPhone = 'known_phone';
  static const _kBiometric = 'biometric_enabled';
  static const _kPattern = 'pattern_enabled';
  static const _kLockChoice = 'lock_choice_done';
  static const _kDraft = 'quote_draft';
  static const _kAttestation = 'attestation_cache';
  static const _kPrefs = 'notification_prefs';
  static const _kDeviceId = 'push_device_id';

  // Jeton --------------------------------------------------------------------
  Future<String?> readToken() async {
    try {
      return await _secure.read(key: _kToken);
    } catch (_) {
      return null;
    }
  }

  Future<void> writeToken(String? token) async {
    if (token == null) {
      await _secure.delete(key: _kToken);
    } else {
      await _secure.write(key: _kToken, value: token);
    }
  }

  Future<String?> readRefreshToken() async {
    try {
      return await _secure.read(key: _kRefresh);
    } catch (_) {
      return null;
    }
  }

  Future<void> writeRefreshToken(String? token) async {
    if (token == null) {
      await _secure.delete(key: _kRefresh);
    } else {
      await _secure.write(key: _kRefresh, value: token);
    }
  }

  /// Enregistre le couple de jetons (connexion ou renouvellement).
  Future<void> writeTokens(String accessToken, String? refreshToken) async {
    await writeToken(accessToken);
    await writeRefreshToken(refreshToken);
  }

  // Réglages -----------------------------------------------------------------
  bool get onboardingDone => _prefs.getBool(_kOnboarding) ?? false;
  Future<void> setOnboardingDone() => _prefs.setBool(_kOnboarding, true);

  /// Numéro du dernier compte connecté : rend l’appareil « connu » (empreinte).
  String? get knownPhone => _prefs.getString(_kKnownPhone);

  /// Identifiant de l'appareil enregistré pour le push (`POST /mobile/devices`).
  String? get deviceId => _prefs.getString(_kDeviceId);

  Future<void> writeDeviceId(String? id) async {
    if (id == null) {
      await _prefs.remove(_kDeviceId);
    } else {
      await _prefs.setString(_kDeviceId, id);
    }
  }
  Future<void> setKnownPhone(String? phone) =>
      phone == null ? _prefs.remove(_kKnownPhone) : _prefs.setString(_kKnownPhone, phone);

  bool get biometricEnabled => _prefs.getBool(_kBiometric) ?? false;
  Future<void> setBiometricEnabled(bool value) => _prefs.setBool(_kBiometric, value);

  /// Le client a déjà choisi son mode de déverrouillage (empreinte, schéma, PIN) sur cet appareil.
  bool get lockChoiceDone => _prefs.getBool(_kLockChoice) ?? false;
  Future<void> setLockChoiceDone(bool value) => _prefs.setBool(_kLockChoice, value);

  bool get patternEnabled => _prefs.getBool(_kPattern) ?? false;
  Future<void> setPatternEnabled(bool value) => _prefs.setBool(_kPattern, value);

  Map<String, bool> get notificationPrefs {
    final raw = _prefs.getString(_kPrefs);
    final defaults = {'echeances': true, 'sinistres': true, 'offres': false};
    if (raw == null) return defaults;
    final decoded = jsonDecode(raw) as Map<String, dynamic>;
    return {for (final e in defaults.entries) e.key: (decoded[e.key] as bool?) ?? e.value};
  }

  Future<void> setNotificationPrefs(Map<String, bool> value) => _prefs.setString(_kPrefs, jsonEncode(value));

  // Brouillon de devis (décision 2.5) ------------------------------------------
  QuoteDraft? readDraft() {
    final raw = _prefs.getString(_kDraft);
    if (raw == null) return null;
    try {
      final draft = QuoteDraft.fromJson(jsonDecode(raw) as Map<String, dynamic>);
      if (DateTime.now().difference(draft.savedAt) > Env.draftLifetime) {
        _prefs.remove(_kDraft);
        return null;
      }
      return draft;
    } catch (_) {
      _prefs.remove(_kDraft);
      return null;
    }
  }

  Future<void> saveDraft(QuoteDraft draft) {
    draft.savedAt = DateTime.now();
    return _prefs.setString(_kDraft, jsonEncode(draft.toJson()));
  }

  Future<void> clearDraft() => _prefs.remove(_kDraft);

  // Attestation hors connexion (décision 5.4) ----------------------------------
  Map<String, String>? readAttestation(String contractId) {
    final raw = _prefs.getString('$_kAttestation.$contractId');
    if (raw == null) return null;
    return (jsonDecode(raw) as Map<String, dynamic>).map((k, v) => MapEntry(k, v.toString()));
  }

  Future<void> cacheAttestation(Contract c) => _prefs.setString(
        '$_kAttestation.${c.id}',
        jsonEncode({
          'object': c.insuredObject,
          'number': c.number,
          'holder': c.holderName ?? '',
          'end': c.endDate?.toIso8601String() ?? '',
          'code': c.verificationCode ?? '',
        }),
      );

  Future<void> clearAll() async {
    await writeToken(null);
    await writeRefreshToken(null);
    await _prefs.remove(_kDraft);
  }
}
