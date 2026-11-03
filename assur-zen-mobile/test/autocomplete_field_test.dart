import 'package:assurzen_mobile/core/widgets/az_inputs.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Future<TextEditingController> pump(WidgetTester tester, List<String> options, {List<String>? changes}) async {
    final controller = TextEditingController();
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: ListView(padding: const EdgeInsets.all(20), children: [
          AzAutocompleteField(
            label: 'Marque',
            controller: controller,
            options: options,
            onChanged: (v) => changes?.add(v),
          ),
        ]),
      ),
    ));
    return controller;
  }

  testWidgets('propose d\'abord les marques qui commencent par la saisie, sans tenir compte de la casse', (tester) async {
    await pump(tester, ['Yamaha', 'Honda', 'Haojue', 'Apsonic']);

    await tester.enterText(find.byType(TextField), 'HO');
    await tester.pump();

    expect(find.text('Honda'), findsOneWidget);
    expect(find.text('Yamaha'), findsNothing);
    // « Haojue » ne contient pas « ho » (h-a-o…) : rien d'autre n'est proposé.
    expect(find.text('Haojue'), findsNothing);
  });

  testWidgets('choisir une suggestion remplit le champ et prévient la page', (tester) async {
    final changes = <String>[];
    final controller = await pump(tester, ['Yamaha', 'Honda'], changes: changes);

    await tester.enterText(find.byType(TextField), 'yam');
    await tester.pump();
    await tester.tap(find.text('Yamaha'));
    await tester.pump();

    expect(controller.text, 'Yamaha');
    expect(changes.last, 'Yamaha');
  });

  testWidgets('la saisie libre reste possible et les accents sont ignorés', (tester) async {
    final controller = await pump(tester, ['Citroën']);

    await tester.enterText(find.byType(TextField), 'citro');
    await tester.pump();
    expect(find.text('Citroën'), findsOneWidget);

    await tester.enterText(find.byType(TextField), 'Marque inconnue');
    await tester.pump();
    expect(find.text('Citroën'), findsNothing);
    expect(controller.text, 'Marque inconnue'); // acceptée telle quelle
  });

  testWidgets('sans liste, c\'est un simple champ texte', (tester) async {
    final controller = await pump(tester, const []);

    await tester.enterText(find.byType(TextField), 'Yamaha');
    await tester.pump();

    expect(controller.text, 'Yamaha');
    expect(find.byType(InkWell), findsNothing);
  });
}
