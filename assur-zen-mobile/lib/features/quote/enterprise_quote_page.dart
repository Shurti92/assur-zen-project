import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../core/ui.dart';
import '../../data/models/enterprise_quote.dart';
import '../../data/models/models.dart';
import '../catalog/advisor_sheet.dart';

/// Cotation Assurance Entreprise. Tout vient du serveur : formules, options, prime annuelle TTC, garanties.
///
/// 1. La formule. 2. L'option (sautée si la formule n'en a qu'une). 3. Le résultat : un seul montant TTC,
/// le rappel du choix, les garanties et leurs montants d'indemnité (repliables) quand le serveur en donne,
/// puis « Être rappelé ». Rien n'est enregistré à la cotation.
class EnterpriseQuotePage extends StatefulWidget {
  const EnterpriseQuotePage({super.key, required this.product});

  final Product product;

  @override
  State<EnterpriseQuotePage> createState() => _EnterpriseQuotePageState();
}

class _EnterpriseQuotePageState extends State<EnterpriseQuotePage> {
  late Future<EnterpriseForm> _form = context.repos.enterpriseQuotes.getForm();

  EnterprisePack? _pack;
  EnterpriseOption? _option;
  EnterpriseSimulation? _result;
  bool _busy = false;
  String? _error;

  /// 0 : formule, 1 : option, 2 : résultat.
  int get _step => _result != null ? 2 : (_pack != null ? 1 : 0);

  void _back() {
    setState(() {
      _error = null;
      if (_result != null) {
        _result = null;
        // Une formule à une seule option n'a pas d'écran d'option : retour à la liste.
        if (_pack != null && _pack!.options.length == 1) {
          _pack = null;
          _option = null;
        }
      } else {
        _pack = null;
        _option = null;
      }
    });
  }

  Future<void> _pick(EnterprisePack pack) async {
    setState(() {
      _pack = pack;
      _option = pack.options.length == 1 ? pack.options.first : null;
      _error = null;
    });
    if (pack.options.length == 1) await _simulate();
  }

  Future<void> _simulate() async {
    final pack = _pack;
    final option = _option;
    if (pack == null || option == null) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final sim = await context.repos.enterpriseQuotes.simulate(formulaId: pack.id, option: option.id);
      if (mounted) setState(() => _result = sim);
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = errorMessage(e);
          // Retour à la liste : pas d'écran d'option à rouvrir pour une formule à une seule option.
          if (pack.options.length == 1) {
            _pack = null;
            _option = null;
          }
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final title = switch (_step) {
      0 => 'Cotation ${widget.product.shortName}'.trim(),
      1 => _pack?.name ?? '',
      _ => 'Votre cotation',
    };
    return PopScope(
      canPop: _step == 0,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _back();
      },
      child: Scaffold(
        backgroundColor: AzColors.background,
        appBar: AzTopBar(title: title, onBack: _step == 0 ? null : _back),
        body: SafeArea(
          child: AzContentWidth(
            child: FutureBuilder<EnterpriseForm>(
              future: _form,
              builder: (context, snap) {
                if (snap.connectionState != ConnectionState.done) return const AzLoader();
                if (snap.hasError) {
                  return AzErrorState(
                    message: errorMessage(snap.error!),
                    onRetry: () => setState(() => _form = context.repos.enterpriseQuotes.getForm()),
                  );
                }
                final form = snap.data!;
                if (form.packs.isEmpty) {
                  return AzEmptyState(
                    icon: Icons.business_center_outlined,
                    title: 'Cotation indisponible',
                    message: 'Nous ne pouvons pas afficher les tarifs pour le moment. Un conseiller peut vous rappeler.',
                    actionLabel: 'Être rappelé',
                    onAction: () => showAdvisorSheet(context, product: widget.product),
                  );
                }
                return switch (_step) {
                  0 => _formulas(form),
                  1 => _options(),
                  _ => _resultView(_result!),
                };
              },
            ),
          ),
        ),
      ),
    );
  }

  Widget _steps(int active) => Row(
        children: [
          for (var i = 0; i < 2; i++) ...[
            if (i > 0) const SizedBox(width: 4),
            Expanded(child: Container(height: 5, decoration: BoxDecoration(color: i <= active ? AzColors.blue700 : AzColors.line, borderRadius: BorderRadius.circular(4)))),
          ],
        ],
      );

  Widget _formulas(EnterpriseForm form) {
    return ListView(
      padding: EdgeInsets.fromLTRB(AzSpace.screen, 4, AzSpace.screen, 24 + MediaQuery.viewPaddingOf(context).bottom),
      children: [
        _steps(0),
        const SizedBox(height: 12),
        if (_error != null) ...[
          AzBanner(kind: AzBannerKind.error, text: _error!),
          const SizedBox(height: 12),
        ],
        for (final p in form.packs) ...[
          AzCard(
            radius: 22,
            onTap: _busy ? null : () => _pick(p),
            semanticLabel: p.name,
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(p.name, style: AzText.titleSmall),
                      if (p.description != null) ...[
                        const SizedBox(height: 4),
                        Text(p.description!, style: AzText.bodyMedium.copyWith(color: AzColors.muted), maxLines: 3, overflow: TextOverflow.ellipsis),
                      ],
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                const Icon(Icons.chevron_right_rounded, color: AzColors.muted),
              ],
            ),
          ),
          const SizedBox(height: 10),
        ],
      ],
    );
  }

  Widget _options() {
    final pack = _pack!;
    return ListView(
      padding: EdgeInsets.fromLTRB(AzSpace.screen, 4, AzSpace.screen, 24 + MediaQuery.viewPaddingOf(context).bottom),
      children: [
        _steps(1),
        const SizedBox(height: 12),
        if (pack.description != null) ...[
          AzBanner(kind: AzBannerKind.info, text: pack.description!),
          const SizedBox(height: 12),
        ],
        if (_error != null) ...[
          AzBanner(kind: AzBannerKind.error, text: _error!),
          const SizedBox(height: 12),
        ],
        for (final o in pack.options) ...[
          AzCard(
            radius: 22,
            color: _option?.id == o.id ? AzColors.blue50 : Colors.white,
            borderColor: _option?.id == o.id ? AzColors.blue700 : AzColors.line,
            onTap: _busy ? null : () => setState(() => _option = o),
            semanticLabel: '${o.label}, ${formatFcfa(o.total)}',
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(o.label, style: AzText.titleSmall),
                      const SizedBox(height: 2),
                      Text('Prime annuelle TTC', style: AzText.caption.copyWith(color: AzColors.muted)),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Text(formatFcfa(o.total), style: AzText.titleSmall.copyWith(color: AzColors.blue700)),
              ],
            ),
          ),
          const SizedBox(height: 10),
        ],
        const SizedBox(height: 6),
        AzButton('Voir ma cotation', loading: _busy, onPressed: _option == null ? null : _simulate),
      ],
    );
  }

  Widget _resultView(EnterpriseSimulation sim) {
    final groups = sim.guaranteeGroups;
    final duration = sim.durationMonths == null ? '' : ' · ${sim.durationMonths} mois';
    return ListView(
      padding: EdgeInsets.fromLTRB(AzSpace.screen, 4, AzSpace.screen, 24 + MediaQuery.viewPaddingOf(context).bottom),
      children: [
        AzCard(
          color: AzColors.blue700,
          borderColor: null,
          radius: 28,
          padding: const EdgeInsets.all(18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('${sim.formulaName} · ${sim.optionLabel}', style: AzText.bodyMedium.copyWith(color: AzColors.blue100)),
              const SizedBox(height: 6),
              Text(formatFcfa(sim.total), style: AzText.display.copyWith(color: Colors.white, fontSize: 28, height: 1.1)),
              const SizedBox(height: 6),
              Text('Prime annuelle TTC$duration', style: AzText.bodyMedium.copyWith(color: AzColors.blue100)),
            ],
          ),
        ),
        const SizedBox(height: 12),
        AzSummaryCard(radius: 26, rows: [
          ('Produit', widget.product.title),
          ('Formule', sim.formulaName),
          ('Option', sim.optionLabel),
          if (sim.durationMonths != null) ('Durée', '${sim.durationMonths} mois'),
        ]),
        if (sim.note != null) ...[
          const SizedBox(height: 12),
          AzBanner(kind: AzBannerKind.info, text: sim.note!),
        ],
        // Garanties et indemnités : rubrique masquée quand le serveur n'en renseigne pas pour cette formule.
        if (groups.isNotEmpty) ...[
          const SizedBox(height: 16),
          const AzSectionHeader('Garanties et montants d’indemnité'),
          const SizedBox(height: 4),
          Text('Touchez une garantie pour voir la franchise, la base d’indemnisation et le détail.', style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
          const SizedBox(height: 8),
          AzCard(
            radius: 26,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: Column(
              children: [
                for (var i = 0; i < groups.length; i++) ...[
                  if (i > 0) const Divider(height: 1, color: AzColors.lineSoft),
                  _GuaranteeTile(group: groups[i]),
                ],
              ],
            ),
          ),
        ],
        if (sim.limits.isNotEmpty) ...[
          const SizedBox(height: 16),
          const AzSectionHeader('Limites'),
          const SizedBox(height: 8),
          AzSummaryCard(radius: 26, rows: [
            for (final l in sim.limits) (l.label, l.amount != null ? formatFcfa(l.amount!) : (l.text ?? '')),
          ]),
        ],
        const SizedBox(height: 20),
        AzButton.secondary('Être rappelé par un conseiller', onPressed: () => showAdvisorSheet(context, product: widget.product)),
      ],
    );
  }
}

String _amountText(EnterpriseGuarantee g) => g.indemnityAmount != null ? formatFcfa(g.indemnityAmount!) : (g.indemnityLabel ?? '');

/// Une garantie : nom et montant sur une ligne ; se déplie quand elle a une franchise, une base
/// d'indemnisation ou des lignes « dont ».
class _GuaranteeTile extends StatefulWidget {
  const _GuaranteeTile({required this.group});

  final EnterpriseGuaranteeGroup group;

  @override
  State<_GuaranteeTile> createState() => _GuaranteeTileState();
}

class _GuaranteeTileState extends State<_GuaranteeTile> {
  bool _open = false;

  @override
  Widget build(BuildContext context) {
    final g = widget.group.main;
    final expandable = widget.group.expandable;
    final amount = _amountText(g);
    final header = Padding(
      padding: const EdgeInsets.symmetric(vertical: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (expandable) ...[
            Icon(_open ? Icons.expand_more_rounded : Icons.chevron_right_rounded, size: 20, color: AzColors.muted),
            const SizedBox(width: 4),
          ],
          Expanded(child: Text(g.name, style: AzText.label.copyWith(fontWeight: FontWeight.w700))),
          if (amount.isNotEmpty) ...[
            const SizedBox(width: 8),
            Flexible(child: Text(amount, textAlign: TextAlign.right, style: AzText.label.copyWith(color: AzColors.blue700, fontWeight: FontWeight.w800))),
          ],
        ],
      ),
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (expandable) InkWell(onTap: () => setState(() => _open = !_open), child: header) else header,
        if (expandable && _open)
          Padding(
            padding: const EdgeInsets.only(left: 24, bottom: 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (g.deductible != null) _detail('Franchise', g.deductible!),
                if (g.basis != null) _detail('Base d’indemnisation', g.basis!),
                for (final c in widget.group.children)
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Container(
                      padding: const EdgeInsets.only(left: 10),
                      decoration: const BoxDecoration(border: Border(left: BorderSide(color: AzColors.line, width: 2))),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Expanded(child: Text('dont ${c.name}', style: AzText.bodyMedium.copyWith(color: AzColors.muted))),
                          const SizedBox(width: 8),
                          Flexible(child: Text(_amountText(c), textAlign: TextAlign.right, style: AzText.bodyMedium.copyWith(fontWeight: FontWeight.w700))),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
          ),
      ],
    );
  }

  Widget _detail(String label, String value) => Padding(
        padding: const EdgeInsets.only(top: 2),
        child: Text.rich(TextSpan(children: [
          TextSpan(text: '$label : ', style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
          TextSpan(text: value, style: AzText.bodyMedium.copyWith(fontWeight: FontWeight.w700)),
        ])),
      );
}
