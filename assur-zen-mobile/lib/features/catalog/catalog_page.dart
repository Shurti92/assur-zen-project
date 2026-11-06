import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';

/// Ouvre la fiche d’un produit.
void openProduct(BuildContext context, String productId) => context.push(AppRoutes.product, productId);

/// Catalogue des 9 assurances : recherche, familles, assistant de choix.
class CatalogPage extends StatefulWidget {
  const CatalogPage({super.key});

  @override
  State<CatalogPage> createState() => _CatalogPageState();
}

class _CatalogPageState extends State<CatalogPage> {
  final _search = TextEditingController();
  ProductFamily? _family;

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: AsyncView<List<Product>>(
        load: () => context.repos.catalog.getProducts(),
        builder: (context, products, reload) {
          final q = _search.text.trim().toLowerCase();
          final list = products
              .where((p) => _family == null || p.family == _family)
              .where((p) => q.isEmpty || '${p.title} ${p.description}'.toLowerCase().contains(q))
              .toList();
          return RefreshIndicator(
            onRefresh: reload,
            child: ListView(
              padding: const EdgeInsets.only(bottom: 24),
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(AzSpace.screen, 20, AzSpace.screen, 12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Semantics(header: true, child: Text('Nos solutions d’assurance', style: AzText.display.copyWith(fontSize: 28, height: 1.1))),
                      const SizedBox(height: 4),
                      Text('Choisissez votre protection.', style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
                      const SizedBox(height: 14),
                      TextField(
                        controller: _search,
                        onChanged: (_) => setState(() {}),
                        textInputAction: TextInputAction.search,
                        style: AzText.body.copyWith(fontSize: 15),
                        decoration: InputDecoration(
                          hintText: 'Rechercher une assurance',
                          prefixIcon: const Icon(Icons.search_rounded, color: AzColors.muted),
                          suffixIcon: _search.text.isEmpty
                              ? null
                              : IconButton(
                                  tooltip: 'Effacer',
                                  icon: const Icon(Icons.close_rounded, color: AzColors.muted),
                                  onPressed: () => setState(_search.clear),
                                ),
                          filled: true,
                          fillColor: Colors.white,
                          contentPadding: const EdgeInsets.symmetric(vertical: 14),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(18),
                            borderSide: const BorderSide(color: AzColors.line),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(18),
                            borderSide: const BorderSide(color: AzColors.blue700, width: 2),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                SizedBox(
                  height: 44,
                  child: ListView(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.symmetric(horizontal: AzSpace.screen),
                    children: [
                      AzChoiceChip(label: 'Tous', selected: _family == null, onTap: () => setState(() => _family = null)),
                      for (final f in ProductFamily.values) ...[
                        const SizedBox(width: 8),
                        AzChoiceChip(label: f.label, selected: _family == f, onTap: () => setState(() => _family = f)),
                      ],
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: AzSpace.screen),
                  child: Column(
                    children: [
                      AzCard(
                        color: AzColors.orange50,
                        borderColor: null,
                        radius: 22,
                        onTap: () => context.push(AppRoutes.finder),
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                        child: Row(
                          children: [
                            const AzIconTile(icon: Icons.help_outline_rounded, size: 44, background: Colors.white, color: AzColors.orange700),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Text('Pas sûr de votre choix ?', style: AzText.titleSmall),
                                  Text('Trouvez la bonne assurance en 2 questions.',
                                      style: AzText.caption.copyWith(color: AzColors.warningInk, fontSize: 12.5, fontWeight: FontWeight.w500)),
                                ],
                              ),
                            ),
                            const SizedBox(width: 8),
                            Container(
                              width: 36,
                              height: 36,
                              decoration: const BoxDecoration(color: AzColors.orange500, shape: BoxShape.circle),
                              child: const Icon(Icons.arrow_forward_rounded, color: Colors.white, size: 18),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 10),
                      // Grille de deux colonnes : les deux cartes d'une ligne ont la même hauteur.
                      for (var i = 0; i < list.length; i += 2) ...[
                        if (i > 0) const SizedBox(height: 10),
                        IntrinsicHeight(
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              Expanded(child: ProductTile(product: list[i])),
                              const SizedBox(width: 10),
                              Expanded(child: i + 1 < list.length ? ProductTile(product: list[i + 1]) : const SizedBox.shrink()),
                            ],
                          ),
                        ),
                      ],
                      if (list.isEmpty)
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 40),
                          child: Text('Aucune assurance ne correspond à « ${_search.text} ».',
                              textAlign: TextAlign.center, style: AzText.body.copyWith(color: AzColors.muted)),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

/// Carte produit du catalogue (grille de deux colonnes) : pastille d'icône, nom, description, mode de souscription.
class ProductTile extends StatelessWidget {
  const ProductTile({super.key, required this.product});

  final Product product;

  @override
  Widget build(BuildContext context) {
    return AzCard(
      radius: 26,
      onTap: () => openProduct(context, product.id),
      semanticLabel: product.title,
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AzIconTile(icon: productIcon(product.id, code: product.iconCode), size: 48, background: productTint(product.id), iconSize: 26, radius: 17),
          const SizedBox(height: 10),
          Text(product.title, style: AzText.titleSmall.copyWith(fontSize: 15.5, height: 1.2)),
          if (product.description.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(
              product.description,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              style: AzText.caption.copyWith(fontSize: 12.5, height: 17 / 12.5, fontWeight: FontWeight.w500),
            ),
          ],
          const Spacer(),
          const SizedBox(height: 8),
          SubscriptionModeBadge(online: product.onlineSubscription, quoteOnly: product.family == ProductFamily.professionnel),
        ],
      ),
    );
  }
}

/// « Souscription en ligne » ou « Avec un conseiller » (décision 1.2).
class SubscriptionModeBadge extends StatelessWidget {
  const SubscriptionModeBadge({super.key, required this.online, this.quoteOnly = false});

  final bool online;

  /// Produit professionnel : le parcours s'arrête au devis.
  final bool quoteOnly;

  @override
  Widget build(BuildContext context) {
    if (quoteOnly) return const AzStatusBadge(label: 'Sur devis', foreground: AzColors.info, background: AzColors.infoBg, dot: false);
    return online
        ? const AzStatusBadge(label: 'Souscription en ligne', foreground: AzColors.success, background: AzColors.successBg, dot: false)
        : const AzStatusBadge(label: 'Avec un conseiller', foreground: AzColors.info, background: AzColors.infoBg, dot: false);
  }
}
