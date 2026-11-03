import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../catalog/catalog_page.dart';
import '../claims/claims_page.dart';
import '../contracts/contracts_page.dart';
import '../home/home_page.dart';
import '../profile/profile_page.dart';

/// Index des onglets de la barre de navigation.
abstract final class ShellTab {
  static const home = 0;
  static const contracts = 1;
  static const catalog = 2; // « Souscrire »
  static const claims = 3;
  static const profile = 4;
}

/// Permet à un écran de changer d’onglet : `ShellScope.of(context).goTo(ShellTab.claims)`.
class ShellScope extends InheritedWidget {
  const ShellScope({super.key, required this.goTo, required this.current, required super.child});

  final ValueChanged<int> goTo;
  final int current;

  static ShellScope? maybeOf(BuildContext context) => context.dependOnInheritedWidgetOfExactType<ShellScope>();

  @override
  bool updateShouldNotify(ShellScope oldWidget) => current != oldWidget.current;
}

/// Coquille principale : 5 onglets, chacun garde son état (IndexedStack).
class MainShell extends StatefulWidget {
  const MainShell({super.key, this.initialTab = ShellTab.home});

  final int initialTab;

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  late int _tab = widget.initialTab;
  final Set<int> _visited = {};

  void _goTo(int tab) {
    if (tab == _tab) return;
    setState(() => _tab = tab);
  }

  @override
  Widget build(BuildContext context) {
    _visited.add(_tab);
    final session = AppScope.of(context).session;
    return ListenableBuilder(
      listenable: session,
      builder: (context, _) {
        final guest = !session.isLoggedIn;
        final pages = <Widget>[
          guest ? const GuestGate(tab: ShellTab.home) : const HomePage(),
          guest ? const GuestGate(tab: ShellTab.contracts) : const ContractsPage(),
          const CatalogPage(),
          guest ? const GuestGate(tab: ShellTab.claims) : const ClaimsPage(),
          guest ? const GuestGate(tab: ShellTab.profile) : const ProfilePage(),
        ];
        return PopScope(
          canPop: _tab == ShellTab.home || guest,
          onPopInvokedWithResult: (didPop, _) {
            if (!didPop) _goTo(ShellTab.home);
          },
          child: ShellScope(
            goTo: _goTo,
            current: _tab,
            child: Scaffold(
              body: IndexedStack(
                index: _tab,
                children: [
                  for (var i = 0; i < pages.length; i++)
                    // Chargement paresseux : un onglet n’est construit qu’à sa première visite.
                    _visited.contains(i) ? pages[i] : const SizedBox.shrink(),
                ],
              ),
              bottomNavigationBar: AzBottomNav(
                current: _tab,
                unread: session.unreadCount,
                onSelect: _goTo,
              ),
            ),
          ),
        );
      },
    );
  }
}

/// Barre de navigation à 5 onglets, « Souscrire » mis en avant au centre.
class AzBottomNav extends StatelessWidget {
  const AzBottomNav({super.key, required this.current, required this.onSelect, this.unread = 0});

  final int current;
  final ValueChanged<int> onSelect;
  final int unread;

  static const _items = [
    (Icons.home_outlined, Icons.home_rounded, 'Accueil'),
    (Icons.shield_outlined, Icons.shield_rounded, 'Assurances'),
    (Icons.add_rounded, Icons.add_rounded, 'Souscrire'),
    (Icons.report_outlined, Icons.report_rounded, 'Sinistres'),
    (Icons.person_outline_rounded, Icons.person_rounded, 'Profil'),
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: AzColors.line)),
      ),
      child: SafeArea(
        top: false,
        // Libellés agrandis jusqu’à 115 % : la barre reste lisible, sans débordement.
        child: MediaQuery.withClampedTextScaling(
          maxScaleFactor: 1.15,
          child: SizedBox(
            height: 76,
            child: Row(
              children: [
                for (var i = 0; i < _items.length; i++)
                  Expanded(
                    child: _NavItem(
                      icon: i == current ? _items[i].$2 : _items[i].$1,
                      label: _items[i].$3,
                      selected: i == current,
                      center: i == ShellTab.catalog,
                      onTap: () => onSelect(i),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  const _NavItem({required this.icon, required this.label, required this.selected, required this.center, required this.onTap});

  final IconData icon;
  final String label;
  final bool selected;
  final bool center;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final color = selected ? AzColors.blue700 : AzColors.muted;
    return Semantics(
      button: true,
      selected: selected,
      label: 'Onglet $label',
      excludeSemantics: true,
      child: InkResponse(
        onTap: onTap,
        containedInkWell: true,
        highlightShape: BoxShape.rectangle,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            if (center)
              Transform.translate(
                offset: const Offset(0, -12),
                child: Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: AzColors.orange500,
                    borderRadius: BorderRadius.circular(18),
                    boxShadow: AzShadow.fab,
                  ),
                  child: Icon(icon, color: Colors.white, size: 26),
                ),
              )
            else
              AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                width: 56,
                height: 30,
                decoration: BoxDecoration(
                  color: selected ? AzColors.blue50 : Colors.transparent,
                  borderRadius: BorderRadius.circular(AzRadius.pill),
                ),
                child: Icon(icon, color: color, size: 23),
              ),
            SizedBox(height: center ? 0 : 4),
            Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.fade,
              softWrap: false,
              style: AzText.caption.copyWith(fontSize: 11, color: color, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 6),
          ],
        ),
      ),
    );
  }
}

/// Onglet réservé aux clients connectés, affiché pendant une visite sans compte.
class GuestGate extends StatelessWidget {
  const GuestGate({super.key, required this.tab});

  final int tab;

  @override
  Widget build(BuildContext context) {
    final (IconData icon, String title, String message) = switch (tab) {
      ShellTab.contracts => (Icons.shield_outlined, 'Vos contrats, ici', 'Connectez-vous pour retrouver vos contrats et vos attestations.'),
      ShellTab.claims => (Icons.report_outlined, 'Déclarer un sinistre', 'Connectez-vous pour déclarer un sinistre et suivre vos dossiers.'),
      ShellTab.profile => (Icons.person_outline_rounded, 'Votre espace', 'Connectez-vous pour gérer votre compte et vos préférences.'),
      _ => (Icons.home_outlined, 'Bienvenue chez AssurZen', 'Connectez-vous pour voir vos protections, ou découvrez nos offres sans compte.'),
    };
    return SafeArea(
      child: Column(
        children: [
          Expanded(
            child: AzEmptyState(
              icon: icon,
              title: title,
              message: message,
              actionLabel: 'Se connecter',
              onAction: () => context.push(AppRoutes.login),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 0, 24, 16),
            child: AzButton.ghost('Créer un compte', onPressed: () => context.push(AppRoutes.register)),
          ),
        ],
      ),
    );
  }
}
