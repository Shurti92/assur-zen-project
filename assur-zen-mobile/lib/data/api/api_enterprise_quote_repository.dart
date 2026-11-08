import '../../core/network/api_client.dart';
import '../../core/network/api_exception.dart';
import '../../core/network/endpoints.dart';
import '../models/enterprise_quote.dart';
import '../repositories/repositories.dart';

/// Cotation Entreprise : `GET /public/quotes/entreprise/form` et `POST /public/quotes/entreprise/simulate`
/// (publiques, rien n'est enregistré).
class ApiEnterpriseQuoteRepository implements EnterpriseQuoteRepository {
  ApiEnterpriseQuoteRepository(this._client);

  final ApiClient _client;

  @override
  Future<EnterpriseForm> getForm() async => formFromJson(await _client.get(Endpoints.enterpriseQuoteForm));

  @override
  Future<EnterpriseSimulation> simulate({required String formulaId, required String option}) async {
    final data = await _client.post(Endpoints.enterpriseQuoteSimulate, body: {'formulaId': formulaId, 'option': option});
    final sim = simulationFromJson(data);
    if (sim == null) throw ApiException.server();
    return sim;
  }

  @override
  Future<List<EnterpriseActivityDomain>> getActivityDomains() async {
    try {
      return domainsFromJson(await _client.get(Endpoints.enterpriseDomains));
    } on ApiException {
      // Liste facultative : sans elle, le client saisit son domaine en texte libre.
      return const [];
    }
  }

  @override
  Future<EnterpriseRequestReceipt> submitRequest(EnterpriseRequestInput input) async {
    final receipt = receiptFromJson(await _client.post(Endpoints.enterpriseRequests, body: input.toJson()));
    if (receipt == null) throw ApiException.server();
    return receipt;
  }

  // Conversion JSON (publique pour les tests) ---------------------------------

  static List<EnterpriseActivityDomain> domainsFromJson(dynamic data) {
    if (data is! Map<String, dynamic>) return const [];
    return [
      for (final d in _maps(data['items']))
        if (_text(d['label']) != null) EnterpriseActivityDomain(code: _text(d['code']) ?? _text(d['label'])!, label: _text(d['label'])!),
    ];
  }

  /// Sans référence, la demande n'est pas déclarée prise en compte.
  static EnterpriseRequestReceipt? receiptFromJson(dynamic data) {
    if (data is! Map<String, dynamic>) return null;
    final reference = _text(data['reference']);
    if (reference == null) return null;
    return EnterpriseRequestReceipt(
      reference: reference,
      status: _text(data['status']),
      formulaName: _text(data['formulaName']),
      optionLabel: _text(data['optionLabel']),
      total: _int(data['total']),
      message: _text(data['message']),
    );
  }

  static String? _text(Object? v) {
    final s = v?.toString().trim();
    return (s == null || s.isEmpty) ? null : s;
  }

  static int? _int(Object? v) => v is num ? v.round() : null;

  static List<Map<String, dynamic>> _maps(Object? v) => v is List ? [for (final e in v) if (e is Map<String, dynamic>) e] : const [];

  /// Une option sans identifiant, sans libellé ou sans montant est ignorée ; une formule sans option aussi.
  static EnterpriseForm formFromJson(dynamic data) {
    if (data is! Map<String, dynamic>) return const EnterpriseForm(packs: []);
    final packs = <EnterprisePack>[];
    for (final p in _maps(data['packs'])) {
      final id = _text(p['id']);
      final name = _text(p['name']);
      if (id == null || name == null) continue;
      final options = [
        for (final o in _maps(p['options']))
          if (_text(o['id']) != null && _int(o['total']) != null)
            EnterpriseOption(id: _text(o['id'])!, label: _text(o['label']) ?? _text(o['id'])!, total: _int(o['total'])!),
      ];
      if (options.isEmpty) continue;
      packs.add(EnterprisePack(id: id, name: name, description: _text(p['description']), options: options));
    }
    return EnterpriseForm(packs: packs, durationMonths: _int(data['durationMonths']));
  }

  static EnterpriseSimulation? simulationFromJson(dynamic data) {
    if (data is! Map<String, dynamic>) return null;
    final total = _int(data['total']);
    final formulaId = _text(data['formulaId']);
    final option = _text(data['option']);
    if (total == null || formulaId == null || option == null) return null;
    return EnterpriseSimulation(
      formulaId: formulaId,
      formulaName: _text(data['formulaName']) ?? formulaId,
      formulaDescription: _text(data['formulaDescription']),
      option: option,
      optionLabel: _text(data['optionLabel']) ?? option,
      durationMonths: _int(data['durationMonths']),
      total: total,
      currency: _text(data['currency']),
      guarantees: [
        for (final g in _maps(data['guarantees']))
          if (_text(g['name']) != null)
            EnterpriseGuarantee(
              name: _text(g['name'])!,
              level: _int(g['level']) ?? 0,
              // Un montant à 0 n'est jamais un montant : le libellé du serveur s'affiche à la place.
              indemnityAmount: (_int(g['indemnityAmount']) ?? 0) > 0 ? _int(g['indemnityAmount']) : null,
              indemnityLabel: _text(g['indemnityLabel']),
              deductible: _text(g['deductible']),
              basis: _text(g['basis']),
            ),
      ],
      limits: [
        for (final l in _maps(data['limits']))
          if (_text(l['label']) != null && ((_int(l['amount']) ?? 0) > 0 || _text(l['text']) != null))
            EnterpriseLimit(label: _text(l['label'])!, amount: (_int(l['amount']) ?? 0) > 0 ? _int(l['amount']) : null, text: _text(l['text'])),
      ],
      note: _text(data['note']),
    );
  }
}
