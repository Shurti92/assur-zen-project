import 'package:assurzen_mobile/core/widgets/az_cards.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('AzInfoRow : un libellé long ne casse pas la valeur lettre par lettre', (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: Padding(
            padding: EdgeInsets.all(16),
            child: AzInfoRow('Responsabilité Civile dommages corporels', 'Obligatoire'),
          ),
        ),
      ),
    );

    // Avant la correction, la valeur était écrasée et s'écrivait lettre par lettre
    // (11 lignes). La police de test est très large : on tolère 2 lignes, pas plus.
    final value = tester.getSize(find.text('Obligatoire'));
    expect(value.height, lessThan(60));
    expect(tester.takeException(), isNull);
  });
}
