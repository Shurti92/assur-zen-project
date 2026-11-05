import 'package:assurzen_mobile/app/routes.dart';
import 'package:assurzen_mobile/data/models/moto_quote.dart';
import 'package:assurzen_mobile/features/quote/moto_quote_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'helpers/fonts.dart';

const _premium = MotoPremium(netPremium: 6447, taxes: 925, accessories: 500, fga: 128, total: 8000);

MotoQuote _quote(
  MotoQuoteStatus status, {
  QuoteConversion conversion = QuoteConversion.none,
  String? contractId,
  String? policyNumber,
  bool canAccept = false,
}) =>
    MotoQuote(
      reference: 'DEV-2026-000019',
      status: status,
      canAccept: canAccept,
      simulation: const MotoQuoteSimulation(
        formulaId: 'f',
        formulaName: 'Formule',
        vehicleType: 'MOTO',
        durationMonths: 12,
        premium: _premium,
        guarantees: [],
      ),
      cylinderCc: 125,
      brand: 'Yamaha',
      model: 'TESTDEMO',
      conversion: conversion,
      contractId: contractId,
      policyNumber: policyNumber,
    );

Future<List<String>> _show(WidgetTester tester, MotoQuote quote, {bool withPay = true}) async {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = const Size(411, 1400);
  addTearDown(tester.view.reset);
  final pushed = <String>[];
  await tester.pumpWidget(MaterialApp(
    onGenerateRoute: (settings) {
      pushed.add(settings.name ?? '');
      return MaterialPageRoute<void>(settings: settings, builder: (_) => const Scaffold(body: Text('page ouverte')));
    },
    home: Scaffold(
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: MotoQuoteResult(
          simulation: quote.simulation,
          quote: quote,
          busy: false,
          onSave: () {},
          onAccept: () {},
          onPay: withPay ? () {} : null,
        ),
      ),
    ),
  ));
  await tester.pump();
  return pushed;
}

void main() {
  setUpAll(loadAppFonts);

  group('Devis : paiement et contrat', () {
    testWidgets('devis calculé : on propose d\'accepter, jamais de payer', (tester) async {
      await _show(tester, _quote(MotoQuoteStatus.calcule, canAccept: true));

      expect(find.text('Accepter ce devis'), findsOneWidget);
      expect(find.text('Payer et obtenir mon contrat'), findsNothing);
    });

    testWidgets('devis accepté : bouton « Payer et obtenir mon contrat »', (tester) async {
      await _show(tester, _quote(MotoQuoteStatus.accepte));

      expect(find.text('Payer et obtenir mon contrat'), findsOneWidget);
      expect(find.text('Accepter ce devis'), findsNothing);
    });

    testWidgets('devis accepté sans action de paiement : aucun bouton', (tester) async {
      await _show(tester, _quote(MotoQuoteStatus.accepte), withPay: false);

      expect(find.text('Payer et obtenir mon contrat'), findsNothing);
    });

    testWidgets('paiement en cours : la référence du serveur est citée, le bouton reste (le serveur renvoie la référence à suivre)', (tester) async {
      await _show(
        tester,
        _quote(MotoQuoteStatus.accepte, conversion: const QuoteConversion(status: QuoteConversionStatus.pending, paymentReference: 'PAY-2026-000031')),
      );

      expect(find.textContaining('PAY-2026-000031', findRichText: true), findsOneWidget);
      expect(find.text('Payer et obtenir mon contrat'), findsOneWidget);
    });

    testWidgets('paiement échoué : message, nouvel essai possible', (tester) async {
      await _show(
        tester,
        _quote(MotoQuoteStatus.accepte, conversion: const QuoteConversion(status: QuoteConversionStatus.failed, paymentReference: 'PAY-2026-000032')),
      );

      expect(find.textContaining('n’a pas abouti', findRichText: true), findsOneWidget);
      expect(find.text('Payer et obtenir mon contrat'), findsOneWidget);
    });

    testWidgets('devis payé : « Paiement reçu », aucun second paiement possible', (tester) async {
      await _show(
        tester,
        _quote(MotoQuoteStatus.paye,
            conversion: QuoteConversion(status: QuoteConversionStatus.confirmed, paymentReference: 'PAY-2026-000019', paidAt: DateTime(2026, 11, 5))),
      );

      expect(find.textContaining('Paiement reçu.', findRichText: true), findsOneWidget);
      expect(find.textContaining('en cours d’émission', findRichText: true), findsOneWidget);
      expect(find.text('Payer et obtenir mon contrat'), findsNothing);
      expect(find.text('Voir mon contrat'), findsNothing, reason: 'le contrat n\'existe pas encore');
    });

    testWidgets('devis converti : numéro de police et lien vers le contrat', (tester) async {
      final pushed = await _show(
        tester,
        _quote(MotoQuoteStatus.converti,
            conversion: const QuoteConversion(status: QuoteConversionStatus.confirmed, paymentReference: 'PAY-2026-000019'),
            contractId: '901',
            policyNumber: 'POL-2026-0001'),
      );

      expect(find.textContaining('Votre contrat est émis.', findRichText: true), findsOneWidget);
      expect(find.textContaining('POL-2026-0001', findRichText: true), findsOneWidget);
      expect(find.text('Payer et obtenir mon contrat'), findsNothing);

      await tester.ensureVisible(find.text('Voir mon contrat'));
      await tester.tap(find.text('Voir mon contrat'));
      await tester.pumpAndSettle();
      expect(pushed, contains(AppRoutes.contract));
    });

    testWidgets('devis converti sans identifiant de contrat : pas de lien', (tester) async {
      await _show(tester, _quote(MotoQuoteStatus.converti, policyNumber: 'POL-2026-0001'));

      expect(find.text('Voir mon contrat'), findsNothing);
    });

    testWidgets('devis expiré : ni acceptation, ni paiement', (tester) async {
      await _show(tester, _quote(MotoQuoteStatus.expire));

      expect(find.text('Payer et obtenir mon contrat'), findsNothing);
      expect(find.text('Accepter ce devis'), findsNothing);
    });
  });
}
