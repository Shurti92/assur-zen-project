import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/env.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';
import '../quote/quote_flow_page.dart';
import 'advisor_sheet.dart';
import 'catalog_page.dart';

/// Fiche produit : avantages, garanties validées, éligibilité, devis ou rappel.
class ProductPage extends StatefulWidget {
  const ProductPage({super.key, required this.productId});

  final String productId;

  @override
  State<ProductPage> createState() => _ProductPageState();
}

class _ProductPageState extends State<ProductPage> {
  bool _more = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: AsyncView<Product>(
        load: () => context.repos.catalog.getProduct(widget.productId),
        loading: const AzLoader(),
        builder: (context, p, _) => Column(
          children: [
            Expanded(
              child: ListView(
                padding: EdgeInsets.zero,
                children: [
                  _Hero(product: p),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(AzSpace.screen, 24, AzSpace.screen, 24),
                    child: AzGap(
                      gap: 24,
                      children: [
                        if (p.benefits.isNotEmpty) _Benefits(benefits: p.benefits),
                        _Guarantees(product: p),
                        _Formulas(formulas: p.formulas),
                        _TextList(title: 'Exclusions', items: p.exclusions, icon: Icons.remove_circle_outline_rounded),
                        _TextList(title: 'Pièces à fournir', items: p.requiredDocuments, icon: Icons.description_outlined),
                        _ProductFaq(faq: p.faq),
                        // Contenu fourni par le back-office : une rubrique vide n'est pas affichée.
                        if (p.eligibility != null || Env.useMock)
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const AzSectionHeader('Conditions d’éligibilité'),
                              const SizedBox(height: 6),
                              Text(
                                p.eligibility ?? 'Les conditions (pièces requises, âge, usage du bien) vous sont indiquées lors du devis.',
                                style: AzText.bodyMedium.copyWith(color: AzColors.muted),
                              ),
                            ],
                          ),
                        AnimatedSize(
                          duration: const Duration(milliseconds: 220),
                          child: _more && p.howItWorks.isNotEmpty
                              ? AzCard(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      const Text('Comment ça marche ?', style: AzText.titleSmall),
                                      const SizedBox(height: 6),
                                      Text(p.howItWorks, style: AzText.bodyMedium),
                                    ],
                                  ),
                                )
                              : const SizedBox(width: double.infinity),
                        ),
                        // Le tarif n'existe que pour les produits souscrits en ligne (devis).
                        if (p.onlineSubscription)
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                            decoration: BoxDecoration(color: AzColors.background, borderRadius: BorderRadius.circular(14)),
                            child: Row(
                              children: [
                                Text('Tarif', style: AzText.label.copyWith(color: AzColors.muted)),
                                const Spacer(),
                                const Text('Calculé selon votre profil', style: AzText.titleSmall),
                              ],
                            ),
                          ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            AzBottomBar(children: [
              Row(
                children: [
                  if (p.howItWorks.isNotEmpty) ...[
                    Expanded(
                      flex: 4,
                      child: AzButton.secondary(_more ? 'Moins de détails' : 'En savoir plus', onPressed: () => setState(() => _more = !_more)),
                    ),
                    const SizedBox(width: 10),
                  ],
                  Expanded(
                    flex: 6,
                    child: AzButton(
                      // Le devis en ligne moto est servi par la Gateway (prix et garanties du serveur).
                      p.id == 'moto' || p.onlineSubscription ? 'Obtenir un devis' : 'Être rappelé',
                      onPressed: () => p.id == 'moto'
                          ? context.push(AppRoutes.motoQuote, p.title)
                          : p.onlineSubscription
                              ? context.push(AppRoutes.quote, QuoteArgs(productId: p.id))
                              : showAdvisorSheet(context, product: p),
                    ),
                  ),
                ],
              ),
            ]),
          ],
        ),
      ),
    );
  }
}

class _Hero extends StatelessWidget {
  const _Hero({required this.product});

  final Product product;

  @override
  Widget build(BuildContext context) {
    return Container(
      color: productTint(product.id),
      child: Stack(
        children: [
          Positioned(
            right: -60,
            top: -40,
            child: Container(
              width: 220,
              height: 220,
              decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: const Color(0x1F033892), width: 1.5)),
            ),
          ),
          SafeArea(
            bottom: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, AzSpace.screen, 28),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  AzIconButton(icon: Icons.arrow_back_ios_new_rounded, tooltip: 'Retour', onPressed: () => Navigator.of(context).maybePop()),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(8, 16, 0, 0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          width: 84,
                          height: 84,
                          decoration: BoxDecoration(
                            color: AzColors.blue700,
                            borderRadius: BorderRadius.circular(24),
                            boxShadow: const [BoxShadow(color: Color(0x40033892), blurRadius: 28, offset: Offset(0, 12))],
                          ),
                          child: Icon(productIcon(product.id, code: product.iconCode), size: 42, color: Colors.white),
                        ),
                        const SizedBox(height: 14),
                        Semantics(header: true, child: Text(product.title, style: AzText.headline.copyWith(fontSize: 28, height: 34 / 28))),
                        if (product.description.isNotEmpty) ...[
                          const SizedBox(height: 8),
                          Text(product.description, style: AzText.body.copyWith(fontSize: 15, color: AzColors.ink2)),
                        ],
                        const SizedBox(height: 10),
                        SubscriptionModeBadge(online: product.onlineSubscription),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Benefits extends StatelessWidget {
  const _Benefits({required this.benefits});

  final List<String> benefits;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const AzSectionHeader('Les avantages'),
        const SizedBox(height: 8),
        for (final b in benefits)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 24,
                  height: 24,
                  decoration: const BoxDecoration(color: AzColors.successBg, shape: BoxShape.circle),
                  child: const Icon(Icons.check_rounded, size: 16, color: AzColors.success),
                ),
                const SizedBox(width: 12),
                Expanded(child: Text(b, style: AzText.body.copyWith(fontSize: 15, height: 22 / 15))),
              ],
            ),
          ),
      ],
    );
  }
}

/// Garanties du produit, dans l'ordre du serveur. Rubrique masquée si vide ;
/// description et plafond/franchise affichés seulement s'ils sont communiqués.
class _Guarantees extends StatelessWidget {
  const _Guarantees({required this.product});

  final Product product;

  @override
  Widget build(BuildContext context) {
    final list = product.guarantees;
    if (list.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const AzSectionHeader('Garanties'),
        const SizedBox(height: 8),
        AzCard(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          child: Column(
            children: [
              for (var i = 0; i < list.length; i++)
                Container(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  decoration: BoxDecoration(border: i == list.length - 1 ? null : const Border(bottom: BorderSide(color: AzColors.lineSoft))),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Expanded(child: Text(list[i].name, style: AzText.body.copyWith(fontWeight: FontWeight.w700))),
                          const SizedBox(width: 12),
                          Text(
                            list[i].kindLabel,
                            style: AzText.bodyMedium.copyWith(
                              fontWeight: FontWeight.w700,
                              color: switch (list[i].kind) {
                                GuaranteeKind.obligatoire || GuaranteeKind.incluse => AzColors.success,
                                _ => AzColors.warning,
                              },
                            ),
                          ),
                        ],
                      ),
                      if (list[i].description != null) ...[
                        const SizedBox(height: 4),
                        Text(list[i].description!, style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
                      ],
                      if (list[i].limit != null) ...[
                        const SizedBox(height: 4),
                        Text('Plafond / franchise : ${list[i].limit}', style: AzText.caption.copyWith(fontSize: 13, color: AzColors.ink2)),
                      ],
                    ],
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

/// Formules du produit (contenu, sans prix). Rubrique masquée si vide.
class _Formulas extends StatelessWidget {
  const _Formulas({required this.formulas});

  final List<Formula> formulas;

  @override
  Widget build(BuildContext context) {
    if (formulas.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const AzSectionHeader('Formules'),
        const SizedBox(height: 8),
        for (final f in formulas) ...[
          AzCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(f.name, style: AzText.titleSmall),
                if (f.description.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(f.description, style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
                ],
                if (f.guarantees.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  for (final g in f.guarantees)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 4),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Icon(Icons.check_rounded, size: 18, color: AzColors.success),
                          const SizedBox(width: 8),
                          Expanded(child: Text(g, style: AzText.bodyMedium)),
                        ],
                      ),
                    ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 10),
        ],
      ],
    );
  }
}

/// Liste de textes du back-office (exclusions, pièces à fournir). Masquée si vide.
class _TextList extends StatelessWidget {
  const _TextList({required this.title, required this.items, required this.icon});

  final String title;
  final List<String> items;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AzSectionHeader(title),
        const SizedBox(height: 8),
        for (final t in items)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(icon, size: 18, color: AzColors.muted),
                const SizedBox(width: 10),
                Expanded(child: Text(t, style: AzText.bodyMedium)),
              ],
            ),
          ),
      ],
    );
  }
}

/// Questions fréquentes du produit. Masquée si vide.
class _ProductFaq extends StatelessWidget {
  const _ProductFaq({required this.faq});

  final List<FaqItem> faq;

  @override
  Widget build(BuildContext context) {
    if (faq.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const AzSectionHeader('Questions fréquentes'),
        const SizedBox(height: 8),
        AzCard(
          padding: EdgeInsets.zero,
          child: Column(
            children: [
              for (var i = 0; i < faq.length; i++) ...[
                if (i > 0) const Divider(height: 1, color: AzColors.lineSoft),
                Theme(
                  data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
                  child: ExpansionTile(
                    title: Text(faq[i].question, style: AzText.body.copyWith(fontSize: 15, fontWeight: FontWeight.w700)),
                    tilePadding: const EdgeInsets.symmetric(horizontal: 16),
                    childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                    expandedAlignment: Alignment.centerLeft,
                    children: [Text(faq[i].answer, style: AzText.bodyMedium)],
                  ),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}
