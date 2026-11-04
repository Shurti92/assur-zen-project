import 'dart:math';

import '../../core/network/api_client.dart';
import '../../core/network/api_exception.dart';
import '../../core/network/endpoints.dart';
import '../models/models.dart';
import '../repositories/repositories.dart';

/// Paiement branché sur la Gateway : `GET /mobile/payments/methods`,
/// `POST /mobile/payments`, `GET /mobile/payments/{reference}`.
///
/// En dev, le paiement est SIMULÉ par le serveur (`simulated: true`, aucun
/// argent ne bouge). Le montant est décidé par le serveur ; `amount` n'est
/// envoyé que pour contrôle (409 AMOUNT_MISMATCH si écart). CONFIRMED vient
/// uniquement du serveur, jamais de l'app.
class ApiPaymentRepository implements PaymentRepository {
  ApiPaymentRepository(this._client, {String Function()? newKey}) : _newKey = newKey ?? _randomKey;

  final ApiClient _client;
  final String Function() _newKey;

  @override
  Future<List<PaymentMethod>> getMethods() async {
    final data = await _client.get(Endpoints.paymentMethods);
    final items = data is Map<String, dynamic> ? data['items'] : data;
    if (items is! List) return const [];
    // Seuls les moyens déclarés actifs par le serveur sont proposés.
    return [
      for (final e in items)
        if (e is Map<String, dynamic> && e['enabled'] == true) methodFromJson(e),
    ];
  }

  @override
  Future<PaymentAttempt> initiate({
    required String payableReference,
    required int amount,
    required String methodId,
    String? phone,
  }) async {
    final data = await _client.post(
      Endpoints.payments,
      body: {
        'payableReference': payableReference,
        'methodId': methodId,
        'amount': amount,
        if (phone != null && phone.isNotEmpty) 'phone': phone,
        'idempotencyKey': _newKey(),
      },
    );
    return _attempt(data);
  }

  @override
  Future<PaymentAttempt> status(String reference) async => _attempt(await _client.get(Endpoints.payment(reference)));

  // Conversion JSON (publique pour les tests) ---------------------------------

  static PaymentMethod methodFromJson(Map<String, dynamic> j) {
    final id = (j['id'] ?? '').toString();
    final name = (j['name'] ?? id).toString();
    return PaymentMethod(
      id: id,
      name: name,
      // Présentation locale (pastille) : le serveur ne fournit ni sigle ni couleur.
      shortLabel: _shortLabel(id, name),
      subtitle: (j['subtitle'] ?? '').toString(),
      brandColor: _brandColors[id] ?? 0xFF3A4660,
      needsPhoneNumber: j['needsPhoneNumber'] != false,
      enabled: j['enabled'] == true,
      simulated: j['simulated'] == true,
    );
  }

  static PaymentAttempt _attempt(dynamic data) {
    if (data is! Map<String, dynamic>) throw ApiException.server();
    final reference = (data['reference'] ?? '').toString();
    if (reference.isEmpty) throw ApiException.server();
    return PaymentAttempt(
      reference: reference,
      status: switch (data['status']?.toString()) {
        'CONFIRMED' => PaymentStatus.confirmed,
        'FAILED' => PaymentStatus.failed,
        _ => PaymentStatus.pending,
      },
      failureReason: _nonEmpty(data['failureReason']),
      receiptUrl: _nonEmpty(data['receiptUrl']),
      simulated: data['simulated'] == true,
      redirectUrl: _httpsUrl(data['redirectUrl']),
      expiresAt: DateTime.tryParse(data['expiresAt']?.toString() ?? ''),
    );
  }

  /// Seule une URL HTTPS est ouverte (jamais un autre schéma venu du réseau).
  static String? _httpsUrl(Object? v) {
    final s = _nonEmpty(v);
    final uri = s == null ? null : Uri.tryParse(s);
    return (uri != null && uri.scheme == 'https' && uri.host.isNotEmpty) ? s : null;
  }

  static String? _nonEmpty(Object? v) {
    final s = v?.toString().trim();
    return (s == null || s.isEmpty) ? null : s;
  }

  static String _shortLabel(String id, String name) => switch (id) {
        'wave' => 'W',
        'orange' || 'orange_money' => 'OM',
        'mtn' || 'mtn_momo' => 'MTN',
        'moov' || 'moov_money' => 'MV',
        _ => name.isEmpty ? '?' : name.substring(0, name.length < 2 ? name.length : 2).toUpperCase(),
      };

  static const _brandColors = {
    'orange': 0xFFE4701E,
    'mtn': 0xFF8A6D00,
    'moov': 0xFF1D5BB4,
    'wave': 0xFF1A78C2,
    'orange_money': 0xFFE4701E,
    'mtn_momo': 0xFF8A6D00,
    'moov_money': 0xFF1D5BB4,
  };

  static String _randomKey() {
    final r = Random.secure();
    return List.generate(16, (_) => r.nextInt(256).toRadixString(16).padLeft(2, '0')).join();
  }
}
