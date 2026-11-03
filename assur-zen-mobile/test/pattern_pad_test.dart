import 'package:assurzen_mobile/core/widgets/az_pattern_pad.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('un tracé qui descend dans une page défilante n\'est pas coupé par le défilement', (tester) async {
    final results = <List<int>>[];
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: ListView(children: [
          const SizedBox(height: 40),
          AzPatternPad(onCompleted: results.add),
          const SizedBox(height: 900),
        ]),
      ),
    ));
    final box = tester.getTopLeft(find.byType(AzPatternPad).first);
    final origin = box + Offset((tester.getSize(find.byType(AzPatternPad)).width - 270) / 2, 0);
    Offset dot(int i) => origin + Offset(90.0 * (i % 3) + 45, 90.0 * (i ~/ 3) + 45);

    // Haut gauche → haut droite → bas droite : 0, 1, 2, 5, 8.
    final g = await tester.startGesture(dot(0));
    for (final i in [1, 2, 5, 8]) {
      await g.moveTo(dot(i));
    }
    await g.up();
    await tester.pump();

    expect(results, [
      [0, 1, 2, 5, 8],
    ]);
  });
}
