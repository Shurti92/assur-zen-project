import '../../core/network/api_client.dart';
import '../../core/network/endpoints.dart';
import '../models/models.dart';
import '../repositories/repositories.dart';

/// Assistance : coordonnées, sujets, demandes et réponses du conseiller, FAQ,
/// liens légaux et avis. Tout vient du serveur et se règle dans le back-office ;
/// un champ absent, `null` ou vide est masqué, rien n'est complété.
class ApiSupportRepository implements SupportRepository {
  ApiSupportRepository(this._client);

  final ApiClient _client;

  @override
  Future<ContactInfo> getContactInfo() async {
    final data = await _client.get(Endpoints.contactInfo);
    return contactInfoFromJson(data is Map<String, dynamic> ? data : const {});
  }

  @override
  Future<LegalLinks> getLegalLinks() async {
    final data = await _client.get(Endpoints.legal);
    return legalFromJson(data is Map<String, dynamic> ? data : const {});
  }

  @override
  Future<List<SupportTopic>> getTopics() async {
    final data = await _client.get(Endpoints.supportTopics);
    final items = data is Map<String, dynamic> ? data['items'] : data;
    return [
      for (final e in (items is List ? items : const []))
        if (e is Map<String, dynamic> && _text(e['id']) != null) SupportTopic(_text(e['id'])!, _text(e['label']) ?? _text(e['id'])!),
    ];
  }

  @override
  Future<List<FaqItem>> getFaq() async {
    final data = await _client.get(Endpoints.faq);
    final items = data is Map<String, dynamic> ? data['items'] : data;
    final maps = [for (final e in (items is List ? items : const [])) if (e is Map<String, dynamic>) e];
    // Triée par le serveur ; l'ordre est respecté ici sans en dépendre (tri stable).
    final indexed = [for (final (i, m) in maps.indexed) (i, m)]
      ..sort((a, b) {
        final oa = (a.$2['order'] as num?)?.toInt() ?? 1 << 30;
        final ob = (b.$2['order'] as num?)?.toInt() ?? 1 << 30;
        return oa != ob ? oa.compareTo(ob) : a.$1.compareTo(b.$1);
      });
    return [
      for (final e in indexed)
        if (_text(e.$2['question']) != null && _text(e.$2['answer']) != null) FaqItem(_text(e.$2['question'])!, _text(e.$2['answer'])!),
    ];
  }

  @override
  Future<SupportReceipt> sendRequest({required String topicId, required String message}) async {
    final data = await _client.post(Endpoints.supportRequests, body: {'topic': topicId, 'message': message.trim()});
    final json = data is Map<String, dynamic> ? data : const <String, dynamic>{};
    return SupportReceipt(
      reference: (json['reference'] ?? '').toString(),
      expectedWithinMinutes: (json['expectedWithinMinutes'] as num?)?.toInt(),
    );
  }

  @override
  Future<List<SupportRequestItem>> getRequests() async {
    final data = await _client.get(Endpoints.supportRequests);
    final items = data is Map<String, dynamic> ? data['items'] : data;
    return [for (final e in (items is List ? items : const [])) if (e is Map<String, dynamic>) requestFromJson(e)];
  }

  @override
  Future<String> sendFeedback({required int rating, String? comment, String? contractId}) async {
    final data = await _client.post(
      Endpoints.feedback,
      body: {
        'rating': rating,
        if (comment != null && comment.trim().isNotEmpty) 'comment': comment.trim(),
        if (contractId != null && contractId.isNotEmpty) 'contractId': contractId,
      },
    );
    return data is Map<String, dynamic> ? (data['reference'] ?? '').toString() : '';
  }

  // Conversion JSON (publique pour les tests) ---------------------------------

  static String? _text(Object? v) {
    final s = v?.toString().trim();
    return (s == null || s.isEmpty) ? null : s;
  }

  static ContactInfo contactInfoFromJson(Map<String, dynamic> j) {
    List<ContactNumber> numbers(Object? v) => [
          for (final e in (v is List ? v : const []))
            if (e is Map<String, dynamic> && _text(e['number']) != null) ContactNumber(_text(e['label']) ?? '', _text(e['number'])!),
        ];

    return ContactInfo(
      phones: numbers(j['phones']),
      whatsapp: _text(j['whatsapp']),
      email: _text(j['email']),
      address: _text(j['address']),
      hours: _text(j['hours']),
      emergencyNumbers: numbers(j['emergencyNumbers']),
      supportAvailability: _text(j['supportAvailability']),
    );
  }

  static LegalLinks legalFromJson(Map<String, dynamic> j) =>
      LegalLinks(termsUrl: _text(j['termsUrl']), privacyUrl: _text(j['privacyUrl']), updatedAt: _text(j['updatedAt']));

  static SupportRequestItem requestFromJson(Map<String, dynamic> j) => SupportRequestItem(
        id: j['id'].toString(),
        reference: (j['reference'] ?? '').toString(),
        topicLabel: _text(j['topicLabel']) ?? (j['topic'] ?? '').toString(),
        message: (j['message'] ?? '').toString(),
        status: switch (j['status']?.toString()) {
          'EN_COURS' => SupportStatus.enCours,
          'REPONDU' => SupportStatus.repondu,
          'CLOTURE' => SupportStatus.cloture,
          _ => SupportStatus.enAttente,
        },
        createdAt: DateTime.tryParse((j['createdAt'] ?? '').toString())?.toLocal() ?? DateTime.now(),
        response: _text(j['response']),
        respondedAt: DateTime.tryParse((j['respondedAt'] ?? '').toString())?.toLocal(),
      );
}
