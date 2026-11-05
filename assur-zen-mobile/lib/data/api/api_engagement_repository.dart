import '../../core/network/api_client.dart';
import '../../core/network/api_exception.dart';
import '../../core/network/endpoints.dart';
import '../models/engagement.dart';
import '../repositories/repositories.dart';

/// Engagement branché sur la Gateway : `GET /mobile/engagement`, `PUT /mobile/engagement/consent`,
/// `GET /mobile/protection-score`. Aucune valeur n'est inventée : le serveur décide du texte de
/// consentement, du score et de ses critères.
class ApiEngagementRepository implements EngagementRepository {
  ApiEngagementRepository(this._client);

  final ApiClient _client;

  @override
  Future<Engagement> getEngagement() async {
    final data = await _client.get(Endpoints.engagement);
    if (data is! Map<String, dynamic>) throw ApiException.server();
    return Engagement.fromJson(data);
  }

  @override
  Future<Engagement> setConsent({required bool accepted, int? consentVersion}) async {
    final data = await _client.put(
      Endpoints.engagementConsent,
      body: {'accepted': accepted, if (accepted && consentVersion != null) 'consentVersion': consentVersion},
    );
    if (data is! Map<String, dynamic>) throw ApiException.server();
    return Engagement.fromJson(data);
  }

  @override
  Future<ProtectionScore> getProtectionScore() async {
    final data = await _client.get(Endpoints.protectionScore);
    if (data is! Map<String, dynamic>) throw ApiException.server();
    return ProtectionScore.fromJson(data);
  }
}
