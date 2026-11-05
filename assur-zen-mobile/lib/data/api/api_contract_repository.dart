import '../../core/network/api_client.dart';
import '../../core/network/api_exception.dart';
import '../../core/network/downloaded_file.dart';
import '../../core/network/endpoints.dart';
import '../models/models.dart';
import '../repositories/repositories.dart';

/// Contrats d’un assuré connecté (rôle CLIENT), branchés sur la Gateway :
/// `GET /mobile/contracts`, `GET /mobile/contracts/{id}`,
/// `POST /mobile/contracts/{id}/requests`.
///
/// Le serveur décide du statut, de l’éligibilité au renouvellement (et de son
/// montant), de l’attestation et des actions autorisées : le mobile ne
/// recalcule rien. Aucun champ absent de la réponse n’est inventé.
class ApiContractRepository implements ContractRepository {
  ApiContractRepository(this._client);

  final ApiClient _client;

  @override
  Future<List<Contract>> getContracts() async {
    final data = await _client.get(Endpoints.contracts);
    final items = (data is Map<String, dynamic> ? data['items'] : data);
    if (items is! List) return const [];
    return [for (final e in items) if (e is Map<String, dynamic>) contractFromJson(e)];
  }

  @override
  Future<Contract> getContract(String id) async {
    final data = await _client.get(Endpoints.contract(id));
    if (data is! Map<String, dynamic>) throw ApiException.server();
    return contractFromJson(data);
  }

  @override
  Future<DownloadedFile> downloadDocument(String contractId, String documentId) =>
      _client.download(Endpoints.contractDocument(contractId, documentId));

  @override
  Future<DownloadedFile> downloadAttestation(String contractId) =>
      _client.download(Endpoints.contractAttestationFile(contractId));

  @override
  Future<ContractRequestReceipt> requestChange({
    required String contractId,
    required ContractRequestType type,
    String? message,
  }) async {
    final data = await _client.post(
      Endpoints.contractRequests(contractId),
      body: {
        'type': type == ContractRequestType.termination ? 'TERMINATION' : 'MODIFICATION',
        if (message != null && message.trim().isNotEmpty) 'message': message.trim(),
      },
    );
    final json = data is Map<String, dynamic> ? data : const <String, dynamic>{};
    return ContractRequestReceipt(
      reference: (json['reference'] ?? '').toString(),
      expectedWithinHours: (json['expectedWithinHours'] as num?)?.toInt(),
    );
  }

  // Conversion JSON (publique pour les tests) ---------------------------------

  static Contract contractFromJson(Map<String, dynamic> j) {
    return Contract(
      id: j['id'].toString(),
      productId: (j['productId'] ?? '').toString(),
      name: (j['productName'] ?? '').toString(),
      insuredObject: (j['insuredLabel'] ?? '').toString(),
      number: (j['number'] ?? '').toString(),
      status: _status(j['status']?.toString()),
      holderName: j['holderName']?.toString(),
      startDate: _date(j['startDate']),
      endDate: _date(j['endDate']),
      premium: (j['premium'] as num?)?.round(),
      renewal: _renewal(j['renewal']),
      actions: _actions(j['actions']),
      attestation: _attestation(j['attestation']),
      guarantees: [
        for (final g in _list(j['guarantees']))
          Guarantee((g['name'] ?? '').toString(), _guaranteeKind(g['kind']?.toString())),
      ],
      documents: [
        for (final d in _list(j['documents']))
          ContractDocument(
            id: d['id'].toString(),
            name: (d['name'] ?? '').toString(),
            type: _docType(d['type']?.toString()),
            status: _docStatus(d['status']?.toString()),
            url: d['url']?.toString(),
          ),
      ],
    );
  }

  static List<Map<String, dynamic>> _list(Object? v) =>
      v is List ? [for (final e in v) if (e is Map<String, dynamic>) e] : const [];

  static DateTime? _date(Object? v) => v is String ? DateTime.tryParse(v) : null;

  static ContractRenewal? _renewal(Object? v) {
    if (v is! Map<String, dynamic>) return null;
    return ContractRenewal(
      eligible: v['eligible'] == true,
      opensOn: _date(v['opensOn']),
      daysLeft: (v['daysLeft'] as num?)?.toInt(),
      amount: (v['amount'] as num?)?.round(),
      durationMonths: (v['durationMonths'] as num?)?.toInt(),
      payableReference: v['payableReference']?.toString(),
    );
  }

  static ContractActions _actions(Object? v) {
    if (v is! Map<String, dynamic>) return ContractActions.none;
    return ContractActions(
      canDeclareClaim: v['canDeclareClaim'] == true,
      canRequestChange: v['canRequestChange'] == true,
      canRequestTermination: v['canRequestTermination'] == true,
    );
  }

  static ContractAttestation? _attestation(Object? v) {
    if (v is! Map<String, dynamic>) return null;
    return ContractAttestation(
      available: v['available'] == true,
      verificationCode: v['verificationCode']?.toString(),
      validFrom: _date(v['validFrom']),
      validUntil: _date(v['validUntil']),
    );
  }

  static ContractStatus _status(String? s) => switch (s) {
        'ACTIVE' => ContractStatus.actif,
        'PENDING' => ContractStatus.enAttente,
        'SUSPENDED' => ContractStatus.suspendu,
        'EXPIRED' => ContractStatus.expire,
        'TERMINATED' => ContractStatus.resilie,
        _ => ContractStatus.enAttente,
      };

  static GuaranteeKind _guaranteeKind(String? s) => switch (s) {
        'MANDATORY' => GuaranteeKind.obligatoire,
        'INCLUDED' => GuaranteeKind.incluse,
        'OPTIONAL' => GuaranteeKind.selonFormule,
        _ => GuaranteeKind.aConfirmer,
      };

  static DocumentType _docType(String? s) => switch (s) {
        'ATTESTATION' => DocumentType.attestation,
        'CEDEAO' => DocumentType.carteBrune,
        'CARTE_GRISE' => DocumentType.carteGrise,
        'CONDITIONS' || 'CONDITIONS_PARTICULIERES' => DocumentType.conditions,
        'RECEIPT' => DocumentType.recu,
        _ => DocumentType.autre,
      };

  static DocumentStatus _docStatus(String? s) => switch (s) {
        'AVAILABLE' => DocumentStatus.disponible,
        'GENERATING' => DocumentStatus.enGeneration,
        _ => DocumentStatus.nonDisponible,
      };
}
