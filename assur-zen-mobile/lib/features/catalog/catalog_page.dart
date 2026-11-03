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
                      Semantics(header: true, child: const Text('Nos solutions d’assurance', style: AzText.headline)),
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
                            borderRadius: BorderRadius.circular(14),
                            borderSide: const BorderSide(color: AzColors.line),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(14),
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
                        onTap: () => context.push(AppRoutes.finder),
                        child: Row(
                          children: [
                            const AzIconTile(icon: Icons.help_outline_rounded, size: 48, background: AzColors.orange500, color: Colors.white),
                            const SizedBox(width: 14),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Text('Pas sûr de votre choix ?', style: AzText.titleSmall),
                                  Text('Trouvez la bonne assurance en 2 questions.',
                                      style: AzText.caption.copyWith(color: AzColors.warningInk, fontSize: 13, fontWeight: FontWeight.w500)),
                                ],
                              ),
                            ),
                            const Icon(Icons.chevron_right_rounded, color: AzColors.orange700),
                          ],
                        ),
                      ),
                      const SizedBox(height: 10),
                      for (final p in list) ...[
                        ProductTile(product: p),
                        const SizedBox(height: 10),
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

/// Carte produit du catalogue.
class ProductTile extends StatelessWidget {
  const ProductTile({super.key, required this.product});

  final Product product;

  @override
  Widget build(BuildContext context) {
    return AzCard(
      onTap: () => openProduct(context, product.id),
      semanticLabel: product.title,
      padding: const EdgeInsets.all(14),
      child: Row(
        children: [
          AzIconTile(icon: productIcon(product.id, code: product.iconCode), size: 60, background: productTint(product.id), iconSize: 30, radius: 16),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(product.title, style: AzText.body.copyWith(fontWeight: FontWeight.w800)),
                if (product.description.isNotEmpty) ...[
                  const SizedBox(height: 2),
                  Text(product.description, style: AzText.caption.copyWith(fontSize: 13, height: 18 / 13, fontWeight: FontWeight.w500)),
                ],
                const SizedBox(height: 6),
                SubscriptionModeBadge(online: product.onlineSubscription),
              ],
            ),
          ),
          const Icon(Icons.chevron_right_rounded, color: AzColors.muted),
        ],
      ),
    );
  }
}

/// « Souscription en ligne » ou « Avec un conseiller » (décision 1.2).
class SubscriptionModeBadge extends StatelessWidget {
  const SubscriptionModeBadge({super.key, required this.online});

  final bool online;

  @override
  Widget build(BuildContext context) {
    return online
        ? const AzStatusBadge(label: 'Souscription en ligne', foreground: AzColors.success, background: AzColors.successBg, dot: false)
        : const AzStatusBadge(label: 'Avec un conseiller', foreground: AzColors.info, background: AzColors.infoBg, dot: false);
  }
}
