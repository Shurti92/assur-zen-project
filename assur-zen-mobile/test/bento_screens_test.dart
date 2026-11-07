import 'package:assurzen_mobile/app/app_scope.dart';
import 'package:assurzen_mobile/app/session_controller.dart';
import 'package:assurzen_mobile/core/storage/local_store.dart';
import 'package:assurzen_mobile/core/ui.dart';
import 'package:assurzen_mobile/data/mock/mock_data.dart';
import 'package:assurzen_mobile/data/mock/mock_repositories.dart';
import 'package:assurzen_mobile/features/profile/profile_page.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:assurzen_mobile/features/claims/claims_page.dart';
import 'package:assurzen_mobile/features/auth/auth_layout.dart';
import 'package:assurzen_mobile/features/contracts/contracts_page.dart';
import 'package:assurzen_mobile/features/shell/main_shell.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'helpers/fonts.dart';

Contract _contract({
  ContractStatus status = ContractStatus.actif,
  bool expiring = false,
  bool attestation = false,
  bool canClaim = false,
}) =>
    Contract(
      id: 'C1',
      productId: 'auto',
      name: 'AssurZen Flotte Auto',
      insuredObject: 'BMW 318I · 640JE01',
      number: 'POL-1',
      status: status,
      guarantees: const [],
      documents: const [],
      endDate: DateTime(2026, 11, 18),
      renewal: ContractRenewal(eligible: expiring, daysLeft: 12),
      actions: ContractActions(canDeclareClaim: canClaim),
      attestation: attestation ? const ContractAttestation(available: true) : null,
    );

Future<void> _show(WidgetTester tester, Widget child, {Size size = const Size(360, 740)}) async {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = size;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(MaterialApp(home: Scaffold(body: SingleChildScrollView(padding: const EdgeInsets.all(20), child: child))));
}

void main() {
  setUpAll(loadAppFonts);

  group('Carte de contrat', () {
    testWidgets('à renouveler : badge « Bientôt échu » et bouton Renouveler en tête', (tester) async {
      await _show(tester, ContractCard(contract: _contract(expiring: true, attestation: true, canClaim: true)));

      expect(find.text('Bientôt échu'), findsOneWidget);
      expect(find.text('Renouveler'), findsOneWidget);
      expect(find.text('Attestation'), findsOneWidget);
      expect(find.text('Sinistre'), findsNothing, reason: 'trois boutons ne tiennent pas : le sinistre reste dans le détail');
      expect(find.text('POL-1'), findsOneWidget);
    });

    testWidgets('contrat en règle : Attestation et Sinistre, sans Renouveler', (tester) async {
      await _show(tester, ContractCard(contract: _contract(attestation: true, canClaim: true)));

      expect(find.text('Actif'), findsOneWidget);
      expect(find.text('Attestation'), findsOneWidget);
      expect(find.text('Sinistre'), findsOneWidget);
      expect(find.text('Renouveler'), findsNothing);
      expect(find.text('Bientôt échu'), findsNothing);
    });

    testWidgets('le serveur ne propose aucune action : aucun bouton', (tester) async {
      await _show(tester, ContractCard(contract: _contract()));

      expect(find.text('Attestation'), findsNothing);
      expect(find.text('Sinistre'), findsNothing);
      expect(find.text('Renouveler'), findsNothing);
    });

    for (final size in [const Size(320, 568), const Size(411, 891)]) {
      testWidgets('aucun débordement (${size.width.round()} dp)', (tester) async {
        await _show(tester, ContractCard(contract: _contract(expiring: true, attestation: true, canClaim: true)), size: size);
        expect(tester.takeException(), isNull);
      });
    }
  });

  group('Carte de sinistre', () {
    Claim claim({int step = 1, bool closed = false}) => Claim(
          id: 'K1',
          type: 'Accident / collision',
          contractLabel: 'AssurZen Flotte Auto',
          number: 'SIN-000123',
          declaredAt: DateTime(2026, 11, 3),
          steps: const ['Reçue', 'Analyse', 'Expertise', 'Décision', 'Clôture'],
          currentStep: step,
          history: const [],
          closed: closed,
        );

    testWidgets('en cours : avancement « Étape 2 sur 5 » tiré des étapes du serveur', (tester) async {
      await _show(tester, ClaimCard(claim: claim()));

      expect(find.text('Étape 2 sur 5'), findsOneWidget);
      expect(find.text('Analyse'), findsOneWidget, reason: 'libellé de l étape en cours, à droite');
      expect(find.text('Dossier SIN-000123 · déclaré le 03/11/2026'), findsOneWidget);
    });

    testWidgets('clôturé : « Terminé » et toutes les étapes remplies', (tester) async {
      await _show(tester, ClaimCard(claim: claim(step: 4, closed: true)));

      expect(find.text('Terminé'), findsOneWidget);
      expect(find.text('5 étapes sur 5'), findsOneWidget);
      expect(find.text('Clôturé'), findsOneWidget);
    });

    testWidgets('segments : autant que d\'étapes, remplis jusqu\'à l\'étape en cours', (tester) async {
      await _show(tester, const AzSegments(total: 5, filled: 2));

      final boxes = tester.widgetList<Container>(find.descendant(of: find.byType(AzSegments), matching: find.byType(Container))).toList();
      expect(boxes.length, 5);
      final colors = boxes.map((c) => (c.decoration as BoxDecoration).color).toList();
      expect(colors.where((c) => c == AzColors.blue700).length, 2);
    });
  });

  authLayoutTests();
  profilePhoneTests();

  group('Barre du bas', () {
    testWidgets('cinq onglets : les quatre libellés visibles et Souscrire au centre', (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(360, 740);
      addTearDown(tester.view.reset);
      int? picked;
      await tester.pumpWidget(MaterialApp(
        home: Scaffold(bottomNavigationBar: AzBottomNav(current: ShellTab.home, onSelect: (i) => picked = i)),
      ));

      for (final label in ['Accueil', 'Assurances', 'Sinistres', 'Profil']) {
        expect(find.text(label), findsOneWidget);
      }
      await tester.tap(find.byTooltip('Onglet Souscrire').evaluate().isEmpty ? find.bySemanticsLabel('Onglet Souscrire') : find.byTooltip('Onglet Souscrire'));
      expect(picked, ShellTab.catalog);
      await tester.tap(find.text('Sinistres'));
      expect(picked, ShellTab.claims);
      expect(tester.takeException(), isNull);
    });
  });
}

// Cadre des écrans d'accès (maquette B : bandeau bleu + feuille blanche).
void authLayoutTests() {
  group('Cadre des écrans d\'accès', () {
    Future<void> show(WidgetTester tester, Widget layout, {Size size = const Size(360, 740)}) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = size;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(MaterialApp(home: layout));
    }

    testWidgets('titre, message, contenu et étape sont affichés', (tester) async {
      await show(
        tester,
        AuthLayout(
          title: 'Code PIN oublié',
          subtitle: 'Nous vous envoyons un code par SMS.',
          showBack: true,
          step: 1,
          steps: 3,
          children: [const Text('Champ'), AuthPrimaryButton('Recevoir le code', onPressed: () {})],
        ),
      );

      expect(find.text('Code PIN oublié'), findsOneWidget);
      expect(find.text('Nous vous envoyons un code par SMS.'), findsOneWidget);
      expect(find.text('Champ'), findsOneWidget);
      expect(find.text('Recevoir le code'), findsOneWidget);
      expect(find.bySemanticsLabel('Étape 1 sur 3'), findsOneWidget);
      expect(find.byTooltip('Retour'), findsOneWidget);
    });

    testWidgets('écran de connexion : logo et pas de bouton retour', (tester) async {
      await show(tester, const AuthLayout(title: 'Bon retour', subtitle: 'Message', showMark: true, children: [Text('Champ')]));

      expect(find.text('AssurZen'), findsOneWidget);
      expect(find.byTooltip('Retour'), findsNothing);
    });

    for (final size in [const Size(320, 568), const Size(411, 891)]) {
      testWidgets('feuille longue : défile sans débordement (${size.width.round()} dp)', (tester) async {
        await show(
          tester,
          AuthLayout(title: 'Créer un compte', subtitle: 'Message', showBack: true, children: [for (var i = 0; i < 12; i++) AuthPrimaryButton('Bouton $i', onPressed: () {})]),
          size: size,
        );
        await tester.drag(find.byType(SingleChildScrollView), const Offset(0, -2000));
        await tester.pump();
        expect(tester.takeException(), isNull);
        expect(find.text('Bouton 11'), findsOneWidget);
      });
    }
  });
}

// Numéro de téléphone du Profil : toujours sur une seule ligne, quelle que soit la largeur.
void profilePhoneTests() {
  group('Profil : numéro de téléphone', () {
    for (final size in [const Size(320, 568), const Size(360, 640), const Size(411, 891)]) {
      for (final scale in [1.0, 1.3]) {
        testWidgets('une seule ligne (${size.width.round()} dp, texte ${(scale * 100).round()} %)', (tester) async {
          tester.view.devicePixelRatio = 1;
          tester.view.physicalSize = size;
          addTearDown(tester.view.reset);
          SharedPreferences.setMockInitialValues({'lock_choice_done': true});
          final store = await LocalStore.open();
          final repos = MockRepositories.create();
          final session = SessionController(store: store, repos: repos)..updateUser(MockData.demoUser);
          await tester.pumpWidget(AppScope(
            repos: repos,
            session: session,
            store: store,
            child: MaterialApp(
              builder: (context, child) => MediaQuery(data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)), child: child!),
              home: const Scaffold(body: ProfilePage()),
            ),
          ));
          await tester.pump(const Duration(milliseconds: 600));

          final finder = find.textContaining('+225');
          expect(finder, findsOneWidget);
          // Hauteur d'une seule ligne de 14 sp (agrandie par l'échelle du texte), pas de deux.
          expect(tester.getSize(finder).height, lessThan(14 * 1.6 * scale));
          expect(tester.takeException(), isNull);
          await tester.pumpWidget(const SizedBox.shrink());
          await tester.pump(const Duration(seconds: 5));
        });
      }
    }
  });
}
