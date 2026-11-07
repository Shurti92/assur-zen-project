import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../core/ui.dart';
import '../../data/models/loyalty.dart';

/// Annonce, sous la simulation d'un devis, la remise Zen Points qui sera appliquée à l'enregistrement.
///
/// La simulation publique renvoie toujours le prix public : la remise n'apparaît qu'à la création du devis.
/// La phrase n'est affichée que si le serveur donne au client un palier pour ce produit et cette durée ;
/// le pourcentage est celui du palier, l'application ne calcule aucun montant.
class ZenDiscountHint extends StatefulWidget {
  const ZenDiscountHint({super.key, required this.productKey, required this.months});

  /// Clé du produit du devis : `moto` ou `automobile-vtc`.
  final String productKey;
  final int months;

  @override
  State<ZenDiscountHint> createState() => _ZenDiscountHintState();
}

class _ZenDiscountHintState extends State<ZenDiscountHint> {
  LoyaltyDiscounts? _discounts;
  bool _asked = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_asked) return;
    _asked = true;
    // Sans compte, aucune remise n'est connue : le calcul public reste inchangé.
    if (!context.session.isLoggedIn) return;
    final repo = context.repos.loyalty;
    () async {
      try {
        final d = await repo.getDiscounts();
        if (mounted) setState(() => _discounts = d);
      } catch (_) {}
    }();
  }

  @override
  Widget build(BuildContext context) {
    final percent = _discounts?.percentFor(widget.productKey, widget.months);
    if (percent == null) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: AzBanner(
        kind: AzBannerKind.success,
        text: 'Remise Zen Points −${formatPercent(percent)} % appliquée à l’enregistrement du devis.',
      ),
    );
  }
}
