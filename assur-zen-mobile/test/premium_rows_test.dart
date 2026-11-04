import 'package:assurzen_mobile/data/models/moto_quote.dart';
import 'package:assurzen_mobile/features/quote/moto_quote_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'helpers/fonts.dart';

// Décomposition réelle relevée sur la Gateway de développement (devis VTC, 12 mois), après
// l'application des taxes à 14,5 % : le total ne change pas, seule la répartition change.
const _vtcAvecTaxes = MotoPremium(
  netPremium: 158396,
  taxes: 22967,
  accessories: 2024,
  fga: 280,
  cedeao: 1000,
  attestation: 1000,
  total: 185667,
);

// Ancienne décomposition du même devis : taxes à 0 (non communiquées).
const _vtcSansTaxes = MotoPremium(
  netPremium: 181363,
  taxes: 0,
  accessories: 2024,
  fga: 280,
  cedeao: 1000,
  attestation: 1000,
  total: 185667,
);

// Devis moto : CEDEAO, attestation et remise valent 0.
const _moto = MotoPremium(netPremium: 6447, taxes: 925, accessories: 500, fga: 128, total: 8000);

Future<void> _show(WidgetTester tester, MotoPremium premium) async {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = const Size(411, 891);
  addTearDown(tester.view.reset);
  await tester.pumpWidget(MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: MotoQuoteResult(
          simulation: MotoQuoteSimulation(
            formulaId: 'f',
            formulaName: 'Formule',
            vehicleType: 'VTC',
            durationMonths: 12,
            premium: premium,
            guarantees: const [],
          ),
          quote: null,
          busy: false,
          onSave: () {},
          onAccept: () {},
        ),
      ),
    ),
  ));
  await tester.pump();
}

int _sumOfLines(MotoPremium p) => p.netPremium - p.discount + p.taxes + p.accessories + p.fga + p.cedeao + p.attestation;

void main() {
  setUpAll(loadAppFonts);

  testWidgets('VTC avec taxes : la ligne « Taxes » apparaît, et la décomposition donne le total', (tester) async {
    await _show(tester, _vtcAvecTaxes);

    for (final label in ['Prime nette', 'Taxes', 'Accessoires', 'FGA', 'CEDEAO', 'Attestation', 'Total']) {
      expect(find.text(label), findsOneWidget, reason: label);
    }
    expect(find.text('Remise'), findsNothing); // aucune remise côté client
    expect(find.textContaining('22'), findsWidgets); // la taxe est affichée (22 967)
    expect(_sumOfLines(_vtcAvecTaxes), _vtcAvecTaxes.total);
  });

  testWidgets('VTC sans taxes communiquées : la ligne « Taxes » reste masquée', (tester) async {
    await _show(tester, _vtcSansTaxes);

    expect(find.text('Taxes'), findsNothing);
    expect(find.text('Total'), findsOneWidget);
    expect(_sumOfLines(_vtcSansTaxes), _vtcSansTaxes.total);
  });

  testWidgets('moto : pas de CEDEAO ni d\'attestation, les autres lignes sont là', (tester) async {
    await _show(tester, _moto);

    expect(find.text('CEDEAO'), findsNothing);
    expect(find.text('Attestation'), findsNothing);
    expect(find.text('Taxes'), findsOneWidget);
    expect(_sumOfLines(_moto), _moto.total);
  });

  testWidgets('une remise éventuelle du serveur est affichée en déduction', (tester) async {
    await _show(
      tester,
      const MotoPremium(netPremium: 100000, taxes: 14500, accessories: 0, fga: 0, discount: 10000, total: 104500),
    );

    expect(find.text('Remise'), findsOneWidget);
    expect(find.textContaining('- 10'), findsOneWidget);
  });
}
