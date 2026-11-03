import '../../core/network/api_client.dart';
import '../../core/network/endpoints.dart';
import '../models/moto_quote.dart';
import '../repositories/repositories.dart';

/// Devis en ligne moto : simulation publique (sans compte) puis devis enregistrés
/// du client connecté. Le prix est toujours celui du serveur.
///
/// Les erreurs de validation (`VALIDATION_FAILED`, avec `field`) remontent en
/// `ApiException` ; un devis périmé renvoie 409 `QUOTE_EXPIRED`.
class ApiMotoQuoteRepository implements MotoQuoteRepository {
  ApiMotoQuoteRepository(this._client);

  final ApiClient _client;

  Map<String, dynamic> _map(dynamic data) => data is Map<String, dynamic> ? data : const <String, dynamic>{};

  @override
  Future<MotoQuoteForm> getForm() async => MotoQuoteForm.fromJson(_map(await _client.get(Endpoints.motoQuoteForm)));

  @override
  Future<MotoQuoteSimulation> simulate(MotoQuoteRequest request) async =>
      MotoQuoteSimulation.fromJson(_map(await _client.post(Endpoints.motoQuoteSimulate, body: request.toJson())));

  /// Enregistre le devis (prix figé) pour le client connecté.
  @override
  Future<MotoQuote> create(MotoQuoteRequest request) async =>
      MotoQuote.fromJson(_map(await _client.post(Endpoints.motoQuoteCreate, body: request.toJson())));

  @override
  Future<List<MotoQuote>> list() async {
    final items = _map(await _client.get(Endpoints.motoQuotes))['items'];
    return [
      for (final i in (items as List? ?? const [])) MotoQuote.fromJson((i as Map).cast<String, dynamic>()),
    ];
  }

  @override
  Future<MotoQuote> get(String reference) async =>
      MotoQuote.fromJson(_map(await _client.get(Endpoints.motoQuote(reference))));

  /// Accepte le devis (idempotent).
  @override
  Future<MotoQuote> accept(String reference) async =>
      MotoQuote.fromJson(_map(await _client.post(Endpoints.motoQuoteAccept(reference))));

  @override
  Future<VtcQuoteForm> getVtcForm() async => VtcQuoteForm.fromJson(_map(await _client.get(Endpoints.vtcQuoteForm)));

  @override
  Future<MotoQuoteSimulation> simulateVtc(VtcQuoteRequest request) async =>
      MotoQuoteSimulation.fromJson(_map(await _client.post(Endpoints.vtcQuoteSimulate, body: request.toJson())));

  /// Enregistre le devis VTC (prix figé, aucune remise côté client).
  @override
  Future<MotoQuote> createVtc(VtcQuoteRequest request) async =>
      MotoQuote.fromJson(_map(await _client.post(Endpoints.vtcQuoteCreate, body: request.toJson())));
}
