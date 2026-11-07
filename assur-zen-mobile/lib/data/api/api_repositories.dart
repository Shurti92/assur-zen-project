import '../../core/network/api_client.dart';
import '../../core/network/api_exception.dart';
import '../../core/network/endpoints.dart';
import '../models/models.dart';
import '../repositories/repositories.dart';
import 'api_auth_repository.dart';
import 'api_catalog_repository.dart';
import 'api_claim_repository.dart';
import 'api_contract_repository.dart';
import 'api_moto_quote_repository.dart';
import 'api_engagement_repository.dart';
import 'api_promotion_repository.dart';
import 'api_loyalty_repository.dart';
import 'api_notification_repository.dart';
import 'api_payment_repository.dart';
import 'api_support_repository.dart';

/// Dépôts branchés sur le backend Java / Spring Boot.
///
/// Authentification, contrats, sinistres et paiement (simulé en dev par le
/// serveur) sont branchés. Les autres dépôts lèvent `ApiException.notImplemented` tant que les
/// routes et les DTO réels ne sont pas confirmés : aucun schéma n’est inventé ici.
///
/// Pour les brancher : documentation OpenAPI en main, compléter
/// `core/network/endpoints.dart`, puis chaque méthode ci-dessous.
abstract final class ApiRepositories {
  static Repositories create(
    ApiClient client, {
    required Future<String?> Function() readRefreshToken,
    required Future<void> Function(String accessToken, String refreshToken) saveTokens,
  }) =>
      Repositories(
        auth: ApiAuthRepository(client, readRefreshToken: readRefreshToken, saveTokens: saveTokens),
        catalog: ApiCatalogRepository(client),
        quotes: ApiQuoteRepository(client),
        motoQuotes: ApiMotoQuoteRepository(client),
        payments: ApiPaymentRepository(client),
        contracts: ApiContractRepository(client),
        claims: ApiClaimRepository(client),
        notifications: ApiNotificationRepository(client),
        support: ApiSupportRepository(client),
        engagement: ApiEngagementRepository(client),
        promotions: ApiPromotionRepository(client),
        loyalty: ApiLoyaltyRepository(client),
      );
}

Never _todo(String what) => throw ApiException.notImplemented(what);

/// Devis : NON livré par la Gateway (aucune grille tarifaire côté serveur) ; seule
/// la demande de rappel est branchée (`POST /public/callbacks`).
class ApiQuoteRepository implements QuoteRepository {
  ApiQuoteRepository(this._client);

  final ApiClient _client;

  @override
  Future<Quote> computeQuote(QuoteDraft draft) async => _todo('calcul du devis');

  @override
  Future<void> uploadDocument({required String quoteNumber, required RequiredDocument type, required String filePath}) async {
    // Exemple d’envoi multipart : nom du champ à confirmer avec le backend.
    await _client.upload(Endpoints.quoteDocuments(quoteNumber), filePath: filePath, field: 'file', fields: {'type': type.name});
  }

  @override
  Future<CallbackReceipt> requestCallback({required String phone, required String slot, String? productId}) async {
    final data = await _client.post(
      Endpoints.callbacks,
      body: {
        'phone': phone,
        'slot': slot,
        if (productId != null && productId.isNotEmpty) 'productId': productId,
      },
    );
    final json = data is Map<String, dynamic> ? data : const <String, dynamic>{};
    return CallbackReceipt(
      reference: (json['reference'] ?? '').toString(),
      expectedWithinMinutes: (json['expectedWithinMinutes'] as num?)?.toInt(),
    );
  }
}
