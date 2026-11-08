import 'package:assurzen_mobile/features/catalog/catalog_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'helpers/fonts.dart';

Future<void> _pump(WidgetTester tester, Widget badge) async {
  await tester.pumpWidget(MaterialApp(home: Scaffold(body: Center(child: badge))));
  await tester.pump();
}

void main() {
  setUpAll(loadAppFonts);

  testWidgets('la pastille suit ce que le serveur sait faire pour le produit', (tester) async {
    // Souscription entièrement en ligne.
    await _pump(tester, const SubscriptionModeBadge(online: true));
    expect(find.text('Souscription en ligne'), findsOneWidget);

    // Moto, VTC, Voyage, Entreprise : le serveur chiffre dans l'application (quote.available).
    await _pump(tester, const SubscriptionModeBadge(online: false, quote: true));
    expect(find.text('Devis en ligne'), findsOneWidget);
    expect(find.text('Avec un conseiller'), findsNothing);

    // Aucun devis annoncé : avec un conseiller.
    await _pump(tester, const SubscriptionModeBadge(online: false));
    expect(find.text('Avec un conseiller'), findsOneWidget);

    // Produit professionnel au devis en ligne : « Sur devis » l'emporte.
    await _pump(tester, const SubscriptionModeBadge(online: true, quoteOnly: true, quote: true));
    expect(find.text('Sur devis'), findsOneWidget);
  });
}
