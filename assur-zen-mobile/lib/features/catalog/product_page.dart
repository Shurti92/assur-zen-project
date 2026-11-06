import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/env.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';
import '../quote/quote_flow_page.dart';
import 'advisor_sheet.dart';

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
      backgroundColor: AzColors.background,
      body: AsyncView<Product>(
        load: () => context.repos.catalog.getProduct(widget.productId),
        loading: const AzLoader(),
        builder: (context, p, _) {
          // Produit professionnel : le parcours s'arrête au devis (la suite se fait avec un conseiller).
          final professional = p.family == ProductFamily.professionnel;
          return Column(
            children: [
              Expanded(
                child: ListView(
                  padding: EdgeInsets.zero,
                  children: [
                    _Hero(product: p),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(AzSpace.screen, 12, AzSpace.screen, 24),
                      child: AzGap(
                        gap: 22,
                        children: [
                          if (professional)
                            AzCard(
                              color: AzColors.orange50,
                              borderColor: null,
                              radius: 22,
                              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                              child: Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Padding(
                                    padding: EdgeInsets.only(top: 2),
                                    child: Icon(Icons.info_outline_rounded, size: 18, color: AzColors.orange700),
                                  ),
                                  const SizedBox(width: 10),
                                  Expanded(
                                    child: Text(
                                      'Pour ce produit, l’application vous donne un devis. La souscription se poursuit avec un conseiller.',
                                      style: AzText.bodyMedium.copyWith(color: AzColors.warningInk, fontSize: 13),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          if (p.benefits.isNotEmpty) _Benefits(benefits: p.benefits),
                          if (p.formulas.isNotEmpty) _Formulas(formulas: p.formulas),
                          if (p.guarantees.isNotEmpty) _Guarantees(product: p),
                          if (p.assistancePlans.isNotEmpty) AssistancePlans(plans: p.assistancePlans),
                          if (p.requiredDocuments.isNotEmpty) _TextList(title: 'Pièces à fournir', items: p.requiredDocuments, icon: Icons.description_outlined),
                          if (p.exclusions.isNotEmpty) _TextList(title: 'Exclusions', items: p.exclusions, icon: Icons.remove_circle_outline_rounded),
                          if (p.faq.isNotEmpty) _ProductFaq(faq: p.faq),
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
                                    radius: 26,
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
                        flex: 5,
                        child: AzButton.secondary(_more ? 'Moins de détails' : 'En savoir plus', height: 52, radius: 16, horizontalPadding: 8, onPressed: () => setState(() => _more = !_more)),
                      ),
                      const SizedBox(width: 10),
                    ],
                    Expanded(
                      flex: 5,
                      child: AzButton(
                        // Le devis en ligne moto est servi par la Gateway (prix et garanties du serveur).
                        p.id == 'moto' || p.id == 'automobile-vtc' || p.id == 'voyage' || p.onlineSubscription || professional ? 'Obtenir un devis' : 'Être rappelé',
                        variant: AzButtonVariant.accent,
                        height: 52,
                        radius: 16,
                        horizontalPadding: 8,
                        onPressed: () => p.id == 'moto'
                            ? context.push(AppRoutes.motoQuote, p.title)
                            : p.id == 'automobile-vtc'
                                ? context.push(AppRoutes.vtcQuote, p.title)
                                : p.id == 'voyage'
                                    ? context.push(AppRoutes.voyageQuote, p.title)
                                    : p.onlineSubscription
                                        ? context.push(AppRoutes.quote, QuoteArgs(productId: p.id))
                                        : showAdvisorSheet(context, product: p),
                      ),
                    ),
                  ],
                ),
              ]),
            ],
          );
        },
      ),
    );
  }
}

/// Carte bleue d'identité du produit : icône, mode de souscription, nom, description, deux repères.
class _Hero extends StatelessWidget {
  const _Hero({required this.product});

  final Product product;

  @override
  Widget build(BuildContext context) {
    final professional = product.family == ProductFamily.professionnel;
    return SafeArea(
      bottom: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(AzSpace.screen, 8, AzSpace.screen, 0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            AzIconButton(icon: Icons.arrow_back_ios_new_rounded, tooltip: 'Retour', onPressed: () => Navigator.of(context).maybePop()),
            const SizedBox(height: 10),
            AzCard(
              color: AzColors.blue700,
              borderColor: null,
              radius: 28,
              padding: const EdgeInsets.all(18),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(
                    children: [
                      AzIconTile(
                        icon: productIcon(product.id, code: product.iconCode),
                        size: 52,
                        iconSize: 28,
                        background: const Color(0x29FFFFFF),
                        color: Colors.white,
                        radius: 18,
                      ),
                      const Spacer(),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(color: professional ? AzColors.orange500 : Colors.white, borderRadius: BorderRadius.circular(AzRadius.pill)),
                        child: Text(
                          professional ? 'Sur devis' : (product.onlineSubscription ? 'Souscription en ligne' : 'Avec un conseiller'),
                          style: AzText.caption.copyWith(color: professional ? Colors.white : AzColors.blue700, fontWeight: FontWeight.w700),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),
                  Semantics(header: true, child: Text(product.title, style: AzText.display.copyWith(color: Colors.white, fontSize: 26, height: 1.12, letterSpacing: -0.3))),
                  if (product.description.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    _HeroDescription(text: product.description),
                  ],
                  // Repères : le tarif n'existe que pour les produits en ligne ou sur devis.
                  if (product.onlineSubscription || professional) ...[
                    const SizedBox(height: 14),
                    Row(
                      children: [
                        Expanded(child: _HeroFact('Tarif', professional ? 'Établi sur devis' : 'Calculé selon votre profil')),
                        if (professional) ...[
                          const SizedBox(width: 8),
                          const Expanded(child: _HeroFact('Suite', 'Avec un conseiller')),
                        ],
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _HeroFact extends StatelessWidget {
  const _HeroFact(this.label, this.value);

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
      decoration: BoxDecoration(color: const Color(0x1FFFFFFF), borderRadius: BorderRadius.circular(16)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: AzText.caption.copyWith(color: AzColors.blue100, fontSize: 11.5, fontWeight: FontWeight.w500)),
          const SizedBox(height: 2),
          Text(value, style: AzText.label.copyWith(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 13.5)),
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
        AzCard(
          radius: 26,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          child: Column(
            children: [
              for (var i = 0; i < benefits.length; i++) ...[
                if (i > 0) const Divider(height: 1, color: AzColors.lineSoft),
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        width: 28,
                        height: 28,
                        decoration: const BoxDecoration(color: AzColors.successBg, shape: BoxShape.circle),
                        child: const Icon(Icons.check_rounded, size: 17, color: AzColors.success),
                      ),
                      const SizedBox(width: 12),
                      Expanded(child: Text(benefits[i], style: AzText.body.copyWith(fontSize: 14.5, fontWeight: FontWeight.w600, height: 21 / 14.5))),
                    ],
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

/// Accordéon en carte arrondie : titre, résumé et pastille, contenu qui se déplie. Encadré en bleu quand il est ouvert.
/// Sans contenu à déplier, la ligne reste une carte simple (pas de flèche).
class _Accordion extends StatefulWidget {
  const _Accordion({required this.title, this.subtitle, this.pill, this.body = const [], this.initiallyOpen = false});

  final String title;
  final String? subtitle;
  final Widget? pill;
  final List<Widget> body;
  final bool initiallyOpen;

  @override
  State<_Accordion> createState() => _AccordionState();
}

class _AccordionState extends State<_Accordion> {
  late bool _open = widget.initiallyOpen && widget.body.isNotEmpty;

  @override
  Widget build(BuildContext context) {
    final head = Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(widget.title, style: AzText.titleSmall.copyWith(fontSize: 15, height: 1.3)),
              if (widget.subtitle != null)
                Text(widget.subtitle!, maxLines: 2, overflow: TextOverflow.ellipsis, style: AzText.caption.copyWith(fontSize: 12.5, fontWeight: FontWeight.w500)),
            ],
          ),
        ),
        if (widget.pill != null) ...[const SizedBox(width: 8), widget.pill!],
      ],
    );
    Widget card(Widget child) => Container(
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(26),
            border: Border.all(color: _open ? AzColors.blue700 : AzColors.line, width: _open ? 2 : 1),
          ),
          clipBehavior: Clip.antiAlias,
          child: child,
        );
    if (widget.body.isEmpty) {
      return card(Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14), child: head));
    }
    return card(
      Theme(
        data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
        child: ExpansionTile(
          initiallyExpanded: _open,
          onExpansionChanged: (v) => setState(() => _open = v),
          tilePadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 14),
          expandedAlignment: Alignment.centerLeft,
          expandedCrossAxisAlignment: CrossAxisAlignment.start,
          iconColor: AzColors.blue700,
          collapsedIconColor: AzColors.muted,
          title: head,
          children: [
            const Divider(height: 1, color: AzColors.lineSoft),
            const SizedBox(height: 10),
            ...widget.body,
          ],
        ),
      ),
    );
  }
}

/// Garanties du produit, en accordéons, dans l'ordre du serveur. Rubrique masquée si vide ;
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
        for (var i = 0; i < list.length; i++) ...[
          if (i > 0) const SizedBox(height: 10),
          _Accordion(
            initiallyOpen: i == 0,
            title: list[i].name,
            pill: _kindPill(list[i]),
            body: [
              if (list[i].description != null) Text(list[i].description!, style: AzText.bodyMedium.copyWith(color: AzColors.ink2, fontSize: 13)),
              if (list[i].limit != null) ...[
                const SizedBox(height: 8),
                Text.rich(
                  TextSpan(children: [
                    TextSpan(text: 'Plafond / franchise  ', style: AzText.caption.copyWith(fontSize: 13, fontWeight: FontWeight.w500)),
                    TextSpan(text: list[i].limit, style: AzText.caption.copyWith(fontSize: 13, color: AzColors.ink, fontWeight: FontWeight.w700)),
                  ]),
                ),
              ],
            ],
          ),
        ],
      ],
    );
  }

  static Widget _kindPill(Guarantee g) {
    final (Color fg, Color bg) = switch (g.kind) {
      GuaranteeKind.obligatoire => (AzColors.success, AzColors.successBg),
      GuaranteeKind.incluse => (AzColors.blue700, AzColors.blue50),
      _ => (AzColors.warning, AzColors.warningBg),
    };
    return AzStatusBadge(label: g.kindLabel, foreground: fg, background: bg, dot: false);
  }
}

/// Formules du produit (contenu, sans prix), en accordéons. Rubrique masquée si vide.
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
        for (var i = 0; i < formulas.length; i++) ...[
          if (i > 0) const SizedBox(height: 10),
          _Accordion(
            initiallyOpen: i == 0,
            title: formulas[i].name,
            subtitle: formulas[i].description.isEmpty ? null : formulas[i].description,
            body: [
              for (final g in formulas[i].guarantees)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Padding(padding: EdgeInsets.only(top: 1), child: Icon(Icons.check_rounded, size: 18, color: AzColors.success)),
                      const SizedBox(width: 8),
                      Expanded(child: Text(g, style: AzText.bodyMedium.copyWith(color: AzColors.ink, fontWeight: FontWeight.w600, fontSize: 13.5))),
                    ],
                  ),
                ),
            ],
          ),
        ],
      ],
    );
  }
}

/// Liste de textes du back-office (exclusions, pièces à fournir), en carte arrondie. Masquée si vide.
class _TextList extends StatelessWidget {
  const _TextList({required this.title, required this.items, required this.icon});

  final String title;
  final List<String> items;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) return const SizedBox.shrink();
    final danger = icon == Icons.remove_circle_outline_rounded;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AzSectionHeader(title),
        const SizedBox(height: 8),
        AzCard(
          radius: 26,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          child: Column(
            children: [
              for (var i = 0; i < items.length; i++) ...[
                if (i > 0) const Divider(height: 1, color: AzColors.lineSoft),
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (danger)
                        Icon(icon, size: 20, color: AzColors.error)
                      else
                        AzIconTile(icon: icon, size: 34, iconSize: 18, radius: 12),
                      const SizedBox(width: 10),
                      Expanded(child: Padding(padding: EdgeInsets.only(top: danger ? 1 : 6), child: Text(items[i], style: AzText.bodyMedium.copyWith(color: AzColors.ink, fontWeight: FontWeight.w600)))),
                    ],
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

/// Questions fréquentes du produit, en carte arrondie. Masquée si vide.
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
          radius: 26,
          padding: EdgeInsets.zero,
          child: Column(
            children: [
              for (var i = 0; i < faq.length; i++) ...[
                if (i > 0) const Divider(height: 1, color: AzColors.lineSoft),
                Theme(
                  data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
                  child: ExpansionTile(
                    title: Text(faq[i].question, style: AzText.body.copyWith(fontSize: 14, fontWeight: FontWeight.w600)),
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

/// Libellé du prix d'un niveau : celui du serveur, sinon formaté depuis le montant (« Gratuit » à 0).
/// L'unité (par an, par contrat…) n'est pas connue : elle n'est jamais ajoutée.
String assistancePriceLabel(AssistancePlan plan) =>
    plan.priceLabel ?? (plan.price == 0 ? 'Gratuit' : formatFcfa(plan.price));

/// Éligibilité par âge du véhicule, à titre informatif. `null` : aucune borne, rien à afficher.
String? assistanceEligibility(AssistancePlan plan) {
  String years(int n) => n == 1 ? '1 an' : '$n ans';
  final min = plan.minVehicleAge;
  final max = plan.maxVehicleAge;
  if (min == null && max == null) return null;
  if (min != null && max != null) return min == max ? 'Véhicule de ${years(min)}' : 'Véhicule de $min à ${years(max)}';
  if (max != null) return 'Véhicule de ${years(max)} maximum';
  return 'Véhicule de ${years(min!)} minimum';
}

/// Niveaux d'assistance (Assurance Auto) : une carte comparable par niveau, dans l'ordre du serveur.
/// Masqués quand le serveur n'en envoie pas.
class AssistancePlans extends StatelessWidget {
  const AssistancePlans({super.key, required this.plans});

  final List<AssistancePlan> plans;

  @override
  Widget build(BuildContext context) {
    if (plans.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const AzSectionHeader('Niveaux d’assistance'),
        const SizedBox(height: 8),
        for (final plan in plans) ...[
          _AssistancePlanCard(plan: plan),
          const SizedBox(height: 10),
        ],
      ],
    );
  }
}

class _AssistancePlanCard extends StatelessWidget {
  const _AssistancePlanCard({required this.plan});

  final AssistancePlan plan;

  Widget _pill(String text, {required Color fg, required Color bg}) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
        decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(AzRadius.pill)),
        child: Text(text, style: AzText.caption.copyWith(color: fg, fontWeight: FontWeight.w700)),
      );

  @override
  Widget build(BuildContext context) {
    final eligibility = assistanceEligibility(plan);
    return AzCard(
      padding: EdgeInsets.zero,
      radius: 26,
      borderColor: plan.recommended ? AzColors.blue700 : AzColors.line,
      child: Theme(
        data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
        child: ExpansionTile(
          tilePadding: const EdgeInsets.fromLTRB(16, 6, 12, 6),
          childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          expandedAlignment: Alignment.centerLeft,
          expandedCrossAxisAlignment: CrossAxisAlignment.start,
          title: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: Text(plan.name, style: AzText.titleSmall)),
              const SizedBox(width: 12),
              Flexible(
                child: Text(
                  assistancePriceLabel(plan),
                  textAlign: TextAlign.right,
                  style: AzText.titleSmall.copyWith(color: AzColors.blue700),
                ),
              ),
            ],
          ),
          subtitle: (plan.recommended || plan.benefit || eligibility != null)
              ? Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 6,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      if (plan.recommended) _pill('Recommandé', fg: Colors.white, bg: AzColors.blue700),
                      if (plan.benefit) _pill('Avantage gratuit', fg: AzColors.success, bg: AzColors.successBg),
                      if (eligibility != null) Text(eligibility, style: AzText.caption.copyWith(color: AzColors.muted)),
                    ],
                  ),
                )
              : null,
          children: [
            for (final section in plan.sections) ...[
              Padding(
                padding: const EdgeInsets.only(top: 8, bottom: 6),
                child: Text(section.title, style: AzText.bodyMedium.copyWith(fontWeight: FontWeight.w700)),
              ),
              for (final item in section.items)
                Padding(
                  padding: const EdgeInsets.only(bottom: 6),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(Icons.check_rounded, size: 18, color: AzColors.success),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(item.text, style: AzText.bodyMedium),
                            if (item.condition != null)
                              Text(item.condition!, style: AzText.caption.copyWith(color: AzColors.muted)),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
            ],
          ],
        ),
      ),
    );
  }
}

/// Description du produit dans le bandeau : quatre lignes, puis « Lire la suite » si le texte est plus long.
class _HeroDescription extends StatefulWidget {
  const _HeroDescription({required this.text});

  final String text;

  @override
  State<_HeroDescription> createState() => _HeroDescriptionState();
}

class _HeroDescriptionState extends State<_HeroDescription> {
  bool _all = false;

  @override
  Widget build(BuildContext context) {
    final style = AzText.bodyMedium.copyWith(color: AzColors.blue100);
    return LayoutBuilder(builder: (context, box) {
      final painter = TextPainter(text: TextSpan(text: widget.text, style: style), maxLines: 4, textDirection: TextDirection.ltr, textScaler: MediaQuery.textScalerOf(context))
        ..layout(maxWidth: box.maxWidth);
      final long = painter.didExceedMaxLines;
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(widget.text, maxLines: _all ? null : 4, overflow: _all ? TextOverflow.visible : TextOverflow.ellipsis, style: style),
          if (long)
            InkWell(
              onTap: () => setState(() => _all = !_all),
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Text(_all ? 'Réduire' : 'Lire la suite', style: AzText.label.copyWith(color: Colors.white, fontWeight: FontWeight.w800)),
              ),
            ),
        ],
      );
    });
  }
}
