import '../../core/network/api_client.dart';
import '../../core/network/api_exception.dart';
import '../../core/network/endpoints.dart';
import '../models/models.dart';
import '../repositories/repositories.dart';

/// Catalogue public : `GET /public/products` et `GET /public/products/{id}`.
///
/// Le contenu est paramétré dans le back-office : un produit n'est renvoyé que
/// s'il est actif ET visible sur le mobile, trié par `displayOrder`. Un champ
/// `null`, un texte vide ou une liste vide veut dire « non communiqué » : rien
/// n'est inventé ni complété côté app.
///
/// Les référentiels de devis ne sont PAS livrés par la Gateway (aucune grille
/// tarifaire côté serveur) : [getReferentials] reste non disponible.
class ApiCatalogRepository implements CatalogRepository {
  ApiCatalogRepository(this._client);

  final ApiClient _client;

  @override
  Future<List<Product>> getProducts() async {
    final data = await _client.get(Endpoints.products);
    final items = data is Map<String, dynamic> ? data['items'] : data;
    if (items is! List) return const [];
    final maps = [for (final e in items) if (e is Map<String, dynamic>) e];
    // Le serveur trie déjà par `displayOrder` ; on le respecte aussi ici sans
    // dépendre de l'ordre de réception (tri stable, absent = à la fin).
    final indexed = [for (final (i, m) in maps.indexed) (i, m)]
      ..sort((a, b) {
        final oa = (a.$2['displayOrder'] as num?)?.toInt() ?? 1 << 30;
        final ob = (b.$2['displayOrder'] as num?)?.toInt() ?? 1 << 30;
        return oa != ob ? oa.compareTo(ob) : a.$1.compareTo(b.$1);
      });
    return [for (final e in indexed) productFromJson(e.$2)];
  }

  @override
  Future<Product> getProduct(String id) async {
    final data = await _client.get(Endpoints.product(id));
    if (data is! Map<String, dynamic>) throw ApiException.server();
    return productFromJson(data);
  }

  @override
  Future<QuoteReferentials> getReferentials(String productId) async =>
      throw ApiException.notImplemented('référentiels de devis');

  // Conversion JSON (publique pour les tests) ---------------------------------

  static Product productFromJson(Map<String, dynamic> j) {
    final title = (j['title'] ?? '').toString();
    final guarantees = _guarantees(j['guarantees']);
    return Product(
      formulas: _formulas(j['formulas'], j['guarantees']),
      exclusions: _strings(j['exclusions']),
      requiredDocuments: _strings(j['requiredDocuments']),
      faq: _faq(j['faq']),
      contentVersion: _nonEmpty(j['contentVersion']),
      id: (j['id'] ?? '').toString(),
      title: title,
      shortName: _nonEmpty(j['shortName']) ?? title,
      description: _nonEmpty(j['description']) ?? '',
      family: _family(j['family']?.toString()),
      onlineSubscription: j['onlineSubscription'] == true,
      benefits: _strings(j['benefits']),
      howItWorks: _nonEmpty(j['howItWorks']) ?? '',
      guarantees: guarantees,
      eligibility: _nonEmpty(j['eligibility']),
      iconCode: _nonEmpty(j['icon']),
    );
  }

  static ProductFamily _family(String? v) => switch (v) {
        'VEHICLE' => ProductFamily.vehicule,
        'PERSON' => ProductFamily.personne,
        'PROPERTY' => ProductFamily.bien,
        'PROFESSIONAL' => ProductFamily.professionnel,
        // Famille absente ou inconnue : groupe le moins engageant, sans bloquer l'écran.
        _ => ProductFamily.professionnel,
      };

  static List<Guarantee> _guarantees(Object? v) {
    if (v is! List) return const [];
    return [
      for (final g in v)
        if (g is Map<String, dynamic>)
          Guarantee(
            (g['name'] ?? '').toString(),
            switch (g['kind']?.toString()) {
              'MANDATORY' => GuaranteeKind.obligatoire,
              'INCLUDED' => GuaranteeKind.incluse,
              'OPTIONAL' || 'PER_FORMULA' => GuaranteeKind.selonFormule,
              _ => GuaranteeKind.aConfirmer,
            },
            description: _nonEmpty(g['description']),
            limit: _nonEmpty(g['limit']),
            formulaIds: _strings(g['formulaIds']),
          ),
    ];
  }

  /// Formules du produit, dans l'ordre du serveur ; `guaranteeIds` est résolu en
  /// noms de garanties (un identifiant inconnu est ignoré, rien n'est inventé).
  static List<Formula> _formulas(Object? formulas, Object? guarantees) {
    if (formulas is! List) return const [];
    final names = <String, String>{
      for (final g in (guarantees is List ? guarantees : const []))
        if (g is Map<String, dynamic> && g['id'] != null) g['id'].toString(): (g['name'] ?? '').toString(),
    };
    return [
      for (final f in formulas)
        if (f is Map<String, dynamic>)
          Formula(
            id: (f['id'] ?? '').toString(),
            name: (f['name'] ?? '').toString(),
            description: _nonEmpty(f['description']) ?? '',
            guarantees: [
              for (final id in _strings(f['guaranteeIds']))
                if (names[id] != null && names[id]!.isNotEmpty) names[id]!,
            ],
          ),
    ];
  }

  static List<FaqItem> _faq(Object? v) => [
        for (final e in (v is List ? v : const []))
          if (e is Map<String, dynamic> && _nonEmpty(e['question']) != null && _nonEmpty(e['answer']) != null)
            FaqItem(_nonEmpty(e['question'])!, _nonEmpty(e['answer'])!),
      ];

  static List<String> _strings(Object? v) =>
      v is List ? [for (final e in v) if (e != null && e.toString().trim().isNotEmpty) e.toString()] : const [];

  static String? _nonEmpty(Object? v) {
    final s = v?.toString().trim();
    return (s == null || s.isEmpty) ? null : s;
  }
}
