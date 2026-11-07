import '../../core/network/api_client.dart';
import '../../core/network/api_exception.dart';
import '../../core/network/endpoints.dart';
import '../models/loyalty.dart';
import '../repositories/repositories.dart';

/// Zen Points : `GET /mobile/loyalty`, `POST /mobile/loyalty/consent`, `GET /mobile/loyalty/rules`,
/// `GET /mobile/loyalty/history`. Route absente ou fonction coupée : le programme est masqué.
class ApiLoyaltyRepository implements LoyaltyRepository {
  ApiLoyaltyRepository(this._client);

  final ApiClient _client;

  @override
  Future<LoyaltyOverview?> getOverview() async {
    try {
      return overviewFromJson(await _client.get(Endpoints.loyalty));
    } on ApiException catch (e) {
      // 403 FEATURE_DISABLED, 404 (route pas encore déployée) : pas de programme.
      if (e.statusCode == 403 || e.statusCode == 404 || e.statusCode == 405) return null;
      rethrow;
    }
  }

  @override
  Future<LoyaltyOverview?> setConsent({required bool accepted, String? version}) async {
    final data = await _client.post(
      Endpoints.loyaltyConsent,
      body: {'accepted': accepted, if (version != null) 'version': version},
    );
    return overviewFromJson(data);
  }

  @override
  Future<LoyaltyRules> getRules() async => rulesFromJson(await _client.get(Endpoints.loyaltyRules));

  @override
  Future<LoyaltyHistory> getHistory({int page = 0, int size = 20}) async =>
      historyFromJson(await _client.get(Endpoints.loyaltyHistory, query: {'page': '$page', 'size': '$size'}));

  @override
  Future<LoyaltyDiscounts?> getDiscounts() async {
    try {
      return discountsFromJson(await _client.get(Endpoints.loyaltyDiscounts));
    } on ApiException catch (e) {
      if (e.statusCode == 403 || e.statusCode == 404 || e.statusCode == 405) return null;
      rethrow;
    }
  }

  // Conversion JSON (publique pour les tests) ---------------------------------

  /// `{enabled, levelLabel, tiers[]}` ; coupé, sans palier ou corps inattendu : aucune remise à montrer.
  static LoyaltyDiscounts? discountsFromJson(dynamic data) {
    if (data is! Map<String, dynamic> || data['enabled'] != true) return null;
    final raw = data['tiers'];
    final tiers = <LoyaltyDiscountTier>[
      for (final t in (raw is List ? raw : const []))
        if (t is Map<String, dynamic> && _text(t['productKey']) != null && t['percent'] is num && (t['percent'] as num) > 0)
          LoyaltyDiscountTier(
            productKey: _text(t['productKey'])!,
            minMonths: _int(t['minMonths']) ?? 1,
            maxMonths: _int(t['maxMonths']) ?? _int(t['minMonths']) ?? 1,
            percent: t['percent'] as num,
          ),
    ];
    if (tiers.isEmpty) return null;
    return LoyaltyDiscounts(levelLabel: _text(data['levelLabel']), tiers: tiers);
  }

  static String? _text(Object? v) {
    final s = v?.toString().trim();
    return (s == null || s.isEmpty) ? null : s;
  }

  static int? _int(Object? v) => v is num ? v.round() : null;

  static LoyaltyOverview? overviewFromJson(dynamic data) {
    if (data is! Map<String, dynamic>) return null;
    if (data['enabled'] == false) return null;
    final c = data['consent'];
    final consent = c is Map<String, dynamic>
        ? LoyaltyConsent(
            accepted: c['accepted'] == true,
            currentVersion: _text(c['currentVersion']),
            text: _text(c['text']),
            acceptedAt: DateTime.tryParse((c['acceptedAt'] ?? '').toString())?.toLocal(),
          )
        : const LoyaltyConsent(accepted: false);
    LoyaltyLevel? level;
    final l = data['level'];
    if (l is Map<String, dynamic> && _text(l['label']) != null) {
      LoyaltyNextLevel? next;
      final n = l['nextLevel'];
      if (n is Map<String, dynamic> && _text(n['label']) != null) {
        next = LoyaltyNextLevel(label: _text(n['label'])!, threshold: _int(n['threshold']) ?? 0, pointsToGo: _int(n['pointsToGo']) ?? 0);
      }
      level = LoyaltyLevel(
        code: _text(l['code']) ?? '',
        label: _text(l['label'])!,
        qualifyingPoints: _int(l['qualifyingPoints']) ?? 0,
        next: next,
      );
    }
    LoyaltyExpiring? expiring;
    final x = data['expiring'];
    if (x is Map<String, dynamic> && (_int(x['points']) ?? 0) > 0) {
      expiring = LoyaltyExpiring(points: _int(x['points'])!, date: DateTime.tryParse((x['date'] ?? '').toString()));
    }
    return LoyaltyOverview(consent: consent, balance: _int(data['balance']), level: level, expiring: expiring);
  }

  static LoyaltyRules rulesFromJson(dynamic data) {
    if (data is! Map<String, dynamic>) return const LoyaltyRules();
    List<Map<String, dynamic>> list(Object? v) => v is List ? [for (final e in v) if (e is Map<String, dynamic>) e] : const [];
    return LoyaltyRules(
      termsVersion: _text(data['termsVersion']),
      expiryMonths: _int(data['expiryMonths']),
      actions: [
        for (final a in list(data['actions']))
          if (_text(a['label']) != null)
            LoyaltyAction(code: _text(a['code']) ?? '', label: _text(a['label'])!, points: _int(a['points']) ?? 0, limit: _text(a['limit'])),
      ],
      levels: [
        for (final l in list(data['levels']))
          if (_text(l['label']) != null)
            LoyaltyLevelRule(
              code: _text(l['code']) ?? '',
              label: _text(l['label'])!,
              threshold: _int(l['threshold']) ?? 0,
              perks: [for (final p in (l['perks'] is List ? l['perks'] as List : const [])) if (_text(p) != null) _text(p)!],
            ),
      ],
    );
  }

  static LoyaltyHistory historyFromJson(dynamic data) {
    if (data is! Map<String, dynamic>) return const LoyaltyHistory(items: [], total: 0);
    final items = data['items'];
    return LoyaltyHistory(
      total: _int(data['total']) ?? 0,
      items: [
        for (final e in (items is List ? items : const []))
          if (e is Map<String, dynamic> && e['id'] != null)
            LoyaltyEntry(
              id: e['id'].toString(),
              date: DateTime.tryParse((e['date'] ?? '').toString())?.toLocal() ?? DateTime.now(),
              type: switch (e['type']?.toString()) {
                'EXPIRE' => LoyaltyEntryType.expire,
                'ADJUST' => LoyaltyEntryType.adjust,
                'CANCEL' => LoyaltyEntryType.cancel,
                _ => LoyaltyEntryType.earn,
              },
              label: _text(e['label']) ?? '',
              points: _int(e['points']) ?? 0,
            ),
      ],
    );
  }
}
