import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/state/async_view.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('« Réessayer » recharge les données sans erreur (régression : setState ne doit pas renvoyer un Future)', (tester) async {
    var calls = 0;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: AsyncView<String>(
          load: () async {
            calls++;
            if (calls == 1) throw ApiException.network();
            return 'données';
          },
          builder: (context, data, reload) => Text(data),
        ),
      ),
    ));
    await tester.pumpAndSettle();

    expect(find.text('Réessayer'), findsOneWidget, reason: 'la première lecture échoue : écran d’erreur réseau');

    await tester.tap(find.text('Réessayer'));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull, reason: 'avant la correction : setState() callback argument returned a Future');
    expect(calls, 2);
    expect(find.text('données'), findsOneWidget);
  });
}
