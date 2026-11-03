import '../../core/network/api_client.dart';
import '../../core/network/api_exception.dart';
import '../../core/network/endpoints.dart';
import '../models/models.dart';
import '../repositories/repositories.dart';

/// Sinistres d'un assuré connecté (rôle CLIENT), branchés sur la Gateway :
/// `GET /mobile/products/{id}/claim-types`, `POST /mobile/claims`,
/// `POST /mobile/claims/{id}/files`, `GET /mobile/claims`, `GET /mobile/claims/{id}`.
///
/// Le serveur décide des types proposés, des étapes affichées (5 étapes pour 8
/// statuts réels), de l'historique et des limites de pièces. Le mobile n'invente
/// aucun libellé de statut.
class ApiClaimRepository implements ClaimRepository {
  ApiClaimRepository(this._client);

  final ApiClient _client;

  @override
  Future<List<ClaimType>> getClaimTypes(String productId) async {
    final data = await _client.get(Endpoints.claimTypes(productId));
    final items = data is Map<String, dynamic> ? data['items'] : data;
    if (items is! List) return const [];
    return [
      for (final e in items)
        if (e is Map<String, dynamic> && e['id'] != null) ClaimType(e['id'].toString(), (e['label'] ?? e['id']).toString()),
    ];
  }

  @override
  Future<List<Claim>> getClaims() async {
    final data = await _client.get(Endpoints.claims);
    final items = data is Map<String, dynamic> ? data['items'] : data;
    if (items is! List) return const [];
    return [for (final e in items) if (e is Map<String, dynamic>) claimFromJson(e)];
  }

  @override
  Future<Claim> getClaim(String id) async {
    final data = await _client.get(Endpoints.claim(id));
    if (data is! Map<String, dynamic>) throw ApiException.server();
    return claimFromJson(data);
  }

  /// Crée le sinistre puis envoie les pièces une à une. Une pièce refusée
  /// n'annule pas la déclaration (déjà enregistrée) : elle est comptée dans
  /// [Claim.failedUploads].
  @override
  Future<Claim> declare(ClaimDraft draft) async {
    final created = await _client.post(Endpoints.claims, body: declarationBody(draft));
    if (created is! Map<String, dynamic> || created['id'] == null) throw ApiException.server();
    final id = created['id'].toString();

    var failed = 0;
    for (final (kind, paths) in [('PHOTO', draft.photoPaths), ('DOCUMENT', draft.documentPaths)]) {
      for (final path in paths) {
        try {
          await _client.upload(Endpoints.claimFiles(id), filePath: path, field: 'file', fields: {'kind': kind});
        } on ApiException {
          failed++;
        }
      }
    }

    // Relecture : le suivi réel (étapes, historique) vient du serveur.
    var claim = await getClaim(id);
    // Délai de prise en charge : annoncé seulement si le serveur le fournit (clé de config vide = null).
    claim = claim.withExpectedHandlingHours((created['expectedHandlingHours'] as num?)?.toInt());
    return failed == 0 ? claim : claim.withFailedUploads(failed);
  }

  // Conversion JSON (publique pour les tests) ---------------------------------

  static Map<String, Object?> declarationBody(ClaimDraft d) {
    final day = d.occurredAt;
    String two(int n) => n.toString().padLeft(2, '0');
    final involved = d.thirdPartyInvolved;
    return {
      'contractId': d.contractId,
      'typeId': d.typeId,
      // Le serveur attend une date seule (yyyy-MM-dd), l'heure saisie n'est pas envoyée.
      'occurredAt': '${day.year}-${two(day.month)}-${two(day.day)}',
      if (d.place.trim().isNotEmpty) 'place': d.place.trim(),
      if (involved != null) 'thirdPartyInvolved': involved,
      if (involved == true && (d.thirdPartyName.isNotEmpty || d.thirdPartyPlate.isNotEmpty || d.thirdPartyInsurer.isNotEmpty))
        'thirdParty': {
          if (d.thirdPartyName.isNotEmpty) 'name': d.thirdPartyName,
          if (d.thirdPartyPlate.isNotEmpty) 'plate': d.thirdPartyPlate,
          if (d.thirdPartyInsurer.isNotEmpty) 'insurer': d.thirdPartyInsurer,
        },
      if (d.description.trim().isNotEmpty) 'description': d.description.trim(),
    };
  }

  static Claim claimFromJson(Map<String, dynamic> j) {
    final steps = <String>[
      for (final s in (j['steps'] is List ? j['steps'] as List : const []))
        if (s is Map<String, dynamic>) (s['label'] ?? '').toString() else s.toString(),
    ];
    final history = <ClaimEvent>[
      for (final h in (j['history'] is List ? j['history'] as List : const []))
        if (h is Map<String, dynamic>)
          ClaimEvent(DateTime.tryParse((h['date'] ?? '').toString())?.toLocal() ?? DateTime.now(), (h['text'] ?? '').toString()),
    ]..sort((a, b) => b.date.compareTo(a.date));
    final current = (j['currentStep'] as num?)?.toInt() ?? 0;
    return Claim(
      id: j['id'].toString(),
      type: (j['typeLabel'] ?? '').toString(),
      contractLabel: (j['contractLabel'] ?? '').toString(),
      number: (j['number'] ?? '').toString(),
      declaredAt: DateTime.tryParse((j['declaredAt'] ?? '').toString())?.toLocal() ?? DateTime.now(),
      steps: steps,
      currentStep: steps.isEmpty ? 0 : current.clamp(0, steps.length - 1),
      history: history,
      closed: j['closed'] == true,
    );
  }
}
