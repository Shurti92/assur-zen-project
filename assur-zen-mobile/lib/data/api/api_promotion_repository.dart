import '../../core/network/api_client.dart';
import '../../core/network/endpoints.dart';
import '../models/promo_popup.dart';
import '../repositories/repositories.dart';

/// Pop-up publicitaires : `GET /public/promotions/popups` et `POST .../{id}/events`.
/// Sans route (404), sans réseau ou liste vide : aucun pop-up, l'application n'en est pas gênée.
class ApiPromotionRepository implements PromotionRepository {
  ApiPromotionRepository(this._client);

  final ApiClient _client;

  @override
  Future<List<PromoPopup>> getPopups({String placement = 'HOME'}) async {
    try {
      final data = await _client.get(Endpoints.promoPopups, query: {'placement': placement});
      return PromoPopup.listFromJson(data);
    } catch (_) {
      return const [];
    }
  }

  @override
  Future<void> sendPopupEvent(String id, String type) async {
    try {
      await _client.post(Endpoints.promoPopupEvent(id), body: {'type': type});
    } catch (_) {
      // mesure indicative : un échec ne se voit pas
    }
  }
}
