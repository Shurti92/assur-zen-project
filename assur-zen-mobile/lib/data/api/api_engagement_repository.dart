import 'dart:math';

import '../../core/network/api_client.dart';
import '../../core/network/api_exception.dart';
import '../../core/network/endpoints.dart';
import '../models/engagement.dart';
import '../repositories/repositories.dart';

/// Engagement branché sur la Gateway : `GET /mobile/engagement`, `PUT /mobile/engagement/consent`,
/// `GET /mobile/protection-score`. Aucune valeur n'est inventée : le serveur décide du texte de
/// consentement, du score et de ses critères.
class ApiEngagementRepository implements EngagementRepository {
  ApiEngagementRepository(this._client, {String Function()? newKey}) : _newKey = newKey ?? _randomKey;

  final ApiClient _client;
  final String Function() _newKey;

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

  @override
  Future<List<AppBadge>> getBadges() async => AppBadge.listFromJson(await _client.get(Endpoints.badges));

  /// 204 (corps vide) : rien à proposer aujourd'hui. Une question injouable (sans texte ou avec moins de
  /// deux choix) est traitée de la même façon : on n'affiche pas une question cassée.
  @override
  Future<QuizQuestion?> getQuizToday() async => QuizQuestion.fromJson(await _client.get(Endpoints.quizToday));

  @override
  Future<QuizAnswer> answerQuiz({required String questionId, required String choiceId}) async {
    final data = await _client.post(
      Endpoints.quizAnswer(questionId),
      body: {'choiceId': choiceId, 'idempotencyKey': _newKey()},
    );
    final answer = QuizAnswer.fromJson(data, choiceId: choiceId);
    if (answer == null) throw ApiException.server();
    return answer;
  }

  static String _randomKey() {
    final r = Random.secure();
    return List.generate(16, (_) => r.nextInt(256).toRadixString(16).padLeft(2, '0')).join();
  }
}
