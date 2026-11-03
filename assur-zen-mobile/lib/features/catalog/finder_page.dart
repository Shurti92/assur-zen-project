import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';
import 'catalog_page.dart';

/// « Trouver mon assurance » : 2 questions pour orienter un client indécis.
/// Les correspondances besoin → produit pourront venir du serveur.
class FinderPage extends StatefulWidget {
  const FinderPage({super.key});

  @override
  State<FinderPage> createState() => _FinderPageState();
}

class _FinderPageState extends State<FinderPage> {
  int _step = 0;
  String? _productId;

  static const _needs = [
    ('vehicule', 'Mon véhicule', Icons.directions_car_outlined),
    ('sante', 'Ma santé et ma famille', Icons.health_and_safety_outlined),
    ('habitation', 'Mon logement', Icons.home_outlined),
    ('voyage', 'Mes voyages', Icons.luggage_outlined),
    ('scolaire', 'La scolarité de mes enfants', Icons.school_outlined),
    ('vie', 'L’avenir de mes proches', Icons.volunteer_activism_outlined),
    ('entreprise', 'Mon entreprise', Icons.business_center_outlined),
  ];

  static const _vehicles = [
    ('auto', 'Une voiture personnelle', Icons.directions_car_outlined),
    ('moto', 'Une moto ou un deux-roues', Icons.two_wheeler_outlined),
    ('vtc', 'Je fais du VTC (Yango…)', Icons.local_taxi_outlined),
    ('auto', 'Un véhicule professionnel', Icons.business_center_outlined),
  ];

  void _pick(String id) {
    setState(() {
      if (_step == 0 && id == 'vehicule') {
        _step = 1;
      } else {
        _productId = id;
        _step = 2;
      }
    });
  }

  void _back() {
    if (_step == 2) {
      setState(() => _step = const {'auto', 'moto', 'vtc'}.contains(_productId) ? 1 : 0);
    } else if (_step == 1) {
      setState(() => _step = 0);
    } else {
      Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: _step == 0,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _back();
      },
      child: Scaffold(
        backgroundColor: Colors.white,
        appBar: AzTopBar(title: 'Trouver mon assurance', onBack: _back),
        body: AnimatedSwitcher(
          duration: const Duration(milliseconds: 220),
          child: _step < 2 ? _question(context) : _result(context),
        ),
      ),
    );
  }

  Widget _question(BuildContext context) {
    final options = _step == 0 ? _needs : _vehicles;
    return ListView(
      key: ValueKey('q$_step'),
      padding: const EdgeInsets.fromLTRB(AzSpace.screen, 8, AzSpace.screen, 24),
      children: [
        Text('Question ${_step + 1} sur 2', style: AzText.label.copyWith(color: AzColors.orange700, fontWeight: FontWeight.w700)),
        const SizedBox(height: 8),
        Text(_step == 0 ? 'Que souhaitez-vous protéger ?' : 'De quel véhicule s’agit-il ?', style: AzText.headline),
        const SizedBox(height: 16),
        for (final (id, label, icon) in options) ...[
          AzOptionTile(
            title: label,
            selected: false,
            showRadio: false,
            leading: AzIconTile(icon: icon),
            trailing: const Icon(Icons.chevron_right_rounded, color: AzColors.muted),
            onTap: () => _pick(id),
          ),
          const SizedBox(height: 10),
        ],
      ],
    );
  }

  Widget _result(BuildContext context) {
    return AsyncView<Product>(
      key: ValueKey('r$_productId'),
      load: () => context.repos.catalog.getProduct(_productId!),
      loading: const AzLoader(),
      builder: (context, p, _) => ListView(
        padding: const EdgeInsets.fromLTRB(AzSpace.screen, 8, AzSpace.screen, 24),
        children: [
          Text('Notre suggestion', style: AzText.label.copyWith(color: AzColors.success, fontWeight: FontWeight.w700)),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(22),
            decoration: BoxDecoration(color: productTint(p.id), borderRadius: BorderRadius.circular(AzRadius.hero)),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                AzIconTile(icon: productIcon(p.id, code: p.iconCode), size: 64, background: AzColors.blue700, color: Colors.white, radius: 18),
                const SizedBox(height: 14),
                Text(p.title, style: AzText.headline),
                if (p.description.isNotEmpty) ...[
                  const SizedBox(height: 6),
                  Text(p.description, style: AzText.body.copyWith(fontSize: 15, color: AzColors.ink2)),
                ],
                const SizedBox(height: 12),
                SubscriptionModeBadge(online: p.onlineSubscription),
              ],
            ),
          ),
          const SizedBox(height: 20),
          AzButton('Voir cette assurance', onPressed: () => Navigator.of(context).pushReplacementNamed(AppRoutes.product, arguments: p.id)),
          const SizedBox(height: 8),
          AzButton.ghost('Recommencer', onPressed: () => setState(() => _step = 0)),
          const SizedBox(height: 8),
          ValueListenableBuilder<ContactInfo>(
            valueListenable: ContactDirectory.info,
            builder: (context, info, _) {
              final phone = info.primaryPhone;
              if (phone == null) return const SizedBox.shrink();
              return Text('Un doute ? Contactez un conseiller au ${formatPhone(phone)}.',
                  textAlign: TextAlign.center, style: AzText.caption.copyWith(fontSize: 13, fontWeight: FontWeight.w500));
            },
          ),
        ],
      ),
    );
  }
}
