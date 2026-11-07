import '../../core/network/api_client.dart';
import '../../core/network/endpoints.dart';
import '../models/models.dart';
import '../repositories/repositories.dart';

/// Notifications in-app du client : `GET /mobile/notifications`,
/// `GET /mobile/notifications/unread-count`, `POST /{id}/read`, `POST /read-all`.
///
/// Titres et textes sont rédigés par le serveur : le mobile les affiche tels quels.
class ApiNotificationRepository implements NotificationRepository {
  ApiNotificationRepository(this._client);

  final ApiClient _client;

  @override
  Future<List<AppNotification>> getNotifications() async {
    // Une page de 50 (maximum du serveur), de la plus récente à la plus ancienne.
    final data = await _client.get(Endpoints.notifications, query: {'page': '0', 'size': '50'});
    final items = data is Map<String, dynamic> ? data['items'] : data;
    if (items is! List) return const [];
    return [for (final e in items) if (e is Map<String, dynamic>) notificationFromJson(e)];
  }

  @override
  Future<int> getUnreadCount() async {
    final data = await _client.get(Endpoints.notificationsUnreadCount);
    return data is Map<String, dynamic> ? (data['unreadCount'] as num?)?.toInt() ?? 0 : 0;
  }

  @override
  Future<void> markRead(String id) async {
    await _client.post(Endpoints.notificationRead(id));
  }

  @override
  Future<void> markAllRead() async {
    await _client.post(Endpoints.notificationsReadAll);
  }

  @override
  Future<List<NotificationPreference>> getPreferences() async =>
      preferencesFromJson(await _client.get(Endpoints.notificationPreferences));

  @override
  Future<NotificationSettings> getSettings() async => settingsFromJson(await _client.get(Endpoints.notificationPreferences));

  @override
  Future<NotificationSettings> updateSettings({bool? push, bool? sms, String? smsConsentVersion, List<CategoryChannels>? categories}) async {
    final data = await _client.put(
      Endpoints.notificationPreferences,
      body: {
        if (push != null || sms != null)
          'channels': {
            if (push != null) 'push': {'enabled': push},
            if (sms != null) 'sms': {'enabled': sms, if (sms && smsConsentVersion != null) 'consentVersion': smsConsentVersion},
          },
        if (categories != null)
          'categories': [
            for (final c in categories) {'category': categoryCode(c.category), 'push': c.push, 'sms': c.sms},
          ],
      },
    );
    return settingsFromJson(data);
  }

  static NotificationSettings settingsFromJson(dynamic data) {
    final json = data is Map<String, dynamic> ? data : const <String, dynamic>{};
    final channels = json['channels'];
    final hasChannels = channels is Map<String, dynamic>;
    final push = hasChannels && channels['push'] is Map<String, dynamic> ? channels['push'] as Map<String, dynamic> : const <String, dynamic>{};
    final sms = hasChannels && channels['sms'] is Map<String, dynamic> ? channels['sms'] as Map<String, dynamic> : const <String, dynamic>{};
    String? text(Object? v) {
      final s = v?.toString().trim();
      return (s == null || s.isEmpty) ? null : s;
    }

    final cats = json['categories'];
    return NotificationSettings(
      hasChannels: hasChannels,
      pushEnabled: push['enabled'] != false,
      smsEnabled: sms['enabled'] == true,
      smsConsentAt: DateTime.tryParse((sms['consentAt'] ?? '').toString())?.toLocal(),
      // Version à renvoyer à l'activation : la version actuelle du texte, sinon celle déjà acceptée.
      smsConsentVersion: text(sms['currentConsentVersion']) ?? text(sms['consentVersion']),
      smsConsentText: text(sms['consentText']),
      smsPhone: text(sms['phone']),
      categories: [
        for (final e in (cats is List ? cats : const []))
          if (e is Map<String, dynamic> && e['category'] != null)
            CategoryChannels(categoryFromCode(e['category'].toString()), push: e['push'] != false, sms: e['sms'] == true),
      ],
    );
  }

  @override
  Future<List<NotificationPreference>> setPreference(NotificationCategory category, bool enabled) async {
    // Seules les catégories envoyées changent ; le serveur renvoie la liste complète.
    final data = await _client.put(
      Endpoints.notificationPreferences,
      body: {
        'items': [
          {'category': categoryCode(category), 'enabled': enabled},
        ],
      },
    );
    return preferencesFromJson(data);
  }

  @override
  Future<String> registerDevice({required String token, String? appVersion}) async {
    final data = await _client.post(
      Endpoints.devices,
      body: {
        'token': token,
        'platform': 'ANDROID',
        if (appVersion != null && appVersion.isNotEmpty) 'appVersion': appVersion,
      },
    );
    return data is Map<String, dynamic> ? (data['id'] ?? '').toString() : '';
  }

  @override
  Future<void> unregisterDevice(String deviceId) async {
    await _client.delete(Endpoints.device(deviceId));
  }

  // Conversion JSON (publique pour les tests) ---------------------------------

  static String categoryCode(NotificationCategory c) => switch (c) {
        NotificationCategory.echeance => 'EXPIRY',
        NotificationCategory.souscription => 'SUBSCRIPTION',
        NotificationCategory.paiement => 'PAYMENT',
        NotificationCategory.sinistre => 'CLAIM',
        NotificationCategory.document => 'DOCUMENT',
        NotificationCategory.service => 'SERVICE',
        NotificationCategory.loyalty => 'LOYALTY',
      };

  static NotificationCategory categoryFromCode(String? code) => switch (code) {
        'EXPIRY' => NotificationCategory.echeance,
        'SUBSCRIPTION' => NotificationCategory.souscription,
        'PAYMENT' => NotificationCategory.paiement,
        'CLAIM' => NotificationCategory.sinistre,
        'DOCUMENT' => NotificationCategory.document,
        'LOYALTY' => NotificationCategory.loyalty,
        _ => NotificationCategory.service,
      };

  static List<NotificationPreference> preferencesFromJson(dynamic data) {
    final items = data is Map<String, dynamic> ? data['items'] : data;
    return [
      for (final e in (items is List ? items : const []))
        if (e is Map<String, dynamic> && e['category'] != null)
          NotificationPreference(categoryFromCode(e['category'].toString()), e['enabled'] != false),
    ];
  }

  static AppNotification notificationFromJson(Map<String, dynamic> j) {
    String? id(Object? v) {
      final s = v?.toString().trim();
      return (s == null || s.isEmpty) ? null : s;
    }

    return AppNotification(
      id: j['id'].toString(),
      category: categoryFromCode(j['category']?.toString()),
      title: (j['title'] ?? '').toString(),
      body: (j['body'] ?? '').toString(),
      date: DateTime.tryParse((j['createdAt'] ?? '').toString())?.toLocal() ?? DateTime.now(),
      read: j['read'] == true,
      contractId: id(j['contractId']),
      claimId: id(j['claimId']),
      quoteReference: id(j['quoteReference']),
      target: id(j['target']),
    );
  }
}
